"""Tests of the mod-base Build adapter against the real release matrix and scenario contract."""

from __future__ import annotations

import io
import json
import subprocess
import sys
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "scripts" / "ci"))

import mod_base_build_adapter as adapter  # noqa: E402

SHA = "a" * 40
TREE = "b" * 40


def inputs() -> tuple[dict, dict]:
    return (adapter.parse_inventory((ROOT / "release" / "release-matrix.json").read_bytes()),
            adapter.parse_contract((ROOT / "e2e" / "scenario-contract.json").read_bytes()))


def kit_src() -> Path | None:
    """The pinned kit's sources: staged in the sandbox, or found by the kit's own locator."""

    staged = ROOT / "out" / "mod-base-kit" / "src"
    if staged.is_dir():
        return staged
    found = subprocess.run([sys.executable, str(ROOT / "scripts" / "ci" / "mod_base_kit.py"), "path"],
                           capture_output=True, text=True)
    return Path(found.stdout.strip()) / "src" if found.returncode == 0 and found.stdout.strip() else None


def fake_production(inventory: dict, row: dict, identity: dict) -> bytes:
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, "w") as archive:
        archive.writestr(adapter.BUILD_IDENTITY, json.dumps(identity))
        archive.writestr(adapter.METADATA[row["loader"]], "{}")
        archive.writestr("multiplayerspawners.mixins.json", "{}")
        archive.writestr("com/theplumteam/multiplayerspawners/mixin/BaseSpawnerMixin.class", b"\xca\xfe\xba\xbe")
    return stream.getvalue()


def staged_target(inventory: dict, target: str) -> dict[str, bytes]:
    """What build_target would stage for `target`, with stand-in jars."""

    sources = {path: (ROOT / path).read_bytes() for path in adapter.HARNESS_SOURCES}
    files: dict[str, bytes] = {}
    for row in adapter.lanes_of(inventory, target):
        identity = adapter.identity(inventory, row, "production", SHA, TREE)
        files[adapter.production_path(inventory, row)] = fake_production(inventory, row, identity)
        files[adapter.harness_path(inventory, row)] = adapter.harness_jar(inventory, row, SHA, TREE, sources)
    files[adapter.target_path(target, adapter.BUILD_LOG_NAME)] = b"BUILD SUCCESSFUL\n"
    files[adapter.target_path(target, adapter.MANIFEST_NAME)] = adapter.manifest(target, SHA, TREE, files)
    return files


class PlanTest(unittest.TestCase):

    def test_one_target_per_version_and_one_lane_per_artifact(self) -> None:
        inventory, contract = inputs()
        plan = adapter.derive_plan(inventory, contract)
        self.assertEqual([target["id"] for target in plan["targets"]], adapter.targets(inventory))
        self.assertEqual([lane["id"] for lane in plan["lanes"]],
                         [row["artifact_node"] for row in inventory["artifacts"]])
        self.assertEqual(len(plan["lanes"]), inventory["lane_count"])
        for lane in plan["lanes"]:
            self.assertEqual(lane["obligations"], ["scenario/server-smoke/started", "scenario/server-smoke/mixins",
                                                   "scenario/server-smoke/config"])

    def test_the_plan_is_deterministic(self) -> None:
        inventory, contract = inputs()
        self.assertEqual(adapter.encode(adapter.derive_plan(inventory, contract)),
                         adapter.encode(adapter.derive_plan(*inputs())))

    def test_the_kit_accepts_the_plan(self) -> None:
        source = kit_src()
        if source is None:
            self.skipTest("the pinned kit is not available")
        # A verified kit must never gain bytecode.
        sys.dont_write_bytecode = True
        sys.path.insert(0, str(source))
        from mod_base.build_ci.protocol import validate_plan_units  # noqa: PLC0415

        validate_plan_units(json.loads(adapter.encode(adapter.derive_plan(*inputs()))))

    def test_a_duplicate_key_is_refused(self) -> None:
        with self.assertRaises(adapter.AdapterError):
            adapter.decode(b'{"a": 1, "a": 2}', "test")


class VerifyTargetTest(unittest.TestCase):

    def setUp(self) -> None:
        self.inventory, _ = inputs()
        self.target = "1.21.1"
        self.files = staged_target(self.inventory, self.target)

    def verify(self) -> list[dict]:
        return adapter.verify_target(self.inventory, self.target, tested_sha=SHA, tested_tree=TREE,
                                     read=lambda path: self.files[path])

    def test_a_complete_target_passes(self) -> None:
        self.assertEqual(sorted(record["path"] for record in self.verify()),
                         sorted(output["path"] for output in adapter.target_outputs(self.inventory, self.target)))

    def test_a_changed_jar_fails(self) -> None:
        path = next(path for path in self.files if path.startswith("files/"))
        self.files[path] += b"x"
        with self.assertRaises(adapter.AdapterError):
            self.verify()

    def test_a_jar_of_another_commit_fails(self) -> None:
        row = adapter.lanes_of(self.inventory, self.target)[0]
        identity = adapter.identity(self.inventory, row, "production", "c" * 40, TREE)
        self.files[adapter.production_path(self.inventory, row)] = fake_production(self.inventory, row, identity)
        del self.files[adapter.target_path(self.target, adapter.MANIFEST_NAME)]
        self.files[adapter.target_path(self.target, adapter.MANIFEST_NAME)] = adapter.manifest(
            self.target, SHA, TREE, self.files)
        with self.assertRaises(adapter.AdapterError):
            self.verify()


class VerifyRunTest(unittest.TestCase):

    def setUp(self) -> None:
        self.inventory, contract = inputs()
        self.lane = "neoforge-1.21.1"
        self.row = adapter.runtime_row(self.inventory, self.lane)
        self.obligations = adapter.derive_plan(self.inventory, contract)["lanes"][1]["obligations"]
        self.production = b"production jar"
        self.log = b"[Server thread/INFO]: Done (1.0s)!\n"

    def report(self, problems: dict[str, list[str]]) -> dict[str, bytes]:
        checks = [{"obligation": name, "status": "fail" if problems.get(name) else "pass",
                   "problems": problems.get(name, [])} for name in self.obligations]
        return {adapter.RESULT_NAME: adapter.result_report(self.lane, self.row, SHA, self.production, checks,
                                                           self.log),
                adapter.SERVER_LOG_NAME: self.log}

    def verify(self, files: dict[str, bytes], production: bytes | None = None) -> dict:
        return adapter.verify_run(self.lane, self.obligations, self.row, tested_sha=SHA,
                                  production=self.production if production is None else production,
                                  read=lambda path: files[path])

    def test_a_passing_run_passes(self) -> None:
        self.assertEqual(self.verify(self.report({}))["obligations"], self.obligations)

    def test_a_failed_checkpoint_fails(self) -> None:
        with self.assertRaises(adapter.AdapterError):
            self.verify(self.report({"scenario/server-smoke/mixins": ["mixin audit did not pass"]}))

    def test_a_run_of_another_jar_fails(self) -> None:
        with self.assertRaises(adapter.AdapterError):
            self.verify(self.report({}), production=b"another jar")

    def test_a_changed_log_fails(self) -> None:
        files = self.report({})
        files[adapter.SERVER_LOG_NAME] += b"edited\n"
        with self.assertRaises(adapter.AdapterError):
            self.verify(files)


if __name__ == "__main__":
    unittest.main()
