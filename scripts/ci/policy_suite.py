"""MultiplayerSpawners: the mod-base ``policy`` hook, run from the tested checkout.

It checks the release matrix with its own validator and runs the adapter's tests
(``scripts/ci/tests``). It writes nothing; the exit status is its result.
"""

from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path

import mod_base_build_adapter as adapter

INVENTORY = "release/release-matrix.json"


def run(checkout: Path) -> None:
    sys.path.insert(0, str(checkout / "scripts" / "release"))
    import matrix  # noqa: PLC0415 - the tested checkout's validator

    problems = matrix.validate(json.loads((checkout / INVENTORY).read_text(encoding="utf-8")))
    for problem in problems:
        print(f"release matrix: {problem}", flush=True)
    suite = unittest.defaultTestLoader.discover(str(checkout / "scripts" / "ci" / "tests"),
                                                top_level_dir=str(checkout / "scripts" / "ci"))
    result = unittest.TextTestRunner(stream=sys.stdout, verbosity=1).run(suite)
    print(f"policy: {len(problems)} matrix problems, {result.testsRun} tests, {len(result.failures)} failures, "
          f"{len(result.errors)} errors", flush=True)
    if problems or not result.wasSuccessful() or result.testsRun == 0:
        raise adapter.AdapterError("the policy checks failed")
