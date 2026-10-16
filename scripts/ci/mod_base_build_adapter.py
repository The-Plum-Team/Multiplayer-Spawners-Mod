"""MultiplayerSpawners: what the mod-base Build hooks know about this mod (``BUILD_ADAPTER_API = 1``).

The candidate files every plan is derived from are the release matrix
(``release/release-matrix.json``, staged as ``inventory``) and the scenario contract
(``e2e/scenario-contract.json``, staged as ``scenario-contract``). From them:

* a **target** is one Minecraft version, built by one Gradle run (``-PmpsTarget``);
* a **lane** is one artifact node of the matrix (``fabric-1.21.1``), with one production jar;
* its **obligations** are the checkpoints of every scenario its loader runs.

A target stages, for each lane, the production jar under ``files/`` and the lane's harness under
``harness/``, plus its own build log and manifest under ``targets/<minecraft>/``. The harness is
what the lane runs against the jar: the server smoke test and the scenario contract, packed as a
deterministic jar. Every jar embeds the build identity (tested commit, tree, lane) in
``META-INF/multiplayerspawners-build.json``.

Standard library only, Python 3.11 or newer. ``mod_base_build_dispatch.py`` is the only caller.
JSON inputs are decoded strictly (no duplicate key, no non-finite number) against closed schemas;
JSON outputs are canonical (sorted keys, compact, one final newline).
"""

from __future__ import annotations

import hashlib
import io
import json
import re
import zipfile
import zlib
from collections.abc import Callable
from typing import Any

BUILD_ADAPTER_API = 1
HOOKS = ("derive_plan", "policy", "build_target", "verify_target", "verify_build", "derive_runtime",
         "run_lane", "verify_runtime")
LOADERS = ("fabric", "forge", "neoforge")
MAX_INPUT_BYTES = 1 << 20
BUILD_IDENTITY = "META-INF/multiplayerspawners-build.json"
#: Loader metadata every production jar must carry.
METADATA = {"fabric": "fabric.mod.json", "forge": "META-INF/mods.toml", "neoforge": "META-INF/neoforge.mods.toml"}
#: The files of the repository a harness jar packs, at these paths inside it.
HARNESS_SOURCES = ("scripts/e2e/server_smoke.py", "e2e/scenario-contract.json")
MANIFEST_NAME = "artifacts.json"
BUILD_LOG_NAME = "build.log"
RESULT_NAME = "result.json"
SERVER_LOG_NAME = "logs/server.log"
_VERSION = re.compile(r"^\d+(\.\d+){1,2}$")
_TOKEN = re.compile(r"^[a-z0-9][a-z0-9.-]{0,39}$")


class AdapterError(Exception):
    """An input or an export does not satisfy this mod's own contract."""


# -- Strict JSON ---------------------------------------------------------------------------------------


def _unique(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise AdapterError(f"duplicate JSON key {key!r}")
        result[key] = value
    return result


def _constant(name: str) -> Any:
    raise AdapterError(f"non-finite JSON number {name}")


def decode(data: bytes, label: str) -> Any:
    if not data or len(data) > MAX_INPUT_BYTES:
        raise AdapterError(f"{label} must hold 1..{MAX_INPUT_BYTES} bytes")
    try:
        return json.loads(data.decode("utf-8"), object_pairs_hook=_unique, parse_constant=_constant)
    except (UnicodeDecodeError, ValueError, RecursionError) as error:
        raise AdapterError(f"{label} is not strict JSON") from error


def encode(value: Any) -> bytes:
    return (json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False, allow_nan=False)
            + "\n").encode("utf-8")


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _object(value: Any, keys: tuple[str, ...], label: str, optional: tuple[str, ...] = ()) -> dict[str, Any]:
    if type(value) is not dict or not set(keys) <= set(value) or not set(value) <= set(keys) | set(optional):
        raise AdapterError(f"{label} must be an object with {sorted(keys)} (optionally {sorted(optional)})")
    return value


