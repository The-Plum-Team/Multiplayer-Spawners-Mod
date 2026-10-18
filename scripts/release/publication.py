#!/usr/bin/env python3
"""Plan and stage a release of every jar of release/release-matrix.json (.github/workflows/release.yml).

    python3 scripts/release/publication.py plan --tag v2.0.0 --github-output "$GITHUB_OUTPUT"
    python3 scripts/release/publication.py plan --github-output "$GITHUB_OUTPUT"   # rehearsal
    python3 scripts/release/publication.py stage --built DIR --out DIR

`plan` checks that a release tag names the matrix's mod_version and that its changelog exists, and
writes the version matrix and one publication per jar (marketplace name, version number, loader,
game version, Java, dependencies). Without a tag it plans a rehearsal that never publishes.

`stage` takes the jars the build jobs uploaded, checks that each expected jar is there exactly once
and carries this mod's metadata, and writes the release bundle: every jar under its published
file name, SHA256SUMS and CHANGELOG.md.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MATRIX = ROOT / "release" / "release-matrix.json"
CHANGELOGS = ROOT / "release" / "changelogs"

MODRINTH_ID = "ltl1ms4K"
CURSEFORGE_ID = "1333296"
DISPLAY_NAME = "Multiplayer Spawners"
LOADER_NAMES = {"fabric": "Fabric", "forge": "Forge", "neoforge": "NeoForge"}
# Each loader's mod metadata file, which every jar of that loader must contain.
METADATA_FILES = {"fabric": "fabric.mod.json", "forge": "META-INF/mods.toml", "neoforge": "META-INF/neoforge.mods.toml"}
TAG = re.compile(r"^v(\d+\.\d+\.\d+)$")
MAX_JAR_BYTES = 16 * 1024 * 1024
MAX_CHANGELOG_BYTES = 64 * 1024


class ReleaseError(Exception):
    pass


def load_matrix() -> dict:
    return json.loads(MATRIX.read_text(encoding="utf-8"))


def published_file(artifact: dict, version: str) -> str:
    return f"{DISPLAY_NAME} - {LOADER_NAMES[artifact['loader']]} - {artifact['minecraft']} - {version}.jar"


def publications(matrix: dict) -> list[dict]:
    version = matrix["project"]["mod_version"]
    rows = []
    for artifact in matrix["artifacts"]:
        loader = artifact["loader"]
        if loader not in LOADER_NAMES:
            raise ReleaseError(f"unknown loader {loader!r} in {artifact['artifact_node']}")
        rows.append({
            "id": artifact["artifact_node"],
            "file": f"dist/{published_file(artifact, version)}",
            "name": f"{DISPLAY_NAME} {version} [{LOADER_NAMES[loader]} {artifact['minecraft']}]",
            "version_number": f"{version}+{loader}-{artifact['minecraft']}",
            "loader": loader,
            "game_versions": artifact["minecraft"],
            "java": str(artifact["java"]),
            "dependencies": "fabric-api(required)" if loader == "fabric" else "",
            "modrinth_id": MODRINTH_ID,
            "curseforge_id": CURSEFORGE_ID,
        })
    return rows


def plan(tag: str, output: Path | None) -> dict:
    matrix = load_matrix()
    version = matrix["project"]["mod_version"]
    publish = bool(tag)
    if tag:
        match = TAG.fullmatch(tag)
        if not match:
            raise ReleaseError(f"release tag {tag!r} is not v<major>.<minor>.<patch>")
        if match.group(1) != version:
            raise ReleaseError(f"tag {tag} does not match mod_version {version} of release/release-matrix.json")
    changelog = CHANGELOGS / f"{version}.md"
    if publish and not changelog.is_file():
        raise ReleaseError(f"missing changelog release/changelogs/{version}.md")
    versions = []
    for artifact in matrix["artifacts"]:
        if artifact["minecraft"] not in versions:
            versions.append(artifact["minecraft"])
    result = {
        "version": version,
        "publish": "true" if publish else "false",
        "versions": json.dumps({"minecraft": versions}, separators=(",", ":")),
        "publications": json.dumps({"include": publications(matrix)}, separators=(",", ":")),
    }
    if output:
        with output.open("a", encoding="utf-8") as handle:
            for key, value in result.items():
                handle.write(f"{key}={value}\n")
    return result


def check_jar(path: Path, artifact: dict) -> None:
    if path.is_symlink() or not path.is_file():
        raise ReleaseError(f"{path.name} is not a regular file")
    size = path.stat().st_size
    if not 0 < size <= MAX_JAR_BYTES:
        raise ReleaseError(f"{path.name} has an unexpected size ({size} bytes)")
    try:
        with zipfile.ZipFile(path) as jar:
            names = set(jar.namelist())
    except zipfile.BadZipFile as error:
        raise ReleaseError(f"{path.name} is not a jar: {error}") from None
    if METADATA_FILES[artifact["loader"]] not in names:
        raise ReleaseError(f"{path.name} has no {METADATA_FILES[artifact['loader']]}")


def stage(built: Path, out: Path) -> list[str]:
    matrix = load_matrix()
    version = matrix["project"]["mod_version"]
    found = {path.name: path for path in built.rglob("*.jar")}
    expected = {}
    for artifact in matrix["artifacts"]:
        name = Path(artifact["jar"].replace("{mod_version}", version)).name
        expected[name] = artifact
    missing = sorted(set(expected) - set(found))
    extra = sorted(set(found) - set(expected))
    if missing or extra:
        raise ReleaseError(f"built jars differ from the matrix: missing {missing}, unexpected {extra}")
    if len(list(built.rglob("*.jar"))) != len(found):
        raise ReleaseError("a jar name appears more than once in the built jars")
    changelog = CHANGELOGS / f"{version}.md"
    if not changelog.is_file() or changelog.stat().st_size > MAX_CHANGELOG_BYTES:
        raise ReleaseError(f"missing or oversized changelog release/changelogs/{version}.md")

    out.mkdir(parents=True, exist_ok=False)
    sums = []
    for name, artifact in expected.items():
        source = found[name]
        check_jar(source, artifact)
        target = out / published_file(artifact, version)
        shutil.copyfile(source, target)
        sums.append(f"{hashlib.sha256(target.read_bytes()).hexdigest()}  {target.name}")
    (out / "SHA256SUMS").write_text("\n".join(sums) + "\n", encoding="utf-8")
    shutil.copyfile(changelog, out / "CHANGELOG.md")
    return sums


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)
    plan_parser = commands.add_parser("plan")
    plan_parser.add_argument("--tag", default="", help="the pushed release tag; empty for a rehearsal")
    plan_parser.add_argument("--github-output", type=Path)
    stage_parser = commands.add_parser("stage")
    stage_parser.add_argument("--built", type=Path, required=True)
    stage_parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    try:
        if args.command == "plan":
            result = plan(args.tag, args.github_output)
            print(f"version {result['version']}, publish {result['publish']}, "
                  f"{len(json.loads(result['publications'])['include'])} jars")
        else:
            sums = stage(args.built, args.out)
            print("\n".join(sums))
    except (ReleaseError, OSError, KeyError, ValueError) as error:
        print(f"release: {error}"[:500], file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
