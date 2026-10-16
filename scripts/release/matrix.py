#!/usr/bin/env python3
"""Validate release/release-matrix.json, the only list of supported versions and loaders.

    python3 scripts/release/matrix.py                # validate; exit 1 with the problems found
    python3 scripts/release/matrix.py --versions     # print the Minecraft versions, one per line
    python3 scripts/release/matrix.py --github-matrix  # print {"minecraft": [...]} for a CI matrix
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MATRIX = ROOT / "release" / "release-matrix.json"
LOADERS = {"fabric", "forge", "neoforge"}
VERSION = re.compile(r"^\d+(\.\d+){1,2}$")
SHA256 = re.compile(r"^[0-9a-f]{64}$")


def version_key(version: str) -> tuple[int, ...]:
    parts = [int(part) for part in version.split(".")]
    return tuple(parts + [0] * (3 - len(parts)))


def validate(matrix: dict) -> list[str]:
    problems: list[str] = []
    if matrix.get("schema_version") != 1:
        return [f"unsupported schema_version {matrix.get('schema_version')!r}"]
    project = matrix.get("project", {})
    for key in ("mod_id", "name", "mod_version", "description", "authors", "license", "sources", "issues"):
        if not project.get(key):
            problems.append(f"project.{key} is missing")

    artifacts = matrix.get("artifacts", [])
    if matrix.get("lane_count") != len(artifacts):
        problems.append(f"lane_count is {matrix.get('lane_count')} but there are {len(artifacts)} artifacts")

    seen: set[str] = set()
    by_version: dict[str, list[dict]] = {}
    previous: tuple[int, ...] = ()
    for index, artifact in enumerate(artifacts):
        where = f"artifacts[{index}]"
        minecraft = artifact.get("minecraft", "")
        loader = artifact.get("loader", "")
        node = artifact.get("artifact_node")
        if not VERSION.match(minecraft):
            problems.append(f"{where}: invalid minecraft version {minecraft!r}")
            continue
        if loader not in LOADERS:
            problems.append(f"{where}: unknown loader {loader!r}")
            continue
        if node != f"{loader}-{minecraft}":
            problems.append(f"{where}: artifact_node {node!r} should be {loader}-{minecraft}")
        if node in seen:
            problems.append(f"{where}: duplicate artifact_node {node}")
        seen.add(node)
        if version_key(minecraft) < previous:
            problems.append(f"{where}: artifacts must be ordered by Minecraft version")
        previous = version_key(minecraft)
        by_version.setdefault(minecraft, []).append(artifact)

        no_remap = artifact.get("no_remap")
        if not isinstance(no_remap, bool):
            problems.append(f"{where}: no_remap must be a boolean")
        task = "shadowJar" if no_remap else "remapJar"
        if artifact.get("gradle_task") != f":{loader}:{minecraft}:{task}":
            problems.append(f"{where}: gradle_task should be :{loader}:{minecraft}:{task}")
        jar = f"{loader}/versions/{minecraft}/build/libs/{project.get('name')}-{loader}-{minecraft}-{{mod_version}}.jar"
        if artifact.get("jar") != jar:
            problems.append(f"{where}: jar should be {jar}")
        if not artifact.get("loader_version"):
            problems.append(f"{where}: loader_version is missing")
        if loader == "fabric" and not artifact.get("fabric_api"):
            problems.append(f"{where}: fabric_api is missing")
        if loader == "fabric" and not SHA256.match(str(artifact.get("fabric_api_sha256", ""))):
            problems.append(f"{where}: fabric_api_sha256 is missing or not a SHA-256")
        installer = matrix.get("installers", {}).get(artifact.get("installer"))
        if installer is None:
            problems.append(f"{where}: installer {artifact.get('installer')!r} is not in installers")
        elif loader == "fabric" and not artifact["installer"].startswith("fabric-"):
            problems.append(f"{where}: a Fabric lane needs the Fabric installer")
        elif loader != "fabric" and artifact["installer"] != f"{loader}-{artifact['loader_version']}":
            problems.append(f"{where}: installer should be {loader}-{artifact['loader_version']}")
        metadata = artifact.get("metadata", {})
        required = ["minecraft", "loader"] + ([] if loader == "fabric" else ["loader_api"])
        for key in required:
            if not metadata.get(key):
                problems.append(f"{where}: metadata.{key} is missing")
        if loader == "forge" and version_key(minecraft) >= version_key("1.20.2"):
            problems.append(f"{where}: Forge is only built up to 1.20.1; NeoForge covers later versions")
        if loader == "neoforge" and version_key(minecraft) < version_key("1.21"):
            problems.append(f"{where}: NeoForge lanes start at 1.21")

    for minecraft, rows in by_version.items():
        loaders = {row["loader"] for row in rows}
        if "fabric" not in loaders:
            # The common module compiles against the Fabric lane's loader.
            problems.append(f"{minecraft}: every version needs a fabric lane")
        if {"forge", "neoforge"} <= loaders:
            problems.append(f"{minecraft}: Forge and NeoForge cannot share a version")
        if len({row.get("java") for row in rows}) != 1 or len({row.get("no_remap") for row in rows}) != 1:
            problems.append(f"{minecraft}: lanes disagree on java/no_remap")

    installers = matrix.get("installers")
    if type(installers) is not dict or not installers:
        problems.append("installers must map names to {url, sha256}")
    else:
        for name, installer in installers.items():
            if (type(installer) is not dict or set(installer) != {"url", "sha256"}
                    or not str(installer["url"]).startswith("https://") or not SHA256.match(str(installer["sha256"]))):
                problems.append(f"installers.{name}: needs an https url and a sha256")
        unused = set(installers) - {artifact.get("installer") for artifact in artifacts}
        if unused:
            problems.append(f"installers not used by any lane: {', '.join(sorted(unused))}")

    if matrix.get("default_target") not in by_version:
        problems.append(f"default_target {matrix.get('default_target')!r} is not a listed version")
    return problems


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    output = parser.add_mutually_exclusive_group()
    output.add_argument("--versions", action="store_true", help="print the Minecraft versions")
    output.add_argument("--github-matrix", action="store_true", help="print a GitHub Actions matrix")
    parser.add_argument("--matrix", type=Path, default=MATRIX)
    args = parser.parse_args()

    matrix = json.loads(args.matrix.read_text(encoding="utf-8"))
    problems = validate(matrix)
    if problems:
        for problem in problems:
            print(f"release matrix: {problem}", file=sys.stderr)
        return 1

    ordered = list(dict.fromkeys(artifact["minecraft"] for artifact in matrix["artifacts"]))
    if args.versions:
        print("\n".join(ordered))
    elif args.github_matrix:
        print(json.dumps({"minecraft": ordered}))
    else:
        print(f"release matrix: {matrix['lane_count']} lanes across {len(ordered)} Minecraft versions")
    return 0


if __name__ == "__main__":
    sys.exit(main())
