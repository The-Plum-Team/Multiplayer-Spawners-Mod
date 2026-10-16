"""MultiplayerSpawners: the mod-base Build dispatcher, run as ``<python> -I -B <this file> --hook <name>``.

The kit runs it in a disposable account whose ``HOME`` lies directly below the worker root:

* a protected hook (``derive_plan``, ``derive_runtime``, ``verify_*``) runs from the protected copy of
  this directory. It reads ``<root>/validation-input/`` and the sealed exports
  (``<root>/sealed-build/``, ``<root>/sealed-runtime/``) and writes ``$HOME/validation/``;
* a candidate hook (``policy``, ``build_target``, ``run_lane``) runs from the tested checkout, its
  working directory, and writes ``$HOME/export/``. It must leave the checkout as it found it, so a
  build runs in a copy of the tracked tree below ``$TMPDIR``.

``MB_TARGET_ID`` or ``MB_LANE_ID`` names the unit; ``MB_TESTED_SHA`` and ``MB_TESTED_TREE`` name what
is tested; ``MB_JAVA_HOMES`` lists the JDKs the job installed. Every output is a new private file.
"""

from __future__ import annotations

import io
import json
import os
import re
import subprocess
import sys
import tarfile
from pathlib import Path
from typing import Any

# ``-I`` keeps the script's directory off ``sys.path``; the adapter lives next to this file.
sys.path.insert(0, str(Path(__file__).resolve().parent))

import mod_base_build_adapter as adapter  # noqa: E402
import policy_suite  # noqa: E402

UNIT_NAMES = {"build_target": "MB_TARGET_ID", "verify_target": "MB_TARGET_ID", "derive_runtime": "MB_LANE_ID",
              "run_lane": "MB_LANE_ID", "verify_runtime": "MB_LANE_ID"}
CANDIDATE_HOOKS = ("policy", "build_target", "run_lane")
KIT_FILES = ("ci-envelope.json", "ci-runtime-envelope.json")
INVENTORY = "release/release-matrix.json"
CONTRACT = "e2e/scenario-contract.json"
CONFIG = "scripts/ci/mod-base-build.json"
GRADLE_TIMEOUT = 3000


class Output:
    """The hook's output directory: files are created, never replaced."""

    def __init__(self, root: Path) -> None:
        self.root = root
        self.written: list[Path] = []

    def write(self, relative: str, data: bytes) -> None:
        path = self.root.joinpath(*relative.split("/"))
        path.parent.mkdir(parents=True, exist_ok=True)
        with open(path, "xb") as stream:
            stream.write(data)
        self.written.append(path)


def _read(root: Path, relative: str) -> bytes:
    path = root.joinpath(*relative.split("/"))
    if path.is_symlink() or not path.is_file():
        raise adapter.AdapterError(f"{relative} is not a regular file")
    return path.read_bytes()


def _files(root: Path) -> set[str]:
    """Every file below ``root`` by relative path; a link or special file is a rejection."""

    found = set()
    for path in sorted(root.rglob("*")) if root.is_dir() else ():
        if path.is_symlink() or not (path.is_dir() or path.is_file()):
            raise adapter.AdapterError(f"{path.name} is not a regular file or directory")
        if path.is_file():
            found.add(path.relative_to(root).as_posix())
    return found


# -- Protected hooks -----------------------------------------------------------------------------------


def _protected_inputs(root: Path) -> tuple[dict[str, Any], dict[str, Any], dict[str, bytes]]:
    inputs = root / "validation-input"
    raw = {name: _read(inputs, name) for name in ("inventory", "scenario-contract")}
    return adapter.parse_inventory(raw["inventory"]), adapter.parse_contract(raw["scenario-contract"]), raw


def _plan(root: Path, raw: dict[str, bytes]) -> dict[str, Any]:
    """The protected plan, which must bind the inventory and the contract this hook was given."""

    plan = adapter.decode(_read(root / "validation-input", "ci-plan.json"), "plan")
    identity = plan["identity"]
    if (identity["inventory_sha256"], identity["scenario_sha256"]) != (adapter.sha256(raw["inventory"]),
                                                                      adapter.sha256(raw["scenario-contract"])):
        raise adapter.AdapterError("the plan was not derived from this release matrix and scenario contract")
    if plan["plan_inputs"] != []:
        raise adapter.AdapterError("the plan binds plan inputs this mod does not use")
    return plan


def _unit(plan: dict[str, Any], kind: str, unit: str) -> dict[str, Any]:
    for entry in plan[kind]:
        if entry["id"] == unit:
            return entry
    raise adapter.AdapterError(f"the plan has no {kind[:-1]} {unit!r}")


