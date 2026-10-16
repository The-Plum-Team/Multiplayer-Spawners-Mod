#!/usr/bin/env python3
"""Start a real dedicated server with a built release jar and check that the mod loads.

For each lane (artifact node of release/release-matrix.json) this installs the loader's own
server from the matrix's pinned installer (refusing any file whose SHA-256 differs), puts the production jar (plus Fabric API on Fabric) in mods/, starts it with
-Dmultiplayerspawners.mixinAudit=true so that every mixin is applied at start, waits for the
server to finish loading, stops it and checks:

  * the server reached "Done" and stopped cleanly;
  * the mod's mixin audit passed and no mixin of the mod failed;
  * the per-world config file was written.

    python3 scripts/e2e/server_smoke.py fabric-1.21.1 neoforge-1.21.1
    python3 scripts/e2e/server_smoke.py --minecraft 1.20.1
    python3 scripts/e2e/server_smoke.py --all
    python3 scripts/e2e/server_smoke.py --all --java 17=/usr/lib/jvm/java-17 --java 21=... --java 25=...

Build the jars first (scripts/release/build_matrix.py). Servers, installers and logs go below
--work-dir (default build/server-smoke); installers and libraries are reused between runs.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import threading
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MATRIX = ROOT / "release" / "release-matrix.json"
START_TIMEOUT = 600
STOP_TIMEOUT = 120
MOD_ID = "multiplayerspawners"


def download(url: str, sha256: str, target: Path) -> Path:
    """Fetch `url` once into `target`, refusing any file whose SHA-256 is not `sha256`."""

    if target.exists() and hashlib.sha256(target.read_bytes()).hexdigest() == sha256:
        return target
    target.parent.mkdir(parents=True, exist_ok=True)
    partial = target.with_name(target.name + ".part")
    request = urllib.request.Request(url, headers={"User-Agent": "MultiplayerSpawners-server-smoke"})
    digest = hashlib.sha256()
    with urllib.request.urlopen(request, timeout=120) as response, partial.open("wb") as out:
        while chunk := response.read(1 << 20):
            digest.update(chunk)
            out.write(chunk)
    if digest.hexdigest() != sha256:
        partial.unlink()
        raise RuntimeError(f"{url} does not have the pinned SHA-256 {sha256}")
    partial.replace(target)
    return target


def find_java(version: int, overrides: dict[int, str]) -> str:
    if version in overrides:
        return str(Path(overrides[version]) / "bin" / "java")
    candidates = [Path(f"/usr/lib/jvm/java-{version}-openjdk-amd64"), Path(f"/usr/lib/jvm/openjdk-{version}")]
    candidates += sorted((Path.home() / ".gradle" / "jdks").glob(f"*{version}*"))
    for home in candidates:
        java = home / "bin" / "java"
        if java.exists():
            return str(java)
    raise SystemExit(f"no Java {version} found; pass --java {version}=<java home>")


def install(artifact: dict, installers: dict, server: Path, cache: Path, java: str) -> list[str]:
    """Install the loader's server into `server` from the matrix's pinned installer and return the
    command that starts it. The installers verify what they download themselves."""

    loader, minecraft, version = artifact["loader"], artifact["minecraft"], artifact["loader_version"]
    pinned = installers[artifact["installer"]]
    installer = download(pinned["url"], pinned["sha256"], cache / f"{artifact['installer']}-installer.jar")
    mods = server / "mods"
    mods.mkdir(parents=True, exist_ok=True)
    jvm = [java, "-Xmx2G", f"-D{MOD_ID}.mixinAudit=true"]
    if loader == "fabric":
        subprocess.run([java, "-jar", str(installer), "server", "-dir", str(server), "-mcversion", minecraft,
                        "-loader", version, "-downloadMinecraft"], cwd=server, check=True,
                       stdout=subprocess.DEVNULL, stderr=subprocess.STDOUT)
        api = artifact["fabric_api"]
        download(f"https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/{api}/fabric-api-{api}.jar",
                 artifact["fabric_api_sha256"], mods / f"fabric-api-{api}.jar")
        return jvm + ["-jar", "fabric-server-launch.jar", "nogui"]

    flag = "--installServer" if loader == "forge" else "--install-server"
    subprocess.run([java, "-jar", str(installer), flag, str(server)], cwd=server, check=True,
                   stdout=subprocess.DEVNULL, stderr=subprocess.STDOUT)
    package = "neoforged/neoforge" if loader == "neoforge" else "minecraftforge/forge"
    args_file = next(server.glob(f"libraries/net/{package}/*/unix_args.txt"))
    return jvm + [f"@{args_file.relative_to(server)}", "nogui"]


CHECKPOINTS = ("started", "mixins", "config")


def run_server(artifact: dict, installers: dict, jar: Path, work: Path,
               overrides: dict[int, str]) -> tuple[dict[str, list[str]], Path]:
    """Run one lane's server with `jar` and return the problems found per checkpoint (an empty
    list is a pass) and the server log."""

    node = artifact["artifact_node"]
    java = find_java(artifact["java"], overrides)
    server = work / "servers" / node
    if server.exists():
        shutil.rmtree(server)
    server.mkdir(parents=True)
    command = install(artifact, installers, server, work / "cache", java)
    shutil.copy(jar, server / "mods")
    (server / "eula.txt").write_text("eula=true\n")
    (server / "server.properties").write_text(
        "online-mode=false\nlevel-type=minecraft\\:flat\nspawn-protection=0\nview-distance=4\n"
        "simulation-distance=4\nserver-port=0\nsync-chunk-writes=false\n")

    log_path = work / "logs" / f"{node}.log"
    log_path.parent.mkdir(parents=True, exist_ok=True)
    lines: list[str] = []
    done = threading.Event()
    with log_path.open("w", encoding="utf-8") as log:
        process = subprocess.Popen(command, cwd=server, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                   stderr=subprocess.STDOUT, text=True, errors="replace")

        def pump() -> None:
            for line in process.stdout:
                log.write(line)
                lines.append(line)
                if re.search(r"Done \([0-9.]+s\)!", line):
                    done.set()

        reader = threading.Thread(target=pump, daemon=True)
        reader.start()
        started = time.monotonic()
        while not done.is_set() and process.poll() is None and time.monotonic() - started < START_TIMEOUT:
            time.sleep(0.5)
        reached_done = done.is_set()
        if process.poll() is None:
            try:
                process.stdin.write("stop\n")
                process.stdin.flush()
            except BrokenPipeError:
                pass
            try:
                process.wait(timeout=STOP_TIMEOUT)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
        reader.join(timeout=10)

    text = "".join(lines)
    problems: dict[str, list[str]] = {checkpoint: [] for checkpoint in CHECKPOINTS}
    if not reached_done:
        problems["started"].append("server did not finish starting")
    if process.returncode not in (0, None):
        problems["started"].append(f"server exited with {process.returncode}")
    if "Mixin audit passed" not in text:
        problems["mixins"].append("mixin audit did not pass")
    for line in lines:
        lowered = line.lower()
        if MOD_ID in lowered and ("mixin" in lowered or "injection" in lowered) and re.search(
                r"\b(ERROR|FATAL)\b|failed|could not", line, re.IGNORECASE):
            problems["mixins"].append("mixin problem: " + line.strip()[:300])
            break
    if not any(server.glob(f"*/serverconfig/{MOD_ID}-server.toml")):
        problems["config"].append("the per-world server config was not written")
    return problems, log_path


def smoke(artifact: dict, matrix: dict, work: Path, overrides: dict[int, str]) -> tuple[bool, str]:
    jar = ROOT / artifact["jar"].replace("{mod_version}", matrix["project"]["mod_version"])
    if not jar.exists():
        return False, f"missing {jar.relative_to(ROOT)}; build it first"
    problems, log_path = run_server(artifact, matrix["installers"], jar, work, overrides)
    found = [problem for checkpoint in CHECKPOINTS for problem in problems[checkpoint]]
    shown = log_path.relative_to(ROOT) if log_path.is_relative_to(ROOT) else log_path
    return not found, "; ".join(found) if found else f"ok (log: {shown})"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("nodes", nargs="*", help="artifact nodes, e.g. fabric-1.21.1")
    parser.add_argument("--all", action="store_true", help="every lane of the release matrix")
    parser.add_argument("--minecraft", action="append", default=[], help="every lane of this Minecraft version")
    parser.add_argument("--work-dir", type=Path, default=ROOT / "build" / "server-smoke")
    parser.add_argument("--java", action="append", default=[], metavar="VERSION=HOME",
                        help="Java home for a feature version, e.g. 25=/opt/jdk-25")
    parser.add_argument("--keep-servers", action="store_true", help="leave the server directories in place")
    args = parser.parse_args()

    matrix = json.loads(MATRIX.read_text(encoding="utf-8"))
    by_node = {artifact["artifact_node"]: artifact for artifact in matrix["artifacts"]}
    nodes = list(by_node) if args.all else list(args.nodes)
    nodes += [node for node, artifact in by_node.items() if artifact["minecraft"] in args.minecraft]
    if not nodes:
        parser.error("name lanes, or pass --minecraft or --all")
    unknown = [node for node in nodes if node not in by_node]
    if unknown:
        parser.error(f"not in the release matrix: {', '.join(unknown)}")
    overrides = {int(k): v for k, v in (item.split("=", 1) for item in args.java)}

    failures = 0
    for node in nodes:
        started = time.monotonic()
        try:
            ok, detail = smoke(by_node[node], matrix, args.work_dir.resolve(), overrides)
        except Exception as error:  # an installer or download failure is a failed lane, not a crash
            ok, detail = False, f"{type(error).__name__}: {error}"
        print(f"{node:18} {'PASS' if ok else 'FAIL'} in {time.monotonic() - started:.0f}s  {detail}", flush=True)
        failures += not ok
        server = args.work_dir.resolve() / "servers" / node
        if ok and not args.keep_servers and server.exists():
            shutil.rmtree(server)
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
