#!/usr/bin/env python3
"""Load test: many spawners and torches, three real players joining and walking among them.

Run it on demand (it is not part of CI); it reuses the multiplayer test's servers and clients
(scripts/e2e/multiplayer.py), so it needs the same tools and built jars:

    python3 scripts/e2e/load.py forge-1.20.1
    python3 scripts/e2e/load.py --minecraft 1.21.1 --spawners 400

Before anyone joins, the server gets a grid of spawners (disabled for Alice and Bob, so every one
of them is synced and suppressed) with a tracked torch next to each, over forceloaded chunks. The
server's ticks per second are measured with /debug start and /debug stop (available on every
supported version), and from 1.20.3 the time a tick takes with /tick query, while it is idle,
while the three players join (the join sends every spawner's state, the step that brought old
versions down) and while the players stand among the spawners as the torches go out.
The run fails if the server falls below --min-tps in any of them; the numbers are printed and
written to <work-dir>/lanes/<node>/load.json.
"""

from __future__ import annotations

import argparse
import json
import math
import re
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import multiplayer as mp  # noqa: E402

GRID_STEP = 8  # blocks between spawners (--grid-step)
ORIGIN = (0, mp.FEET, 0)


class LoadLane(mp.Lane):
    spawners = 400
    min_tps = 18.0
    grid_step = GRID_STEP

    def measure(self, label: str, seconds: float, during=None) -> float:
        self.run("debug start")
        started = time.monotonic()
        if during:
            during()
        time.sleep(max(0.0, seconds - (time.monotonic() - started)))
        answer = self.run("debug stop")
        match = re.search(r"\(([0-9.]+) ticks per second\)", answer)
        self.check(match is not None, f"could not read the tick rate: {answer!r}")
        tps = float(match.group(1))
        self.results_load[label] = tps
        # /tick (from 1.20.3) also tells how long the recent ticks took: the load below 20 TPS.
        query = self.run("tick query")
        mspt = re.search(r"Average time per tick: ([0-9.]+) ?ms", query)
        if mspt:
            self.results_load[f"{label} (ms per tick)"] = float(mspt.group(1))
        print(f"    {label:24} {tps:5.2f} ticks per second" + (f", {mspt.group(1)} ms per tick" if mspt else ""), flush=True)
        self.check(tps >= self.min_tps, f"{label}: the server ran at {tps:.2f} ticks per second (below {self.min_tps})")
        return tps

    def grid(self) -> list[tuple[int, int, int]]:
        side = math.ceil(math.sqrt(self.spawners))
        return [(ORIGIN[0] + i * self.grid_step, ORIGIN[1], ORIGIN[2] + j * self.grid_step)
                for i in range(side) for j in range(side)][:self.spawners]

    def step_joined(self) -> None:
        self.results_load: dict[str, float] = {}
        self.server.start()
        for rule in ("doDaylightCycle false", "advance_time false", "doMobSpawning false", "spawn_mobs false"):
            self.run(f"gamerule {rule}")
        blocks = self.grid()
        far = max(x for x, _, _ in blocks), max(z for _, _, z in blocks)
        self.run(f"forceload add {ORIGIN[0]} {ORIGIN[2]} {far[0]} {far[1]}")
        time.sleep(2)
        self.run("kill @e[type=!minecraft:player]")
        # Torches go out 30 s after they are placed: during the last measurement.
        self.run(f"{mp.MOD_ID} torch delay 30")
        for x, y, z in blocks:
            self.run(f'setblock {x} {y} {z} minecraft:spawner{{SpawnData:{{entity:{{id:"minecraft:pig",'
                     f'Tags:["{mp.SPAWNED_TAG}"]}}}},MultiplayerSpawnersData:{{DisabledPlayers:["Alice","Bob"]}}}}')
            self.run(f"setblock {x + 2} {y} {z} minecraft:torch")
        placed = self.run(f"execute if block {blocks[-1][0]} {blocks[-1][1]} {blocks[-1][2]} minecraft:spawner")
        self.check(placed.startswith("Test passed"), f"the last spawner was not placed: {placed}")
        self.notes.append(f"{len(blocks)} spawners and torches over {far[0] // 16 + 1}x{far[1] // 16 + 1} chunks")

    def step_idle(self) -> None:
        self.measure("idle", 10)

    def step_join(self) -> None:
        mark = len(self.server.lines)
        joined_at = time.monotonic()

        def join() -> None:
            for client in self.clients.values():
                client.launch(self.server.port)
            for name in mp.PLAYERS:
                self.wait_for(f"{name} to join", lambda n=name: f"{n} joined the game" in self.server.log_since(mark), 300, 1)
            self.notes.append(f"three players joined in {time.monotonic() - joined_at:.0f} s")
            # The middle of the grid: each player's view distance covers hundreds of the spawners.
            middle = self.grid()[len(self.grid()) // 2]
            for i, name in enumerate(mp.PLAYERS):
                self.prepare_player(name)
                self.run(f"tp {name} {middle[0] + 0.5 + 3 * i} {mp.FEET} {middle[2] + 3.5}")

        self.measure("three players joining", 0, join)

    def step_among_spawners(self) -> None:
        self.measure("players among spawners", 30)
        x, y, z = self.grid()[0]
        self.check(self.run(f"execute if block {x + 2} {y} {z} {mp.MOD_ID}:unlit_torch").startswith("Test passed"),
                   "the torches were not put out during the measurement")

    def step_stopped(self) -> None:
        super().step_stopped()
        (self.work / "load.json").write_text(json.dumps(
            {"lane": self.node, "spawners": self.spawners, "tps": self.results_load, "notes": self.notes}, indent=2) + "\n")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("nodes", nargs="*", help="artifact nodes, e.g. forge-1.20.1")
    parser.add_argument("--minecraft", action="append", default=[], help="every lane of this Minecraft version")
    parser.add_argument("--spawners", type=int, default=400, help="spawners (each with a torch) to place")
    parser.add_argument("--min-tps", type=float, default=18.0, help="fail below this many ticks per second")
    parser.add_argument("--grid-step", type=int, default=GRID_STEP,
                        help="blocks between spawners; the grid must fit the 256 chunks /forceload takes")
    parser.add_argument("--work-dir", type=Path, default=mp.ROOT / "build" / "load")
    args = parser.parse_args()

    matrix = json.loads(mp.MATRIX.read_text(encoding="utf-8"))
    by_node = {artifact["artifact_node"]: artifact for artifact in matrix["artifacts"]}
    nodes = list(args.nodes) + [node for node, artifact in by_node.items() if artifact["minecraft"] in args.minecraft]
    if not nodes or any(node not in by_node for node in nodes):
        parser.error("name lanes of the release matrix, or pass --minecraft")
    mp.CHECKPOINTS = ("joined", "idle", "join", "among-spawners", "stopped")
    LoadLane.spawners, LoadLane.min_tps, LoadLane.grid_step = args.spawners, args.min_tps, args.grid_step

    failures = 0
    for node in nodes:
        print(f"{node}:", flush=True)
        started = time.monotonic()
        try:
            results = LoadLane(by_node[node], matrix, args.work_dir.resolve(), {}).play()
            failed = [name for name, problems in results.items() if problems]
            ok, detail = not failed, ("within limits" if not failed else f"failed at {failed[0]}")
        except Exception as error:  # an install or launch failure is a failed lane, not a crash
            ok, detail = False, f"{type(error).__name__}: {error}"
        print(f"{node:18} {'PASS' if ok else 'FAIL'} in {time.monotonic() - started:.0f}s  {detail}", flush=True)
        failures += not ok
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
