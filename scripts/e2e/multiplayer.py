#!/usr/bin/env python3
"""Play the mod on a real dedicated server with three real Minecraft clients, per lane.

For each lane (artifact node of release/release-matrix.json) this starts the loader's dedicated
server with the production jar, installs the same loader's client from the matrix's pinned
installer and launches three real clients (Alice, Bob and Carol), each on its own virtual
display (Xvfb), that join the server. The players act through real input: xdotool moves the
mouse and presses buttons and keys in each game window, exactly as a person would. The server is
driven over RCON only for what a person would do with commands or by walking: placing the test
spawners, teleporting players to look at them, handing them items, reading block data.

The scenario, in the order it runs (each checkpoint fails the lane on its own):

  joined            the three clients joined the server
  initial-view      every player sees the untouched spawner as active
  spawns            the spawner spawns while players who have not disabled it are near
  group-disable     Alice's hit disables it for her and Bob (in the group radius), not Carol
  per-player-view   Alice and Bob now see it disabled; Carol still sees it active
  suppressed        it spawns nothing while only Alice and Bob are near ...
  active-for-others ... and spawns again as soon as Carol comes near
  break-message     a second hit tells Alice it is disabled and offers the break link
  joined-disable    Carol's hit on the spawner others disabled disables it for her too
  persistence       after a server restart the spawners still know who disabled them
  join-sync         rejoining players see each spawner the way it is for them
  break-link        Bob clicks the chat link and the spawner is removed
  torch             a torch Alice places next to a spawner is put out
  player-placed     a spawner Alice places is treated as vanilla and breaks normally
  stopped           the server stops cleanly and nothing logged an error from the mod
  evidence          every capture e2e/scenario-contract.json declares was taken by a passed check

Screenshots of what each player sees are written next to the logs. The visual checks classify the
spawner in the middle of the screen: the mod draws an active spawner with its own golden texture
and a disabled one with the vanilla texture.

The captures that the project site publishes are declared in the "evidence" section of
e2e/scenario-contract.json (roles and steps). When every checkpoint passes, each lane writes them
for the mod-base Pages adapter (scripts/pages/mod_base_adapter.py) below <work-dir>/evidence:

  runs/<artifact node>/multiplayer/report.json      the lane, its jar and each step's message
  runs/<artifact node>/multiplayer/<role>/<step>.png

    python3 scripts/e2e/multiplayer.py fabric-1.21.1
    python3 scripts/e2e/multiplayer.py --minecraft 1.20.1
    python3 scripts/e2e/multiplayer.py --all

Requires Xvfb and xdotool, and the hash-locked packages of e2e/requirements.txt
(python3 -m pip install --only-binary=:all: --require-hashes -r e2e/requirements.txt).
Build the jars first (scripts/release/build_matrix.py). Everything goes below --work-dir
(default build/multiplayer); the shared client install is reused between runs.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
import re
import shutil
import signal
import socket
import struct
import subprocess
import sys
import threading
import time
import uuid
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import server_smoke  # noqa: E402

ROOT = server_smoke.ROOT
MATRIX = server_smoke.MATRIX
MOD_ID = server_smoke.MOD_ID
PLAYERS = ("Alice", "Bob", "Carol")
WIDTH, HEIGHT = 1280, 720
RCON_PASSWORD = "multiplayerspawners-e2e"

CHECKPOINTS = ("joined", "initial-view", "spawns", "group-disable", "per-player-view", "suppressed",
               "active-for-others", "break-message", "joined-disable", "persistence", "join-sync",
               "break-link", "torch", "player-placed", "stopped", "evidence")

# The world is a flat world: the ground is the grass block layer at y=-61, players stand at y=-60.
FEET = -60
EYE = FEET + 1.62
S1 = (0, FEET, 10)       # the dungeon spawner the scenario is about
S2 = (40, FEET, 10)      # Carol's own spawner, far from the others
PLACED = (-30, FEET, 10)  # where Alice places a spawner herself
TORCH = (42, FEET, 12)   # within the 7 blocks of S2 that put a torch out
SPAWNED_TAG = "mps_e2e"
CONTRACT = ROOT / "e2e" / "scenario-contract.json"
EVIDENCE_SCENARIO = "multiplayer"


class ScenarioFailure(Exception):
    """A checkpoint failed; the message says what was expected and what happened."""


# --------------------------------------------------------------------------------------------
# Server


class Rcon:
    """The Minecraft RCON protocol: length-prefixed packets of (id, type, body)."""

    def __init__(self, port: int) -> None:
        self.socket = socket.create_connection(("127.0.0.1", port), timeout=30)
        self.next_id = 1
        if self._send(3, RCON_PASSWORD)[0] == -1:
            raise RuntimeError("RCON refused the password")

    def _send(self, kind: int, body: str) -> tuple[int, str]:
        self.next_id += 1
        data = struct.pack("<ii", self.next_id, kind) + body.encode("utf-8") + b"\0\0"
        self.socket.sendall(struct.pack("<i", len(data)) + data)
        size = struct.unpack("<i", self._read(4))[0]
        if not 10 <= size <= 1 << 20:
            raise RuntimeError(f"RCON answered a packet of {size} bytes")
        packet = self._read(size)
        request, _ = struct.unpack("<ii", packet[:8])
        return request, packet[8:-2].decode("utf-8", errors="replace")

    def _read(self, size: int) -> bytes:
        data = b""
        while len(data) < size:
            chunk = self.socket.recv(size - len(data))
            if not chunk:
                raise RuntimeError("RCON connection closed")
            data += chunk
        return data

    def command(self, command: str) -> str:
        return self._send(2, command)[1]

    def close(self) -> None:
        try:
            self.socket.close()
        except OSError:
            pass


def free_port() -> int:
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 0))
        return probe.getsockname()[1]


class Server:
    def __init__(self, artifact: dict, matrix: dict, jar: Path, work: Path, java: str) -> None:
        self.artifact, self.work = artifact, work
        self.directory = work / "server"
        if self.directory.exists():
            shutil.rmtree(self.directory)
        self.directory.mkdir(parents=True)
        self.command = server_smoke.install(artifact, matrix["installers"], self.directory,
                                            work.parent.parent / "cache", java)
        shutil.copy(jar, self.directory / "mods")
        (self.directory / "eula.txt").write_text("eula=true\n")
        self.port, self.rcon_port = free_port(), free_port()
        (self.directory / "server.properties").write_text(
            "online-mode=false\nlevel-type=minecraft\\:flat\ngenerate-structures=false\nspawn-protection=0\n"
            "view-distance=6\nsimulation-distance=6\nsync-chunk-writes=false\ndifficulty=peaceful\n"
            f"gamemode=survival\nserver-port={self.port}\nenable-rcon=true\nrcon.port={self.rcon_port}\n"
            f"rcon.password={RCON_PASSWORD}\nbroadcast-rcon-to-ops=false\nmax-players=10\n"
            # 26.3 turns the whitelist on by default.
            "white-list=false\nenforce-whitelist=false\n")
        self.java = java
        self.stuck_dumps: list[Path] = []
        self.last_stop_stuck = False
        self.process: subprocess.Popen | None = None
        self.rcon: Rcon | None = None
        self.lines: list[str] = []
        self.starts = 0

    def start(self) -> None:
        self.starts += 1
        log = (self.work / f"server-{self.starts}.log").open("w", encoding="utf-8")
        self.process = subprocess.Popen(self.command, cwd=self.directory, stdin=subprocess.PIPE,
                                        stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True,
                                        errors="replace", start_new_session=True)
        done = threading.Event()
        start = len(self.lines)

        def pump() -> None:
            for line in self.process.stdout:
                log.write(line)
                log.flush()
                self.lines.append(line)
                if re.search(r"Done \([0-9.,]+s\)!", line):
                    done.set()
            log.close()

        threading.Thread(target=pump, daemon=True).start()
        deadline = time.monotonic() + server_smoke.START_TIMEOUT
        while not done.is_set():
            if self.process.poll() is not None or time.monotonic() > deadline:
                last = [line.strip() for line in self.lines[start:] if line.strip()][-1:] or ["no output"]
                raise ScenarioFailure(f"the server did not finish starting (server-{self.starts}.log ends with: "
                                      f"{last[0][:300]})")
            time.sleep(0.5)
        # The server opens RCON a moment after it reports "Done".
        deadline = time.monotonic() + 60
        while True:
            try:
                self.rcon = Rcon(self.rcon_port)
                break
            except ConnectionRefusedError:
                if self.process.poll() is not None or time.monotonic() > deadline:
                    raise ScenarioFailure("the server never opened RCON") from None
                time.sleep(1)

    def run(self, command: str) -> str:
        return self.rcon.command(command)

    def log_since(self, mark: int) -> str:
        return "".join(self.lines[mark:])

    def stop(self) -> int | None:
        if self.process is None or self.process.poll() is not None:
            return self.process.returncode if self.process else None
        try:
            self.rcon.command("stop")
        except Exception:  # the server closes RCON while it stops
            pass
        if self.rcon:
            self.rcon.close()
        self.last_stop_stuck = False
        try:
            self.process.wait(timeout=server_smoke.STOP_TIMEOUT)
        except subprocess.TimeoutExpired:
            # Keep what every thread was doing before killing the server: the dump decides
            # whether the mod is what kept it alive (see stopped_cleanly).
            dump = self.work / f"server-{self.starts}-threads.txt"
            jstack = Path(self.java).with_name("jstack")
            with dump.open("w", encoding="utf-8") as out:
                subprocess.run([str(jstack), str(self.process.pid)], stdout=out, stderr=subprocess.STDOUT,
                               timeout=60, check=False)
            self.stuck_dumps.append(dump)
            self.last_stop_stuck = True
            os.killpg(self.process.pid, signal.SIGKILL)
            self.process.wait()
        time.sleep(1)
        return self.process.returncode

    def stopped_cleanly(self, code: int | None) -> str | None:
        """None when the last stop was clean, else what went wrong.

        A clean stop exits with 0. On a loaded machine a server sometimes saves everything and
        then never exits; that counts as clean only when the log shows the complete save and no
        thread of the mod is in the dump taken before it was killed."""

        if code == 0:
            return None
        log = (self.work / f"server-{self.starts}.log").read_text(encoding="utf-8", errors="replace")
        saved = "All dimensions are saved" in log
        dump = self.stuck_dumps[-1].read_text(encoding="utf-8", errors="replace") if self.last_stop_stuck else ""
        if saved and dump and MOD_ID not in dump.lower():
            return None
        return (f"the server exited with {code} when stopped"
                + ("" if saved else " before saving the world")
                + (f" and a thread of the mod kept it alive ({self.stuck_dumps[-1].name})" if MOD_ID in dump.lower() else ""))


# --------------------------------------------------------------------------------------------
# Clients


def version_id(artifact: dict) -> str:
    loader, minecraft, version = artifact["loader"], artifact["minecraft"], artifact["loader_version"]
    if loader == "fabric":
        return f"fabric-loader-{version}-{minecraft}"
    if loader == "forge":
        return f"{minecraft}-forge-{version.removeprefix(minecraft + '-')}"
    return f"neoforge-{version}"


def install_client(artifact: dict, matrix: dict, minecraft: Path, cache: Path, java: str) -> str:
    """Install vanilla Minecraft and the lane's loader into the shared launcher directory, the way
    the official launcher lays it out, and return the version id to launch."""

    import minecraft_launcher_lib  # noqa: PLC0415

    minecraft.mkdir(parents=True, exist_ok=True)
    profiles = minecraft / "launcher_profiles.json"
    if not profiles.exists():
        profiles.write_text('{"profiles": {}}\n')
    vid = version_id(artifact)
    profile = minecraft / "versions" / vid / f"{vid}.json"
    marker = profile.with_suffix(".installed")
    if marker.exists():
        return vid
    minecraft_launcher_lib.install.install_minecraft_version(artifact["minecraft"], str(minecraft))
    pinned = matrix["installers"][artifact["installer"]]
    installer = server_smoke.download(pinned["url"], pinned["sha256"], cache / f"{artifact['installer']}-installer.jar")
    if artifact["loader"] == "fabric":
        command = [java, "-jar", str(installer), "client", "-dir", str(minecraft), "-mcversion",
                   artifact["minecraft"], "-loader", artifact["loader_version"], "-noprofile"]
    else:
        flag = "--installClient" if artifact["loader"] == "forge" else "--install-client"
        command = [java, "-jar", str(installer), flag, str(minecraft)]
    subprocess.run(command, cwd=minecraft, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.STDOUT)
    # The official launcher takes an inherited profile's jar from its parent; the launcher library
    # needs that said. Forge and NeoForge build the game module themselves and must not get the
    # vanilla jar on the class path.
    data = json.loads(profile.read_text(encoding="utf-8"))
    if artifact["loader"] == "fabric":
        data["jar"] = data["inheritsFrom"]
    else:
        data.pop("jar", None)
        profile.with_suffix(".jar").unlink(missing_ok=True)
    profile.write_text(json.dumps(data, indent=2), encoding="utf-8")
    minecraft_launcher_lib.install.install_minecraft_version(vid, str(minecraft))
    marker.write_text("")
    return vid


OPTIONS = """guiScale:2
renderClouds:false
renderDistance:6
simulationDistance:6
pauseOnLostFocus:false
narrator:0
fullscreen:false
bobView:false
tutorialStep:none
onboardAccessibility:false
joinedFirstServer:true
skipMultiplayerWarning:true
fov:0.0
chatOpacity:1.0
soundCategory_master:0.0
"""


EXTRACT_PROPERTIES = ("-Dorg.lwjgl.system.SharedLibraryExtractPath=", "-Djna.tmpdir=", "-Dio.netty.native.workdir=")


class Client:
    def __init__(self, name: str, work: Path, mods: list[Path], minecraft: Path, vid: str, java: str) -> None:
        self.name, self.work, self.minecraft, self.vid, self.java = name, work, minecraft, vid, java
        self.game = work / "clients" / name
        if self.game.exists():
            shutil.rmtree(self.game)
        (self.game / "mods").mkdir(parents=True)
        for mod in mods:
            shutil.copy(mod, self.game / "mods")
        (self.game / "options.txt").write_text(OPTIONS)
        self.display: str | None = None
        self.xvfb: subprocess.Popen | None = None
        self.process: subprocess.Popen | None = None
        self.launches = 0
        self.log_path = self.game / "client-1.log"

    def start_display(self) -> None:
        read, write = os.pipe()
        self.xvfb = subprocess.Popen(["Xvfb", "-displayfd", str(write), "-screen", "0", f"{WIDTH}x{HEIGHT}x24",
                                      "-nolisten", "tcp"], pass_fds=(write,), stdout=subprocess.DEVNULL,
                                     stderr=subprocess.DEVNULL)
        os.close(write)
        with os.fdopen(read) as pipe:
            number = pipe.readline().strip()
        if not number.isdigit():
            raise ScenarioFailure(f"Xvfb did not start for {self.name}")
        self.display = f":{number}"

    def launch(self, port: int) -> None:
        import minecraft_launcher_lib  # noqa: PLC0415

        if self.display is None:
            self.start_display()
        self.launches += 1
        self.log_path = self.work / f"client-{self.name}-{self.launches}.log"
        options = minecraft_launcher_lib.utils.generate_test_options()
        options.update(
            username=self.name, uuid=uuid.uuid5(uuid.NAMESPACE_DNS, f"{MOD_ID}-e2e-{self.name}").hex,
            token="offline", executablePath=self.java, gameDirectory=str(self.game), customResolution=True,
            resolutionWidth=str(WIDTH), resolutionHeight=str(HEIGHT), quickPlayMultiplayer=f"127.0.0.1:{port}",
            jvmArguments=["-Xms512M", "-Xmx2G", "-Dfml.earlyprogresswindow=false"])
        command = minecraft_launcher_lib.command.get_minecraft_command(self.vid, str(self.minecraft), options)
        # The profile has every game of a version extract its native libraries into one shared
        # directory; games started together then overwrite a library while another one loads it
        # (SIGBUS in dlopen). Give each game its own.
        natives = self.game / "natives"
        natives.mkdir(exist_ok=True)
        for prefix in EXTRACT_PROPERTIES:
            command = [f"{prefix}{natives}" if argument.startswith(prefix) else argument for argument in command]
        environment = dict(os.environ, DISPLAY=self.display, SDL_VIDEO_FORCE_EGL="1")
        with self.log_path.open("wb") as log:
            self.process = subprocess.Popen(command, cwd=self.game, env=environment, stdout=log,
                                            stderr=subprocess.STDOUT, start_new_session=True)

    def alive(self) -> bool:
        return self.process is not None and self.process.poll() is None

    def kill(self) -> None:
        if self.alive():
            os.killpg(self.process.pid, signal.SIGTERM)
            try:
                self.process.wait(timeout=20)
            except subprocess.TimeoutExpired:
                os.killpg(self.process.pid, signal.SIGKILL)
                self.process.wait()

    def close(self) -> None:
        self.kill()
        if self.xvfb and self.xvfb.poll() is None:
            self.xvfb.terminate()
            self.xvfb.wait(timeout=10)

    def log(self) -> str:
        return self.log_path.read_text(encoding="utf-8", errors="replace") if self.log_path.exists() else ""

    def chat(self) -> list[str]:
        return [line.split("[CHAT] ", 1)[1].strip() for line in self.log().splitlines() if "[CHAT] " in line]

    def xdotool(self, *arguments: str) -> None:
        subprocess.run(["xdotool", *arguments], env=dict(os.environ, DISPLAY=self.display), check=True,
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=30)

    def focus(self) -> None:
        # GLFW titles its window "Minecraft* <version>" where xdotool's name search sees it; the SDL3
        # window of 26.3 only matches by class (its hidden helper window is never visible).
        window: list[str] = []
        for search in (["--name", "Minecraft"], ["--class", "."]):
            window = subprocess.run(["xdotool", "search", "--onlyvisible", *search],
                                    env=dict(os.environ, DISPLAY=self.display), capture_output=True, text=True,
                                    timeout=30).stdout.split()
            if window:
                break
        if not window:
            raise ScenarioFailure(f"{self.name}'s game window is not on its display")
        self.xdotool("windowfocus", "--sync", window[0])

    def screenshot(self, name: str):
        from PIL import ImageGrab  # noqa: PLC0415

        image = ImageGrab.grab(xdisplay=self.display).convert("RGB")
        image.save(self.work / "screenshots" / f"{name}.png")
        return image

    def left_click(self) -> None:
        # One short click is one hit: in survival, holding the button keeps breaking the block
        # again on every tick once it breaks instantly.
        self.focus()
        self.xdotool("click", "1")

    def right_click(self) -> None:
        self.focus()
        self.xdotool("click", "3")


# --------------------------------------------------------------------------------------------
# What a player sees


def warm_fraction(image) -> float:
    """The share of golden pixels in the middle of the screen, where the scenario puts the spawner.

    The mod's active spawner texture is golden; the vanilla (disabled) one is dark blue-grey and
    the flat world around it is green grass and blue sky."""

    # A spawner three blocks away covers about 200 pixels around the crosshair.
    crop = image.crop((WIDTH // 2 - 80, HEIGHT // 2 - 70, WIDTH // 2 + 80, HEIGHT // 2 + 90))
    pixels = crop.get_flattened_data()
    warm = sum(1 for r, g, b in pixels if r > 150 and g > 70 and b < 90 and r > g + 20)
    return warm / len(pixels)


ACTIVE_MIN, DISABLED_MAX = 0.03, 0.01


def link_position(image) -> tuple[int, int] | None:
    """The middle of the red "[Click here to break it]" text, on the lowest chat line that has it.

    Chat lines are above the hotbar, whose hearts are a different red."""

    pixels = image.load()
    rows: dict[int, list[int]] = {}
    for y in range(HEIGHT // 3, HEIGHT - 80):
        for x in range(0, WIDTH * 2 // 3):
            r, g, b = pixels[x, y]
            if r > 240 and 70 < g < 100 and 70 < b < 100:
                rows.setdefault(y, []).append(x)
    if not rows:
        return None
    ys = sorted(rows)
    bands, band = [], [ys[0]]
    for y in ys[1:]:
        if y - band[-1] > 2:
            bands.append(band)
            band = [y]
        else:
            band.append(y)
    bands.append(band)
    lowest = bands[-1]
    xs = [x for y in lowest for x in rows[y]]
    return (min(xs) + max(xs)) // 2, (lowest[0] + lowest[-1]) // 2


# --------------------------------------------------------------------------------------------
# The scenario


class Lane:
    def __init__(self, artifact: dict, matrix: dict, work: Path, overrides: dict[int, str]) -> None:
        self.artifact, self.matrix = artifact, matrix
        self.node = artifact["artifact_node"]
        self.work = work / "lanes" / self.node
        if self.work.exists():
            shutil.rmtree(self.work)
        (self.work / "screenshots").mkdir(parents=True)
        self.java = server_smoke.find_java(artifact["java"], overrides)
        self.jar = ROOT / artifact["jar"].replace("{mod_version}", matrix["project"]["mod_version"])
        if not self.jar.exists():
            raise ScenarioFailure(f"missing {self.jar.relative_to(ROOT)}; build it first")
        self.cache = work / "cache"
        self.minecraft = work / "minecraft"
        self.server = Server(artifact, matrix, self.jar, self.work, self.java)
        mods = [self.jar, *(self.server.directory / "mods").glob("fabric-api-*.jar")]
        vid = install_client(artifact, matrix, self.minecraft, self.cache, self.java)
        self.clients = {name: Client(name, self.work, mods, self.minecraft, vid, self.java) for name in PLAYERS}
        self.results: dict[str, list[str]] = {}
        self.notes: list[str] = []
        self.captures: dict[tuple[str, str], tuple[Path, str]] = {}
        self.evidence = work / "evidence" / "runs" / self.node / EVIDENCE_SCENARIO
        if self.evidence.exists():
            shutil.rmtree(self.evidence)
        self.started = time.monotonic()

    # -- helpers -------------------------------------------------------------------------------

    def run(self, command: str) -> str:
        return self.server.run(command)

    def check(self, condition: bool, message: str) -> None:
        if not condition:
            raise ScenarioFailure(message)

    def wait_for(self, what: str, predicate, timeout: float, interval: float = 0.5):
        deadline = time.monotonic() + timeout
        while True:
            value = predicate()
            if value:
                return value
            if time.monotonic() > deadline:
                raise ScenarioFailure(f"timed out after {timeout:.0f}s waiting for {what}")
            for client in self.clients.values():
                if client.process is not None and not client.alive():
                    raise ScenarioFailure(f"{client.name}'s game exited (see {client.log_path.name})")
            time.sleep(interval)

    def look(self, player: str, at: tuple[float, float, float], stand: tuple[float, float, float]) -> None:
        """Teleport `player` to stand at `stand` looking at the point `at`."""

        dx, dy, dz = at[0] - stand[0], at[1] - (stand[1] + 1.62), at[2] - stand[2]
        yaw = -math.degrees(math.atan2(dx, dz))
        pitch = -math.degrees(math.atan2(dy, math.hypot(dx, dz)))
        self.run(f"tp {player} {stand[0]:.2f} {stand[1]:.2f} {stand[2]:.2f} {yaw:.2f} {pitch:.2f}")

    def look_at_block(self, player: str, block: tuple[int, int, int], stand_offset: tuple[float, float]) -> None:
        x, y, z = block
        self.look(player, (x + 0.5, y + 0.5, z + 0.5), (x + 0.5 + stand_offset[0], FEET, z + 0.5 + stand_offset[1]))

    def park(self, player: str, block: tuple[int, int, int], distance: float = 30) -> None:
        """Move `player` well away from `block` (out of the spawner and group-disable ranges)."""

        self.look(player, (block[0] + 0.5, EYE, block[2] + 0.5 + 100), (block[0] + 0.5, FEET, block[2] + 0.5 - distance))

    def disabled_players(self, block: tuple[int, int, int]) -> set[str]:
        x, y, z = block
        answer = self.run(f"data get block {x} {y} {z} MultiplayerSpawnersData.DisabledPlayers")
        if "found no elements" in answer.lower():
            return set()
        if "has the following block data" not in answer:
            raise ScenarioFailure(f"could not read the spawner at {block}: {answer}")
        return set(re.findall(r'"([A-Za-z0-9_]+)"', answer.split(":", 1)[-1]))

    def block_is(self, block: tuple[int, int, int], block_id: str) -> bool:
        x, y, z = block
        return self.run(f"execute if block {x} {y} {z} {block_id}").startswith("Test passed")

    def spawned(self) -> int:
        # Only those in the world: the ones dropped into the void take a moment to die.
        answer = self.run(f"execute positioned 0 {FEET} 0 if entity @e[tag={SPAWNED_TAG},distance=..100]")
        # "Test passed, count: 2" until 1.21.8, "Test passed. Count: 2" from 1.21.9.
        match = re.search(r"count: (\d+)", answer, re.IGNORECASE)
        return int(match.group(1)) if match else 0

    def place_spawner(self, block: tuple[int, int, int]) -> None:
        x, y, z = block
        answer = self.run(
            f'setblock {x} {y} {z} minecraft:spawner{{SpawnData:{{entity:{{id:"minecraft:pig",Tags:["{SPAWNED_TAG}"]}}}},'
            f"Delay:0s,MinSpawnDelay:200s,MaxSpawnDelay:400s,SpawnCount:1s,MaxNearbyEntities:3s,RequiredPlayerRange:16s}}")
        self.check("Changed the block" in answer, f"could not place a spawner: {answer}")

    def ready_to_spawn(self, block: tuple[int, int, int]) -> None:
        """Clear what the spawners spawned and make `block` spawn at its next active tick."""

        self.clear_spawned()
        x, y, z = block
        self.run(f"data merge block {x} {y} {z} {{Delay:0s}}")

    def clear_spawned(self) -> None:
        """Drop the spawned pigs into the void (no drops, no death smoke) and remove loose items."""

        self.run(f"tp @e[tag={SPAWNED_TAG}] 0 -500 0")
        self.run("kill @e[type=minecraft:item]")

    def hold(self, player: str, item: str) -> None:
        answer = self.run(f"item replace entity {player} hotbar.0 with {item}")
        self.check("Replaced" in answer, f"could not give {player} {item}: {answer}")

    def position(self, player: str) -> tuple[float, float, float]:
        answer = self.run(f"data get entity {player} Pos")
        values = re.findall(r"(-?[0-9.]+)d", answer)
        if len(values) != 3:
            raise ScenarioFailure(f"could not read where {player} is: {answer}")
        return float(values[0]), float(values[1]), float(values[2])

    def clear_view(self, player: str, block: tuple[int, int, int]) -> None:
        """Move the other players near `block` aside, out of every camera's line to it.

        They stay within the spawner and group ranges, so the checks keep their meaning; a
        player who spawned on a camera spot would otherwise fill that camera's screen."""

        spots = iter(((5.5, -4.5), (-4.5, -4.5)))
        for other in PLAYERS:
            if other == player:
                continue
            x, _, z = self.position(other)
            if math.hypot(x - block[0] - 0.5, z - block[2] - 0.5) <= 8:
                dx, dz = next(spots)
                self.look(other, (block[0] + 0.5, block[1] + 0.5, block[2] + 0.5),
                          (block[0] + 0.5 + dx, FEET, block[2] + 0.5 + dz))

    def view(self, player: str, block: tuple[int, int, int], offset: tuple[float, float], name: str) -> float:
        self.clear_view(player, block)
        self.look_at_block(player, block, offset)
        self.clear_spawned()
        time.sleep(2.5)
        return warm_fraction(self.clients[player].screenshot(name))

    def capture(self, player: str, step: str, name: str, message: str) -> None:
        """Record screenshots/<name>.png as the contract capture <player>/<step>, with the message of
        the check it passed."""

        self.captures[(player.lower(), step)] = (self.work / "screenshots" / f"{name}.png", message)

    def expect_view(self, player: str, block: tuple[int, int, int], offset: tuple[float, float], name: str,
                    active: bool, capture: str | None = None, context: str = "") -> None:
        # The view changes a few frames after the packet; retry for a while before failing.
        deadline = time.monotonic() + 15
        while True:
            warm = self.view(player, block, offset, name)
            if (warm >= ACTIVE_MIN) if active else (warm <= DISABLED_MAX):
                self.notes.append(f"{name}: golden share {warm:.3f}")
                if capture:
                    state, bound = (("active", f"at least {ACTIVE_MIN:.0%}") if active
                                    else ("disabled", f"at most {DISABLED_MAX:.0%}"))
                    self.capture(player, capture, name,
                                 f"{player} sees the spawner at {block[0]} {block[1]} {block[2]} {state}"
                                 f"{context}: {warm:.1%} of the pixels around the crosshair are the mod's golden "
                                 f"active texture ({bound} required).")
                return
            if time.monotonic() > deadline:
                state = "active (golden)" if active else "disabled (vanilla)"
                raise ScenarioFailure(f"{player} should see the spawner at {block} {state}; "
                                      f"golden share {warm:.3f} (screenshots/{name}.png)")

    def chat_contains(self, player: str, text: str, after: int) -> bool:
        return any(text in line for line in self.clients[player].chat()[after:])

    def expect_chat(self, player: str, text: str, after: int, timeout: float = 10) -> None:
        self.wait_for(f"{player} to be told {text!r}", lambda: self.chat_contains(player, text, after), timeout)

    def hit(self, player: str, block: tuple[int, int, int], offset: tuple[float, float] = (0, -2.5)) -> None:
        self.hold(player, "minecraft:netherite_pickaxe")
        self.look_at_block(player, block, offset)
        time.sleep(1.5)
        self.clients[player].left_click()
        time.sleep(1.5)

    def prepare_player(self, player: str) -> None:
        self.run(f"effect give {player} minecraft:haste 99999 254 true")
        self.run(f"effect give {player} minecraft:resistance 99999 4 true")

    def join_all(self) -> None:
        mark = len(self.server.lines)
        for client in self.clients.values():
            client.launch(self.server.port)
        for name in PLAYERS:
            self.wait_for(f"{name} to join", lambda n=name: f"{n} joined the game" in self.server.log_since(mark), 300, 2)
        time.sleep(5)
        for name in PLAYERS:
            self.prepare_player(name)

    # -- steps ---------------------------------------------------------------------------------

    def step_joined(self) -> None:
        self.server.start()
        self.run("time set 6000")
        for rule in ("doDaylightCycle false", "advance_time false"):
            self.run(f"gamerule {rule}")
        self.run("kill @e[type=!minecraft:player]")
        # Keep the scenario's chunks loaded with nobody near (from 1.21.9 there are no spawn
        # chunks). The world saves this, so after a restart they are read back from disk at once.
        answer = self.run(f"forceload add {PLACED[0] - 16} 0 {S2[0] + 16} 16")
        self.check("forceload" in answer.lower() or "marked" in answer.lower() or "chunk" in answer.lower(),
                   f"could not keep the scenario's chunks loaded: {answer}")
        self.join_all()

    def step_initial_view(self) -> None:
        self.place_spawner(S1)
        self.place_spawner(S2)
        self.park("Carol", S1)
        for player, offset in (("Alice", (0, -3)), ("Bob", (0, 3))):
            self.expect_view(player, S1, offset, f"01-{player.lower()}-s1-initial", active=True,
                             capture="s1_initial", context=" before anyone hit it")
        self.expect_view("Carol", S2, (0, -3), "01-carol-s2-initial", active=True,
                         capture="s2_initial", context=" before anyone hit it")
        self.park("Carol", S1)

    def step_spawns(self) -> None:
        self.ready_to_spawn(S1)
        time.sleep(1)
        self.wait_for("the spawner to spawn pigs near Alice and Bob", lambda: self.spawned() > 0, 30)
        self.clear_spawned()

    def step_group_disable(self) -> None:
        alice, bob = len(self.clients["Alice"].chat()), len(self.clients["Bob"].chat())
        self.look_at_block("Bob", S1, (2, 3))
        self.hit("Alice", S1, (0, -3))
        self.wait_for("Alice and Bob in DisabledPlayers", lambda: self.disabled_players(S1) == {"Alice", "Bob"}, 10)
        self.check(self.block_is(S1, "minecraft:spawner"), "the spawner was broken by the first hit")
        self.expect_chat("Alice", "Group disabling: Spawner disabled for 1 nearby player(s).", alice)
        self.expect_chat("Bob", "A spawner in this area has been disabled by Alice.", bob)
        # Two hits are the default: the first one does not offer to break the spawner yet.
        self.check(not self.chat_contains("Alice", "The spawner is disabled for you", alice),
                   "the first hit already showed the break message (requiredBreakAttempts is 2)")

    def step_per_player_view(self) -> None:
        self.expect_view("Alice", S1, (0, -3), "02-alice-s1-disabled", active=False, capture="s1_disabled",
                         context=" after her own hit (the server lists Alice and Bob as having disabled it)")
        self.expect_view("Bob", S1, (0, 3), "02-bob-s1-disabled", active=False, capture="s1_disabled",
                         context=" after Alice's hit within the group radius")
        self.expect_view("Carol", S1, (3, 0), "02-carol-s1-active", active=True, capture="s1_active",
                         context=" that Alice and Bob disabled, since she was out of the group radius")
        self.park("Carol", S1)

    def step_suppressed(self) -> None:
        self.ready_to_spawn(S1)
        time.sleep(8)
        count = self.spawned()
        self.check(count == 0, f"the spawner spawned {count} pig(s) with only Alice and Bob (who disabled it) near")

    def step_active_for_others(self) -> None:
        self.look_at_block("Carol", S1, (3, 0))
        self.wait_for("the spawner to spawn again once Carol is near", lambda: self.spawned() > 0, 30)
        self.park("Carol", S1)
        self.clear_spawned()

    def step_break_message(self) -> None:
        alice = len(self.clients["Alice"].chat())
        self.hit("Alice", S1, (0, -3))
        self.expect_chat("Alice", "The spawner is disabled for you and nearby players.", alice)
        self.check(self.chat_contains("Alice", "[Click here to break it]", alice), "the message has no break link")
        self.check(self.block_is(S1, "minecraft:spawner"), "the second hit broke the spawner")

    def step_joined_disable(self) -> None:
        # Carol, alone at her own spawner: her hit disables it for nobody else.
        self.park("Alice", S2)
        self.park("Bob", S2, 40)
        self.hit("Carol", S2, (0, -3))
        self.wait_for("Carol alone in S2's DisabledPlayers", lambda: self.disabled_players(S2) == {"Carol"}, 10)
        # Carol then hits the spawner Alice and Bob disabled: it is now disabled for her too.
        self.park("Alice", S1)
        self.park("Bob", S1, 40)
        carol = len(self.clients["Carol"].chat())
        self.hit("Carol", S1, (3, 0))
        self.wait_for("Carol in S1's DisabledPlayers",
                      lambda: self.disabled_players(S1) == {"Alice", "Bob", "Carol"}, 10)
        self.expect_chat("Carol", "It will no longer spawn creatures while you are nearby.", carol)
        self.expect_view("Carol", S1, (3, 0), "03-carol-s1-disabled", active=False, capture="s1_disabled",
                         context=" after her own hit on a spawner others disabled")

    def step_persistence(self) -> None:
        for client in self.clients.values():
            client.kill()
        stuck = len(self.server.stuck_dumps)
        problem = self.server.stopped_cleanly(self.server.stop())
        self.check(problem is None, problem or "")
        if len(self.server.stuck_dumps) > stuck:
            self.notes.append(f"the server saved and stopped but did not exit; killed ({self.server.stuck_dumps[-1].name})")
        self.server.start()
        self.check(self.disabled_players(S1) == {"Alice", "Bob", "Carol"},
                   f"after the restart S1 is disabled for {sorted(self.disabled_players(S1))}")
        self.check(self.disabled_players(S2) == {"Carol"},
                   f"after the restart S2 is disabled for {sorted(self.disabled_players(S2))}")

    def step_join_sync(self) -> None:
        self.run("time set 6000")
        self.join_all()
        self.park("Carol", S1)
        self.expect_view("Alice", S1, (0, -3), "04-alice-s1-rejoined", active=False, capture="s1_rejoined",
                         context=" after a server restart and rejoin")
        self.expect_view("Alice", S2, (0, -3), "04-alice-s2-rejoined", active=True, capture="s2_rejoined",
                         context=" (only Carol disabled it) after a server restart and rejoin")
        self.expect_view("Carol", S2, (3, 0), "04-carol-s2-rejoined", active=False, capture="s2_rejoined",
                         context=" (she disabled it) after a server restart and rejoin")

    def step_break_link(self) -> None:
        bob = len(self.clients["Bob"].chat())
        self.park("Alice", S1)
        self.hit("Bob", S1, (0, 3))
        self.expect_chat("Bob", "[Click here to break it]", bob)
        self.check(self.block_is(S1, "minecraft:spawner"), "Bob's hit broke the spawner")
        client = self.clients["Bob"]
        client.focus()
        client.xdotool("key", "t")
        time.sleep(1.5)
        position = link_position(client.screenshot("05-bob-chat-open"))
        self.check(position is not None, "the break link is not visible in Bob's open chat (screenshots/05-bob-chat-open.png)")
        client.xdotool("mousemove", str(position[0]), str(position[1]))
        time.sleep(0.5)
        client.xdotool("click", "1")
        self.wait_for("the spawner to be removed", lambda: self.block_is(S1, "minecraft:air"), 10)
        self.expect_chat("Bob", "Spawner successfully broken and removed!", bob)
        client.xdotool("key", "Escape")
        self.capture("Bob", "break_link", "05-bob-chat-open",
                     f"Bob's open chat shows the red break link at {position[0]},{position[1]}; clicking it removed "
                     f"the spawner at {S1[0]} {S1[1]} {S1[2]} and the chat said: Spawner successfully broken and removed!")

    def step_torch(self) -> None:
        answer = self.run(f"{MOD_ID} torch delay 3")
        self.check("3 seconds" in answer, f"the torch delay command answered {answer!r}")
        self.park("Carol", S2)
        x, y, z = TORCH
        self.hold("Alice", "minecraft:torch")
        self.look("Alice", (x + 0.5, y - 1, z + 0.5), (x + 0.5, FEET, z + 0.5))
        time.sleep(1.5)
        self.clients["Alice"].right_click()
        self.wait_for("Alice's torch to be placed", lambda: self.block_is(TORCH, "minecraft:torch"), 10)
        self.wait_for("the torch to be put out", lambda: self.block_is(TORCH, f"{MOD_ID}:unlit_torch"), 30)
        self.look_at_block("Alice", TORCH, (0, -2))
        time.sleep(2)
        self.clients["Alice"].screenshot("06-alice-torch-out")
        distance = math.dist((x, z), (S2[0], S2[2]))
        self.capture("Alice", "torch_out", "06-alice-torch-out",
                     f"The torch Alice placed at {x} {y} {z}, {distance:.1f} blocks from the spawner at {S2[0]} {S2[1]} "
                     f"{S2[2]}, became {MOD_ID}:unlit_torch after the 3-second delay.")

    def step_player_placed(self) -> None:
        x, y, z = PLACED
        self.hold("Alice", "minecraft:spawner")
        stand = (x + 0.5, FEET, z + 0.5 - 2)
        self.look("Alice", (x + 0.5, y, z + 0.5), stand)
        time.sleep(1.5)
        self.clients["Alice"].right_click()
        self.wait_for("Alice's spawner to be placed", lambda: self.block_is(PLACED, "minecraft:spawner"), 10)
        answer = self.run(f"data get block {x} {y} {z} MultiplayerSpawnersData.IsPlayerPlaced")
        self.check(re.search(r"\b(1b|true)\b", answer) is not None, f"the placed spawner is not marked player-placed: {answer}")
        self.hit("Alice", PLACED, (0, -2))
        self.wait_for("the player-placed spawner to break like a vanilla one",
                      lambda: self.block_is(PLACED, "minecraft:air"), 10)

    def step_stopped(self) -> None:
        for client in self.clients.values():
            client.kill()
        stuck = len(self.server.stuck_dumps)
        problem = self.server.stopped_cleanly(self.server.stop())
        self.check(problem is None, problem or "")
        if len(self.server.stuck_dumps) > stuck:
            self.notes.append(f"the server saved and stopped but did not exit; killed ({self.server.stuck_dumps[-1].name})")
        problems = [line.strip()[:300] for line in self.server.lines
                    if re.search(r"/(ERROR|FATAL)\]", line) and MOD_ID in line.lower()]
        self.check(not problems, "the server logged errors from the mod: " + " | ".join(problems[:3]))
        for client in self.clients.values():
            for path in self.work.glob(f"client-{client.name}-*.log"):
                text = path.read_text(encoding="utf-8", errors="replace")
                errors = [line.strip()[:300] for line in text.splitlines()
                          if re.search(r"/(ERROR|FATAL)\]", line) and MOD_ID in line.lower()]
                self.check(not errors, f"{client.name}'s game logged errors from the mod: " + " | ".join(errors[:3]))
                self.check("Mixin apply failed" not in text and "MixinApplyError" not in text,
                           f"a mixin failed in {client.name}'s game ({path.name})")

    def step_evidence(self) -> None:
        """Write the captures the contract declares, with their messages, for the Pages adapter."""

        contract_bytes = CONTRACT.read_bytes()
        scenario = next(item for item in json.loads(contract_bytes)["evidence"]["scenarios"]
                        if item["id"] == EVIDENCE_SCENARIO)
        roles = {}
        for role in scenario["roles"]:
            steps = []
            for step in role["steps"]:
                captured = self.captures.get((role["role"], step["id"]))
                self.check(captured is not None, f"no passed check took the capture {role['role']}/{step['id']}")
                source, message = captured
                target = self.evidence / role["role"] / f"{step['id']}.png"
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(source, target)
                steps.append({"id": step["id"], "status": "pass", "message": message,
                              "screenshot": f"{role['role']}/{step['id']}.png"})
            roles[role["role"]] = {"steps": steps}
        report = {
            "kind": "multiplayerspawners.e2e.report", "schema_version": 1, "status": "pass",
            "artifact_node": self.node, "minecraft": self.artifact["minecraft"], "loader": self.artifact["loader"],
            "java": self.artifact["java"], "scenario": EVIDENCE_SCENARIO,
            "contract_sha256": hashlib.sha256(contract_bytes).hexdigest(),
            "production_sha256": hashlib.sha256(self.jar.read_bytes()).hexdigest(),
            "elapsed_s": round(time.monotonic() - self.started), "roles": roles,
        }
        (self.evidence / "report.json").write_text(json.dumps(report, indent=2, sort_keys=True) + "\n")

    def play(self) -> dict[str, list[str]]:
        try:
            for checkpoint in CHECKPOINTS:
                step = getattr(self, "step_" + checkpoint.replace("-", "_"))
                started = time.monotonic()
                try:
                    step()
                    self.results[checkpoint] = []
                    print(f"  {checkpoint:18} pass ({time.monotonic() - started:.0f}s)", flush=True)
                except ScenarioFailure as failure:
                    self.results[checkpoint] = [str(failure)]
                    print(f"  {checkpoint:18} FAIL {failure}", flush=True)
                    for client in self.clients.values():
                        if client.alive():
                            try:
                                client.screenshot(f"failed-{checkpoint}-{client.name.lower()}")
                            except Exception:  # evidence only; the failure is already recorded
                                pass
                    break
        finally:
            for client in self.clients.values():
                client.close()
            self.server.stop()
            (self.work / "result.json").write_text(json.dumps(
                {"lane": self.node, "checkpoints": {name: self.results.get(name, ["not reached"]) for name in CHECKPOINTS},
                 "notes": self.notes}, indent=2) + "\n")
        return {name: self.results.get(name, ["not reached"]) for name in CHECKPOINTS}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("nodes", nargs="*", help="artifact nodes, e.g. fabric-1.21.1")
    parser.add_argument("--all", action="store_true", help="every lane of the release matrix")
    parser.add_argument("--minecraft", action="append", default=[], help="every lane of this Minecraft version")
    parser.add_argument("--work-dir", type=Path, default=ROOT / "build" / "multiplayer")
    parser.add_argument("--java", action="append", default=[], metavar="VERSION=HOME",
                        help="Java home for a feature version, e.g. 25=/opt/jdk-25")
    args = parser.parse_args()

    for tool in ("Xvfb", "xdotool"):
        if shutil.which(tool) is None:
            parser.error(f"{tool} is not installed")
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
        print(f"{node}:", flush=True)
        try:
            results = Lane(by_node[node], matrix, args.work_dir.resolve(), overrides).play()
            failed = [name for name, problems in results.items() if problems]
            ok, detail = not failed, ("every checkpoint passed" if not failed else f"failed at {failed[0]}")
        except Exception as error:  # an install or launch failure is a failed lane, not a crash
            ok, detail = False, f"{type(error).__name__}: {error}"
        print(f"{node:18} {'PASS' if ok else 'FAIL'} in {time.monotonic() - started:.0f}s  {detail}", flush=True)
        failures += not ok
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