def _string(value: Any, label: str, pattern: re.Pattern[str] | None = None) -> str:
    if type(value) is not str or not value or (pattern is not None and not pattern.match(value)):
        raise AdapterError(f"{label} is not a valid string")
    return value


# -- Native inputs -------------------------------------------------------------------------------------


def parse_inventory(data: bytes) -> dict[str, Any]:
    """The release matrix (``release/release-matrix.json``); ``scripts/release/matrix.py`` holds
    the full rules, this checks what the plan depends on."""

    matrix = _object(decode(data, "release matrix"),
                     ("schema_version", "lane_count", "default_target", "project", "installers", "artifacts"),
                     "release matrix")
    if type(matrix["schema_version"]) is not int or matrix["schema_version"] != 1:
        raise AdapterError("release matrix schema_version must be 1")
    project = _object(matrix["project"], ("mod_id", "name", "mod_version", "description", "authors", "license",
                                          "sources", "issues"), "project")
    _string(project["name"], "project.name", re.compile(r"^[A-Za-z0-9]+$"))
    _string(project["mod_version"], "project.mod_version", re.compile(r"^\d+\.\d+\.\d+$"))
    artifacts = matrix["artifacts"]
    if type(artifacts) is not list or not artifacts or len(artifacts) != matrix["lane_count"]:
        raise AdapterError("artifacts must list lane_count lanes")
    seen = set()
    for row in artifacts:
        _object(row, ("artifact_node", "minecraft", "loader", "java", "no_remap", "loader_version", "installer",
                      "metadata", "gradle_task", "jar"), "artifact", optional=("fabric_api", "fabric_api_sha256"))
        if row["installer"] not in matrix["installers"]:
            raise AdapterError(f"artifact {row['artifact_node']} names an unknown installer")
        minecraft = _string(row["minecraft"], "artifact.minecraft", _VERSION)
        if row["loader"] not in LOADERS or row["artifact_node"] != f"{row['loader']}-{minecraft}":
            raise AdapterError(f"artifact {row['artifact_node']!r} has an unknown loader or a wrong node name")
        if type(row["java"]) is not int or not 17 <= row["java"] <= 99 or type(row["no_remap"]) is not bool:
            raise AdapterError(f"artifact {row['artifact_node']} has an invalid java or no_remap")
        if row["artifact_node"] in seen:
            raise AdapterError(f"duplicate artifact {row['artifact_node']}")
        seen.add(row["artifact_node"])
    return matrix


def parse_contract(data: bytes) -> dict[str, Any]:
    """The scenario contract: every scenario, the loaders it runs on and its checkpoints.

    Its ``evidence`` section (the multiplayer captures that the Pages adapter publishes) is not an
    obligation of the Build gate and is left to that adapter."""

    contract = _object(decode(data, "scenario contract"), ("schema_version", "scenarios"), "scenario contract",
                       optional=("evidence",))
    if type(contract["schema_version"]) is not int or contract["schema_version"] != 1:
        raise AdapterError("scenario contract schema_version must be 1")
    scenarios = contract["scenarios"]
    if type(scenarios) is not list or not 1 <= len(scenarios) <= 16:
        raise AdapterError("scenarios must list 1..16 scenarios")
    for scenario in scenarios:
        _object(scenario, ("id", "description", "loaders", "checkpoints"), "scenario")
        _string(scenario["id"], "scenario.id", _TOKEN)
        _string(scenario["description"], "scenario.description")
        for key, allowed in (("loaders", LOADERS), ("checkpoints", None)):
            values = scenario[key]
            if type(values) is not list or not values or len(set(map(str, values))) != len(values):
                raise AdapterError(f"scenario.{key} must list distinct values")
            for value in values:
                _string(value, f"scenario.{key}", _TOKEN)
                if allowed is not None and value not in allowed:
                    raise AdapterError(f"scenario.loaders names an unknown loader {value!r}")
    if len({scenario["id"] for scenario in scenarios}) != len(scenarios):
        raise AdapterError("scenarios must have distinct ids")
    return contract


