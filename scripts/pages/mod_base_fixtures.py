"""Test fixtures of the mod-base Pages adapter; never loaded by the Pages host.

``synthesize`` is the hook ``mod_base conformance`` calls in place of a real multiplayer run: it
writes the packaged-output tree that ``scripts/e2e/multiplayer.py`` writes and the adapter's
``collect`` reads (``runs/<artifact node>/<scenario>/report.json`` plus ``<role>/<step>.png``),
with the kit's deterministic generated images in place of the players' screenshots.
"""

from __future__ import annotations

import hashlib
import json
from pathlib import Path

CONTRACT = "e2e/scenario-contract.json"
MAX_CONTRACT_BYTES = 1 << 20


def synthesize(ctx, target, expectation, out_root, image_factory):
    contract = json.loads(ctx.read_blob(target["subject"]["commit"], CONTRACT, MAX_CONTRACT_BYTES))
    scenarios = {scenario["id"]: scenario for scenario in contract["evidence"]["scenarios"]}
    width, height = expectation["image_policy"]["source_size"]
    root = Path(out_root)
    for index, lane in enumerate(expectation["lanes"]):
        base = root / "runs" / lane["artifact_node"] / lane["scenario"]
        roles, seed = {}, 100 * index
        for role in scenarios[lane["scenario"]]["roles"]:
            (base / role["role"]).mkdir(parents=True, exist_ok=True)
            steps = []
            for step in role["steps"]:
                screenshot = f"{role['role']}/{step['id']}.png"
                (base / screenshot).write_bytes(image_factory(width, height, seed))
                seed += 1
                steps.append({"id": step["id"], "status": "pass", "screenshot": screenshot,
                              "message": f"{role['role']} passed {step['id']} on {lane['artifact_node']} (generated fixture)"})
            roles[role["role"]] = {"steps": steps}
        report = {
            "kind": "multiplayerspawners.e2e.report", "schema_version": 1, "status": "pass",
            "artifact_node": lane["artifact_node"], "minecraft": lane["minecraft"], "loader": lane["loader"],
            "java": lane["java"], "scenario": lane["scenario"], "contract_sha256": expectation["contract_sha256"],
            "production_sha256": hashlib.sha256(f"fixture jar {lane['artifact_node']}".encode()).hexdigest(),
            "elapsed_s": 100 + index, "roles": roles,
        }
        (base / "report.json").write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