def _verify_targets(root: Path, output: Output, hook: str, unit: str | None) -> None:
    inventory, _, raw = _protected_inputs(root)
    plan = _plan(root, raw)
    sealed = root / "sealed-build"
    planned = {entry["path"] for target in plan["targets"] for entry in target["outputs"]}
    present = _files(sealed) - set(KIT_FILES)
    if not present <= planned or (unit is None and present != planned):
        raise adapter.AdapterError("the sealed Build does not hold exactly the planned outputs")
    for entry in plan["targets"] if unit is None else [_unit(plan, "targets", unit)]:
        target = entry["id"]
        if (entry["native_contract_sha256"] != adapter.target_contract(inventory, target)
                or entry["outputs"] != adapter.target_outputs(inventory, target)):
            raise adapter.AdapterError(f"target {target} of the plan is not this matrix's target")
        files = adapter.verify_target(inventory, target, tested_sha=plan["identity"]["tested_sha"],
                                      tested_tree=plan["identity"]["tested_tree"],
                                      read=lambda path: _read(sealed, path))
        output.write(f"{target}.json", adapter.encode({
            "schema_version": 1, "hook": hook, "unit": target,
            "native_contract_sha256": entry["native_contract_sha256"],
            "files": sorted(files, key=lambda record: record["path"])}))


def _verify_runtime(root: Path, output: Output, lane: str) -> None:
    inventory, contract, raw = _protected_inputs(root)
    plan = _plan(root, raw)
    entry = _unit(plan, "lanes", lane)
    row = adapter.lane_row(inventory, lane)
    if entry["native_contract_sha256"] != adapter.lane_contract(contract, row):
        raise adapter.AdapterError(f"lane {lane} of the plan is not this contract's lane")
    production = [item["path"] for item in _unit(plan, "targets", entry["target_id"])["outputs"]
                  if item["lane_id"] == lane and item["role"] == "production"]
    results = root / "sealed-runtime" / "lanes" / lane
    summary = adapter.verify_run(lane, entry["obligations"], adapter.runtime_row(inventory, lane),
                                 tested_sha=plan["identity"]["tested_sha"],
                                 production=_read(root / "sealed-build", production[0]),
                                 read=lambda path: _read(results, path))
    if _files(root / "sealed-runtime") - set(KIT_FILES) != {f"lanes/{lane}/{name}" for name in summary["files"]}:
        raise adapter.AdapterError(f"lane {lane} left files its result report does not account for")
    output.write(f"{lane}.json", adapter.encode({"schema_version": 1, "hook": "verify_runtime", "unit": lane,
                                               "native_contract_sha256": entry["native_contract_sha256"], **summary}))


# -- Candidate hooks -----------------------------------------------------------------------------------


def _java_homes() -> dict[int, str]:
    """The installed JDKs by feature version (``MB_JAVA_HOMES``, else ``JAVA_HOME``)."""

    homes: dict[int, str] = {}
    for home in filter(None, os.environ.get("MB_JAVA_HOMES", os.environ.get("JAVA_HOME", "")).split(":")):
        release = Path(home) / "release"
        match = re.search(r'^JAVA_VERSION="(\d+)', release.read_text(encoding="utf-8"), re.MULTILINE) \
            if release.is_file() else None
        if match:
            homes.setdefault(int(match.group(1)), home)
    return homes


def _tracked_copy(checkout: Path, destination: Path) -> None:
    """The tested tree, as tracked, without touching the checkout."""

    archive = subprocess.run(["git", "-C", str(checkout), "archive", "--format=tar", "HEAD"], check=True,
                             stdout=subprocess.PIPE, stderr=subprocess.PIPE).stdout
    destination.mkdir(parents=True)
    with tarfile.open(fileobj=io.BytesIO(archive)) as tree:
        tree.extractall(destination, filter="data")


def _build_target(checkout: Path, output: Output, target: str) -> None:
    inventory = adapter.parse_inventory(_read(checkout, INVENTORY))
    rows = adapter.lanes_of(inventory, target)
    tested_sha, tested_tree = os.environ["MB_TESTED_SHA"], os.environ["MB_TESTED_TREE"]
    work = Path(os.environ["TMPDIR"]) / f"build-{target}"
    _tracked_copy(checkout, work)
    identity = json.dumps({"mod": inventory["project"]["mod_id"], "version": inventory["project"]["mod_version"],
                           "tested_sha": tested_sha, "tested_tree": tested_tree}, sort_keys=True)
    homes = _java_homes()
    command = [str(work / "gradlew"), "--no-daemon", "--console=plain", f"-PmpsTarget={target}",
               f"-PmpsBuildIdentity={identity}", "-Porg.gradle.java.installations.auto-detect=false",
               "-Porg.gradle.java.installations.auto-download=false",
               f"-Porg.gradle.java.installations.paths={','.join(homes.values())}", "buildTarget"]
    build = subprocess.run(command, cwd=work, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                           timeout=GRADLE_TIMEOUT)
    sys.stdout.write(build.stdout.decode("utf-8", "replace")[-20000:])
    if build.returncode != 0:
        raise adapter.AdapterError(f"Gradle failed for {target} (exit {build.returncode})")

    sources = {path: _read(work, path) for path in adapter.HARNESS_SOURCES}
    files: dict[str, bytes] = {}
    for row in rows:
        built = work / row["jar"].replace("{mod_version}", inventory["project"]["mod_version"])
        files[adapter.production_path(inventory, row)] = _read(built.parent, built.name)
        files[adapter.harness_path(inventory, row)] = adapter.harness_jar(inventory, row, tested_sha, tested_tree,
                                                                          sources)
    files[adapter.target_path(target, adapter.BUILD_LOG_NAME)] = build.stdout or b"(no output)\n"
    for path, data in files.items():
        output.write(path, data)
    output.write(adapter.target_path(target, adapter.MANIFEST_NAME),
                 adapter.manifest(target, tested_sha, tested_tree, files))