# -- The plan ------------------------------------------------------------------------------------------


def targets(inventory: dict[str, Any]) -> list[str]:
    """The Minecraft versions, in matrix order."""

    return list(dict.fromkeys(row["minecraft"] for row in inventory["artifacts"]))


def lanes_of(inventory: dict[str, Any], target: str) -> list[dict[str, Any]]:
    rows = [row for row in inventory["artifacts"] if row["minecraft"] == target]
    if not rows:
        raise AdapterError(f"the release matrix has no target {target!r}")
    return rows


def lane_row(inventory: dict[str, Any], lane: str) -> dict[str, Any]:
    for row in inventory["artifacts"]:
        if row["artifact_node"] == lane:
            return row
    raise AdapterError(f"the release matrix has no lane {lane!r}")


def production_path(inventory: dict[str, Any], row: dict[str, Any]) -> str:
    project = inventory["project"]
    return f"files/{project['name']}-{row['loader']}-{row['minecraft']}-{project['mod_version']}.jar"


def harness_path(inventory: dict[str, Any], row: dict[str, Any]) -> str:
    return f"harness/{inventory['project']['name']}-smoke-{row['loader']}-{row['minecraft']}.jar"


def target_path(target: str, name: str) -> str:
    return f"targets/{target}/{name}"


def target_outputs(inventory: dict[str, Any], target: str) -> list[dict[str, Any]]:
    outputs: list[dict[str, Any]] = []
    for row in lanes_of(inventory, target):
        lane = row["artifact_node"]
        outputs.append({"path": production_path(inventory, row), "lane_id": lane, "role": "production"})
        outputs.append({"path": harness_path(inventory, row), "lane_id": lane, "role": "harness"})
    outputs.append({"path": target_path(target, BUILD_LOG_NAME), "lane_id": None, "role": "build-log"})
    outputs.append({"path": target_path(target, MANIFEST_NAME), "lane_id": None, "role": "native-report"})
    return outputs


def scenarios_of(contract: dict[str, Any], loader: str) -> list[dict[str, Any]]:
    return [scenario for scenario in contract["scenarios"] if loader in scenario["loaders"]]


def obligation(scenario: str, checkpoint: str) -> str:
    return f"scenario/{scenario}/{checkpoint}"


def target_contract(inventory: dict[str, Any], target: str) -> str:
    return sha256(encode({"contract": "multiplayerspawners-target-v1", "project": inventory["project"],
                          "lanes": lanes_of(inventory, target)}))


def lane_contract(contract: dict[str, Any], row: dict[str, Any]) -> str:
    return sha256(encode({"contract": "multiplayerspawners-lane-v1", "row": row,
                          "scenarios": scenarios_of(contract, row["loader"])}))


def derive_plan(inventory: dict[str, Any], contract: dict[str, Any]) -> dict[str, Any]:
    plan_targets, plan_lanes = [], []
    for target in targets(inventory):
        rows = lanes_of(inventory, target)
        for row in rows:
            scenarios = scenarios_of(contract, row["loader"])
            if not scenarios:
                raise AdapterError(f"no scenario covers loader {row['loader']!r}")
            plan_lanes.append({"id": row["artifact_node"], "target_id": target,
                               "native_contract_sha256": lane_contract(contract, row),
                               "obligations": [obligation(scenario["id"], checkpoint) for scenario in scenarios
                                               for checkpoint in scenario["checkpoints"]]})
        plan_targets.append({"id": target, "java": rows[0]["java"],
                             "native_contract_sha256": target_contract(inventory, target),
                             "outputs": target_outputs(inventory, target)})
    return {"targets": plan_targets, "lanes": plan_lanes}


