#!/usr/bin/env python3
"""Build every Minecraft version of release/release-matrix.json, one Gradle process each.

Loom keeps per-version state that does not mix, so a Gradle process configures exactly one
version (-PmpsTarget). This script walks the matrix in order and runs `buildTarget` for each.

    python3 scripts/release/build_matrix.py                 # every version, stop at the first failure
    python3 scripts/release/build_matrix.py --keep-going    # every version, report all failures
    python3 scripts/release/build_matrix.py 1.20.1 26.3     # only these versions
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MATRIX = ROOT / "release" / "release-matrix.json"


def versions(matrix: dict) -> list[str]:
    ordered: list[str] = []
    for artifact in matrix["artifacts"]:
        if artifact["minecraft"] not in ordered:
            ordered.append(artifact["minecraft"])
    return ordered


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("targets", nargs="*", help="Minecraft versions to build (default: all)")
    parser.add_argument("--keep-going", action="store_true", help="continue after a failed version")
    parser.add_argument("--task", default="buildTarget", help="Gradle task to run per version")
    parser.add_argument("--log-dir", type=Path, help="write each version's Gradle output to <dir>/<version>.log")
    args = parser.parse_args()

    matrix = json.loads(MATRIX.read_text(encoding="utf-8"))
    known = versions(matrix)
    targets = args.targets or known
    unknown = [target for target in targets if target not in known]
    if unknown:
        parser.error(f"not in the release matrix: {', '.join(unknown)}")

    gradlew = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    if args.log_dir:
        args.log_dir.mkdir(parents=True, exist_ok=True)
    failed: list[str] = []
    for target in targets:
        started = time.monotonic()
        command = [str(gradlew), f"-PmpsTarget={target}", args.task, "--continue", "--console=plain"]
        print(f"==> {target}: {' '.join(command[1:])}", flush=True)
        if args.log_dir:
            with (args.log_dir / f"{target}.log").open("w", encoding="utf-8") as log:
                result = subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
        else:
            result = subprocess.run(command, cwd=ROOT)
        elapsed = time.monotonic() - started
        status = "ok" if result.returncode == 0 else f"FAILED (exit {result.returncode})"
        print(f"<== {target}: {status} in {elapsed:.0f}s", flush=True)
        if result.returncode != 0:
            failed.append(target)
            if not args.keep_going:
                break

    if failed:
        print(f"Failed: {', '.join(failed)}", file=sys.stderr)
        return 1
    print(f"Built {len(targets)} Minecraft version(s).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
