#!/usr/bin/env python3
"""Run the mod-base Build hooks locally, the way the shared workflows run them, for one target.

    python3 scripts/ci/local_build_run.py 1.21.1           # plan, build, verify, run and verify each lane
    python3 scripts/ci/local_build_run.py 1.21.1 --no-lanes

It lays out a worker root as the kit does (``validation-input/``, ``sealed-build/``,
``sealed-runtime/``, a validator home and a candidate home), clones the committed HEAD as the
candidate's checkout, and runs ``mod_base_build_dispatch.py --hook <name>`` for:

    derive_plan -> build_target -> verify_target -> for each lane: derive_runtime -> run_lane -> verify_runtime

with the environment each hook gets (``MB_TARGET_ID``, ``MB_LANE_ID``, ``MB_TESTED_SHA``, ...). After
every candidate hook it checks the kit's rule that the checkout still holds exactly the tested tree.

What it does not reproduce is the isolation: the hooks run as you, with your Gradle home and your
network, and the plan is not bound by the kit's protected code. Commit before running it.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CONFIG = json.loads((ROOT / "scripts" / "ci" / "mod-base-build.json").read_text(encoding="utf-8"))


def git(*args: str, cwd: Path = ROOT) -> str:
    return subprocess.run(["git", *args], cwd=cwd, check=True, capture_output=True, text=True).stdout.strip()


def hook(name: str, home: Path, cwd: Path, dispatcher: Path, env: dict[str, str]) -> None:
    environment = {key: value for key, value in os.environ.items()
                   if key in ("PATH", "JAVA_HOME", "GRADLE_USER_HOME", "LANG")}
    environment.update(env, HOME=str(home), TMPDIR=str(home / "tmp"))
    (home / "tmp").mkdir(parents=True, exist_ok=True)
    result = subprocess.run([sys.executable, "-I", "-B", str(dispatcher), "--hook", name], cwd=cwd,
                            env=environment, capture_output=True, text=True)
    tail = "\n".join((result.stdout + result.stderr).strip().splitlines()[-15:])
    print(f"--- {name} {env.get('MB_TARGET_ID') or env.get('MB_LANE_ID') or ''}\n{tail}", flush=True)
    if result.returncode != 0:
        raise SystemExit(f"hook {name} failed with exit {result.returncode}")


def untouched(checkout: Path) -> None:
    """The kit's rule: tracked files unchanged, nothing untracked outside the two allowed directories."""

    status = git("status", "--porcelain", "--ignored", "--untracked-files=all", cwd=checkout).splitlines()
    allowed = ("out/mod-base-kit/", CONFIG["bundle"]["path"] + "/")
    stray = [line for line in status if not line[3:].startswith(allowed)]
    if stray:
        raise SystemExit("the candidate hook left the checkout changed:\n" + "\n".join(stray[:20]))


def fresh(path: Path) -> Path:
    if path.exists():
        shutil.rmtree(path)
    path.mkdir(parents=True)
    return path


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("target", help="a Minecraft version of the release matrix")
    parser.add_argument("--no-lanes", action="store_true", help="stop after verify_target")
    parser.add_argument("--work-dir", type=Path, help="worker root (default: a new temporary directory)")
    args = parser.parse_args()
    if git("status", "--porcelain"):
        print("warning: uncommitted changes are not part of the tested commit", file=sys.stderr)

    work = args.work_dir.resolve() if args.work_dir else Path(tempfile.mkdtemp(prefix="mb-build-"))
    tested_sha, tested_tree = git("rev-parse", "HEAD"), git("rev-parse", "HEAD^{tree}")
    checkout = work / "repository"
    if checkout.exists():
        shutil.rmtree(checkout)
    git("clone", "--quiet", "--no-hardlinks", str(ROOT), str(checkout))
    git("checkout", "--quiet", "--detach", tested_sha, cwd=checkout)
    controller = fresh(work / "controller")
    for path in [CONFIG["adapter"]["dispatcher"], *(entry["path"] for entry in CONFIG["adapter"]["files"])]:
        (controller / path).parent.mkdir(parents=True, exist_ok=True)
        shutil.copy(checkout / path, controller / path)
    dispatcher = controller / CONFIG["adapter"]["dispatcher"]
    inputs = fresh(work / "validation-input")
    shutil.copy(checkout / CONFIG["inventory"]["path"], inputs / "inventory")
    shutil.copy(checkout / CONFIG["scenario_contract"]["path"], inputs / "scenario-contract")
    validator, candidate = work / "validator-home", work / "candidate-home"
    base = {"MB_TESTED_SHA": tested_sha, "MB_TESTED_TREE": tested_tree,
            "MB_REPOSITORY": CONFIG["repository"], "MB_JAVA_HOMES": os.environ.get("MB_JAVA_HOMES", "")}

    fresh(validator)
    hook("derive_plan", validator, controller, dispatcher, base)
    plan = json.loads((validator / "validation" / "plan.json").read_text(encoding="utf-8"))
    sha = lambda name: hashlib.sha256((inputs / name).read_bytes()).hexdigest()  # noqa: E731
    plan.update(identity={"tested_sha": tested_sha, "tested_tree": tested_tree,
                          "inventory_sha256": sha("inventory"), "scenario_sha256": sha("scenario-contract")},
                plan_inputs=[])
    (inputs / "ci-plan.json").write_text(json.dumps(plan, sort_keys=True), encoding="utf-8")
    target = next(entry for entry in plan["targets"] if entry["id"] == args.target)

    fresh(candidate)
    hook("build_target", candidate, checkout, checkout / CONFIG["adapter"]["dispatcher"],
         {**base, "MB_TARGET_ID": args.target})
    untouched(checkout)
    sealed = fresh(work / "sealed-build")
    shutil.copytree(candidate / "export", sealed, dirs_exist_ok=True)
    fresh(validator)
    hook("verify_target", validator, controller, dispatcher, {**base, "MB_TARGET_ID": args.target})
    if args.no_lanes:
        print(f"target {args.target}: built and verified in {work}")
        return 0

    bundle = checkout / CONFIG["bundle"]["path"]
    shutil.copytree(sealed, bundle, dirs_exist_ok=True)
    for lane in [entry["id"] for entry in plan["lanes"] if entry["target_id"] == target["id"]]:
        fresh(validator)
        hook("derive_runtime", validator, controller, dispatcher, {**base, "MB_LANE_ID": lane})
        values = json.loads((validator / "validation" / "runtime.json").read_text(encoding="utf-8"))["values"]
        fresh(candidate)
        hook("run_lane", candidate, checkout, checkout / CONFIG["adapter"]["dispatcher"],
             {**base, "MB_LANE_ID": lane, **values})
        untouched(checkout)
        runtime = fresh(work / "sealed-runtime")
        shutil.copytree(candidate / "export", runtime, dirs_exist_ok=True)
        fresh(validator)
        hook("verify_runtime", validator, controller, dispatcher, {**base, "MB_LANE_ID": lane})
    print(f"target {args.target}: every hook passed ({work})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