# -- Build outputs -------------------------------------------------------------------------------------


def identity(inventory: dict[str, Any], row: dict[str, Any], role: str, tested_sha: str,
             tested_tree: str) -> dict[str, Any]:
    return {"mod": inventory["project"]["mod_id"], "version": inventory["project"]["mod_version"], "role": role,
            "lane": row["artifact_node"], "tested_sha": tested_sha, "tested_tree": tested_tree}


def harness_jar(inventory: dict[str, Any], row: dict[str, Any], tested_sha: str, tested_tree: str,
                sources: dict[str, bytes]) -> bytes:
    """A deterministic stored jar: the build identity and the files the lane runs."""

    entries = [(BUILD_IDENTITY, encode(identity(inventory, row, "harness", tested_sha, tested_tree)))]
    entries += [(path, sources[path]) for path in HARNESS_SOURCES]
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, "w", zipfile.ZIP_STORED) as archive:
        for name, data in entries:
            entry = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            entry.create_system = 3
            entry.external_attr = 0o644 << 16
            archive.writestr(entry, data)
    return stream.getvalue()


def jar_entries(data: bytes, label: str) -> dict[str, bytes]:
    """Every entry of an intact jar; a damaged archive is a rejection."""

    try:
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            if archive.testzip() is not None:
                raise AdapterError(f"{label} has a damaged entry")
            names = archive.namelist()
            if len(set(names)) != len(names):
                raise AdapterError(f"{label} repeats an entry")
            return {name: archive.read(name) for name in names if not name.endswith("/")}
    except (zipfile.BadZipFile, zlib.error, OSError, KeyError, ValueError, EOFError, NotImplementedError,
            RuntimeError) as error:
        raise AdapterError(f"{label} is not an intact jar") from error


def manifest(target: str, tested_sha: str, tested_tree: str, files: dict[str, bytes]) -> bytes:
    staged = [{"path": path, "sha256": sha256(data), "size": len(data)} for path, data in sorted(files.items())]
    return encode({"schema_version": 1, "target": target, "tested_sha": tested_sha, "tested_tree": tested_tree,
                   "files": staged})


def verify_target(inventory: dict[str, Any], target: str, *, tested_sha: str, tested_tree: str,
                  read: Callable[[str], bytes]) -> list[dict[str, Any]]:
    """Check the staged files of one target (``read(path) -> bytes``) and return the record of
    every one of them, the manifest included."""

    path = target_path(target, MANIFEST_NAME)
    raw = read(path)
    document = _object(decode(raw, "manifest"), ("schema_version", "target", "tested_sha", "tested_tree", "files"),
                       "manifest")
    if encode(document) != raw or (document["schema_version"], document["target"], document["tested_sha"],
                                   document["tested_tree"]) != (1, target, tested_sha, tested_tree):
        raise AdapterError(f"the manifest of {target} is not canonical or names another build")
    expected = sorted(output["path"] for output in target_outputs(inventory, target) if output["path"] != path)
    if type(document["files"]) is not list or [entry.get("path") if type(entry) is dict else None
                                                for entry in document["files"]] != expected:
        raise AdapterError(f"the manifest of {target} does not list exactly its staged files")
    for entry in document["files"]:
        data = read(_object(entry, ("path", "sha256", "size"), "manifest file")["path"])
        if (sha256(data), len(data)) != (entry["sha256"], entry["size"]):
            raise AdapterError(f"{entry['path']} differs from the manifest of {target}")
    for row in lanes_of(inventory, target):
        production = production_path(inventory, row)
        entries = jar_entries(read(production), production)
        if decode(entries.get(BUILD_IDENTITY, b""), f"{production} build identity") != identity(
                inventory, row, "production", tested_sha, tested_tree):
            raise AdapterError(f"{production} embeds another build identity")
        if METADATA[row["loader"]] not in entries or "multiplayerspawners.mixins.json" not in entries:
            raise AdapterError(f"{production} lacks its {row['loader']} metadata or mixin config")
        if not any(name.startswith("com/theplumteam/multiplayerspawners/mixin/") for name in entries):
            raise AdapterError(f"{production} does not hold the common classes")
        harness = harness_path(inventory, row)
        harness_entries = jar_entries(read(harness), harness)
        if sorted(harness_entries) != sorted([BUILD_IDENTITY, *HARNESS_SOURCES]) or decode(
                harness_entries[BUILD_IDENTITY], f"{harness} build identity") != identity(
                inventory, row, "harness", tested_sha, tested_tree):
            raise AdapterError(f"{harness} is not this build's harness")
    return document["files"] + [{"path": path, "sha256": sha256(raw), "size": len(raw)}]