def _run_lane(checkout: Path, output: Output, lane: str) -> None:
    config = adapter.decode(_read(checkout, CONFIG), CONFIG)
    inventory = adapter.parse_inventory(_read(checkout, INVENTORY))
    contract = adapter.parse_contract(_read(checkout, CONTRACT))
    row = adapter.decode(os.environ["E2E_ROW_JSON"].encode("utf-8"), "E2E_ROW_JSON")
    if row != adapter.runtime_row(inventory, lane):
        raise adapter.AdapterError(f"the runtime row does not describe lane {lane}")
    wanted = os.environ["E2E_SCENARIOS"].split(",")
    scenarios = [scenario for scenario in adapter.scenarios_of(contract, row["row"]["loader"])
                 if scenario["id"] in wanted]
    if [scenario["id"] for scenario in scenarios] != wanted:
        raise adapter.AdapterError(f"the runtime scenarios do not describe lane {lane}")
    bundle = checkout / config["bundle"]["path"]
    production = _read(bundle, row["production"])
    embedded = adapter.decode(adapter.jar_entries(production, row["production"]).get(adapter.BUILD_IDENTITY, b""),
                              "build identity")
    if embedded.get("tested_sha") != os.environ["MB_TESTED_SHA"]:
        raise adapter.AdapterError("the staged Build was not built from the tested commit")

    sys.path.insert(0, str(checkout / "scripts" / "e2e"))
    import server_smoke  # noqa: PLC0415 - the lane's own harness, from the tested checkout

    problems, log_path = server_smoke.run_server(row["row"], {row["row"]["installer"]: row["installer"]},
                                                 bundle / row["production"],
                                                 Path(os.environ["TMPDIR"]) / "server-smoke", _java_homes())
    checks = [{"obligation": adapter.obligation(scenario["id"], checkpoint),
               "status": "fail" if problems.get(checkpoint) else "pass",
               "problems": problems.get(checkpoint, ["unknown checkpoint"] if checkpoint not in problems else [])}
              for scenario in scenarios for checkpoint in scenario["checkpoints"]]
    log = log_path.read_bytes()
    output.write(f"lanes/{lane}/{adapter.SERVER_LOG_NAME}", log)
    output.write(f"lanes/{lane}/{adapter.RESULT_NAME}",
                 adapter.result_report(lane, row, os.environ["MB_TESTED_SHA"], production, checks, log))
    for check in checks:
        print(f"{lane} {check['obligation']}: {check['status']} {'; '.join(check['problems'])}", flush=True)


def _hook(hook: str, unit: str | None, root: Path, output: Output) -> None:
    if hook == "derive_plan":
        inventory, contract, _ = _protected_inputs(root)
        output.write("plan.json", adapter.encode(adapter.derive_plan(inventory, contract)))
    elif hook == "derive_runtime":
        inventory, contract, raw = _protected_inputs(root)
        _unit(_plan(root, raw), "lanes", unit)
        output.write("runtime.json", adapter.encode({"values": adapter.runtime_values(inventory, contract, unit)}))
    elif hook in ("verify_target", "verify_build"):
        _verify_targets(root, output, hook, unit)
    elif hook == "verify_runtime":
        _verify_runtime(root, output, unit)
    elif hook == "policy":
        policy_suite.run(Path.cwd())
    elif hook == "build_target":
        _build_target(Path.cwd(), output, unit)
    else:
        _run_lane(Path.cwd(), output, unit)


def main(arguments: list[str]) -> int:
    if len(arguments) != 2 or arguments[0] != "--hook" or arguments[1] not in adapter.HOOKS:
        print("usage: mod_base_build_dispatch.py --hook <name>", flush=True)
        return 2
    hook = arguments[1]
    os.umask(0o077)
    home = Path(os.environ["HOME"])
    unit = os.environ.get(UNIT_NAMES[hook]) if hook in UNIT_NAMES else None
    label = hook if unit is None else f"{hook} {unit}"
    output = Output(home / ("export" if hook in CANDIDATE_HOOKS else "validation"))
    try:
        if hook in UNIT_NAMES and not unit:
            raise adapter.AdapterError(f"{UNIT_NAMES[hook]} is required")
        _hook(hook, unit, home.parent, output)
    except (adapter.AdapterError, subprocess.SubprocessError, KeyError, TypeError, AttributeError, ValueError,
            OSError) as error:
        print(f"multiplayerspawners {label} rejected: {error}", flush=True)
        return 1
    print(f"multiplayerspawners {label}: ok, {len(output.written)} files", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