# -- Runtime -------------------------------------------------------------------------------------------


def runtime_row(inventory: dict[str, Any], lane: str) -> dict[str, Any]:
    """What ``run_lane`` needs to know about the lane and the staged Build."""

    row = lane_row(inventory, lane)
    return {"id": lane, "row": row, "installer": inventory["installers"][row["installer"]],
            "production": production_path(inventory, row), "harness": harness_path(inventory, row)}


def runtime_values(inventory: dict[str, Any], contract: dict[str, Any], lane: str) -> dict[str, str]:
    loader = lane_row(inventory, lane)["loader"]
    return {"E2E_ROW_JSON": encode(runtime_row(inventory, lane)).decode("utf-8").rstrip("\n"),
            "E2E_SCENARIOS": ",".join(scenario["id"] for scenario in scenarios_of(contract, loader))}


def result_report(lane: str, row: dict[str, Any], tested_sha: str, production: bytes,
                  checks: list[dict[str, Any]], log: bytes) -> bytes:
    return encode({"schema_version": 1, "lane": lane, "row": row, "tested_sha": tested_sha,
                   "production_sha256": sha256(production), "checks": checks,
                   "log": {"path": SERVER_LOG_NAME, "sha256": sha256(log), "size": len(log)}})


def verify_run(lane: str, obligations: list[str], row: dict[str, Any], *, tested_sha: str, production: bytes,
               read: Callable[[str], bytes]) -> dict[str, Any]:
    """Check one lane's results (``read(path below lanes/<lane>) -> bytes``) against the plan's
    obligations, the lane row and the sealed production jar; return what the report records."""

    raw = read(RESULT_NAME)
    result = _object(decode(raw, "result report"),
                     ("schema_version", "lane", "row", "tested_sha", "production_sha256", "checks", "log"),
                     "result report")
    if encode(result) != raw or (result["schema_version"], result["lane"], result["row"], result["tested_sha"]) != (
            1, lane, row, tested_sha):
        raise AdapterError(f"the result report of {lane} is not canonical or names another run")
    if result["production_sha256"] != sha256(production):
        raise AdapterError(f"{lane} did not run the sealed production jar")
    checks = result["checks"]
    if type(checks) is not list or [check.get("obligation") if type(check) is dict else None
                                     for check in checks] != obligations:
        raise AdapterError(f"{lane} did not run exactly its planned obligations")
    failed = [check["obligation"] for check in checks
              if _object(check, ("obligation", "status", "problems"), "check")["status"] != "pass"
              or check["problems"]]
    if failed:
        raise AdapterError(f"{lane} failed {', '.join(failed)}")
    log = _object(result["log"], ("path", "sha256", "size"), "log")
    data = read(log["path"])
    if log["path"] != SERVER_LOG_NAME or (sha256(data), len(data)) != (log["sha256"], log["size"]):
        raise AdapterError(f"the server log of {lane} differs from its result report")
    return {"obligations": obligations, "production_sha256": result["production_sha256"],
            "result_sha256": sha256(raw), "files": sorted([RESULT_NAME, SERVER_LOG_NAME])}
