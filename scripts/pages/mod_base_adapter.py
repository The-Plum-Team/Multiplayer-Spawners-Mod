# mod-base adapter of MultiplayerSpawners: seeded once by `template init --seed`, then owned by this repository.
"""The mod-base Pages adapter: how the kit sees this mod's contract and its multiplayer captures.

* ``release/release-matrix.json`` is the release inventory. Every Minecraft version is one key,
  ``mc<minecraft>``, and every artifact node of that version (one per loader) is one lane.
* ``e2e/scenario-contract.json`` declares, in its ``evidence`` section, the scenario the site
  publishes: its roles (the players Alice, Bob and Carol), their ordered steps with the capture each
  one takes, and the comparisons between two captures of one role.
* The packaged output is what ``scripts/e2e/multiplayer.py`` writes when every checkpoint of a lane
  passes: ``runs/<artifact node>/<scenario>/report.json`` (the lane, its production jar's SHA-256,
  the contract's SHA-256 and each step's status, message and screenshot) and the screenshots
  beside it as ``<role>/<step>.png``.

The protocol is https://github.com/The-Plum-Team/mod-base/blob/main/docs/ADAPTER.md. Every
contract and matrix read goes through ``ctx.read_blob`` (inert Git objects of the subject commit),
and nothing here writes anywhere.
"""

from __future__ import annotations

import hashlib
import json
import os
import subprocess

ADAPTER_API = 1

MATRIX = "release/release-matrix.json"
CONTRACT = "e2e/scenario-contract.json"
GITHUB_PREFIX = "https://github.com/"
MAX_DOCUMENT_BYTES = 1 << 20
MAX_REPORT_BYTES = 1 << 20
SHA1_LENGTH = 40


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _document(ctx, commit: str, path: str) -> tuple[dict, bytes]:
    data = ctx.read_blob(commit, path, MAX_DOCUMENT_BYTES)
    return json.loads(data), data


def _tree(ctx, commit: str) -> str:
    """The tree of ``commit`` from the local object store (a read-only ``git rev-parse``)."""

    environment = {"PATH": "/usr/bin:/bin:/usr/local/bin", "HOME": str(ctx.tmpdir), "LC_ALL": "C",
                   "GIT_CONFIG_NOSYSTEM": "1", "GIT_CONFIG_GLOBAL": os.devnull, "GIT_TERMINAL_PROMPT": "0"}
    completed = subprocess.run(["git", "-C", str(ctx.repo_root), "rev-parse", "--verify", f"{commit}^{{tree}}"],
                               check=True, capture_output=True, env=environment, timeout=60)
    tree = completed.stdout.decode("ascii").strip()
    if len(tree) != SHA1_LENGTH:
        raise ValueError(f"unexpected tree id for {commit}")
    return tree


def _repository(matrix: dict) -> str:
    sources = matrix["project"]["sources"]
    if not isinstance(sources, str) or not sources.startswith(GITHUB_PREFIX):
        raise ValueError("the release matrix names no GitHub source repository")
    repository = sources[len(GITHUB_PREFIX):].rstrip("/")
    if repository.count("/") != 1:
        raise ValueError("the release matrix source URL is not a repository URL")
    return repository


def _versions(matrix: dict) -> list[str]:
    """The matrix's Minecraft versions, in its order."""

    versions: list[str] = []
    for artifact in matrix["artifacts"]:
        if artifact["minecraft"] not in versions:
            versions.append(artifact["minecraft"])
    return versions


def key_of(minecraft: str) -> str:
    return f"mc{minecraft}"


def _evidence_scenarios(contract: dict) -> list[dict]:
    scenarios = contract["evidence"]["scenarios"]
    if not isinstance(scenarios, list) or not scenarios:
        raise ValueError("the scenario contract declares no evidence scenario")
    return scenarios


def targets(ctx, branches):
    if branches is not None:
        raise ValueError("MultiplayerSpawners publishes its default branch only")
    commit = ctx.implementation_sha
    matrix, matrix_bytes = _document(ctx, commit, MATRIX)
    _, contract_bytes = _document(ctx, commit, CONTRACT)
    subject = {"branch": ctx.config.canonical_branch, "commit": commit, "tree": _tree(ctx, commit)}
    prefix = ctx.config.labels["release_prefix"]
    return [{"key": key_of(minecraft), "label": f"{prefix} {minecraft}", "subject": subject,
             "matrix_sha256": _sha256(matrix_bytes), "contract_sha256": _sha256(contract_bytes)}
            for minecraft in _versions(matrix)]


def expectation(ctx, target, tested_run, extensions):
    if extensions:
        raise ValueError("MultiplayerSpawners declares no extensions")
    commit = target["subject"]["commit"]
    matrix, matrix_bytes = _document(ctx, commit, MATRIX)
    contract, contract_bytes = _document(ctx, commit, CONTRACT)
    if _sha256(matrix_bytes) != target["matrix_sha256"] or _sha256(contract_bytes) != target["contract_sha256"]:
        raise ValueError("the release matrix or the scenario contract changed since targets")
    rows = [row for row in matrix["artifacts"] if key_of(row["minecraft"]) == target["key"]]
    if not rows:
        raise ValueError(f"the release matrix has no lane for {target['key']}")
    scenarios = _evidence_scenarios(contract)
    lanes, captures, comparisons = [], [], []
    for row in rows:
        node = row["artifact_node"]
        for scenario in scenarios:
            lane_id = f"{node}/{scenario['id']}"
            lanes.append({"lane_id": lane_id, "artifact_node": node, "minecraft": row["minecraft"],
                          "loader": row["loader"], "java": row["java"], "scenario": scenario["id"],
                          "roles": [role["role"] for role in scenario["roles"]]})
            for role in scenario["roles"]:
                order = 0
                for step in role["steps"]:
                    if "capture" not in step:
                        continue
                    capture = step["capture"]
                    captures.append({"frame_id": f"{lane_id}/{role['role']}/{step['id']}",
                                     "capture_id": f"{scenario['id']}.{role['role']}.{step['id']}",
                                     "capture_order": order, "lane_id": lane_id, "role": role["role"],
                                     "step": step["id"], "title": capture["title"],
                                     "expectation": capture["expectation"], "review_tier": capture["review_tier"]})
                    order += 1
                for comparison in role["comparisons"]:
                    comparisons.append({
                        "comparison_id": f"{lane_id}/{role['role']}/{comparison['id']}", "lane_id": lane_id,
                        "role": role["role"], "first_frame_id": f"{lane_id}/{role['role']}/{comparison['first']}",
                        "second_frame_id": f"{lane_id}/{role['role']}/{comparison['second']}",
                        "minimum_changed_fraction": comparison["minimum_changed_fraction"]})
    return {
        "kind": "mod-base.evidence.expectation", "schema_version": 1, "repository": _repository(matrix),
        "key": target["key"], "label": target["label"], "subject": target["subject"],
        "matrix_sha256": target["matrix_sha256"], "contract_sha256": target["contract_sha256"],
        "contract_path": CONTRACT, "profile": contract["evidence"]["profile"], "scope": {"kind": "complete"},
        "image_policy": ctx.config.image_policy(),
        "scenarios": [{"id": scenario["id"], "title": scenario["title"]} for scenario in scenarios],
        "lanes": lanes, "captures": captures, "comparisons": comparisons, "anchor": None,
    }


def run_path(lane: dict) -> str:
    return f"runs/{lane['artifact_node']}/{lane['scenario']}"


def collect(ctx, runtime_root, target, expectation):
    tree = ctx.runtime_tree(runtime_root)
    files, lanes, reports = [], [], {}
    for lane in expectation["lanes"]:
        base = run_path(lane)
        report = tree.read_json(f"{base}/report.json", max_bytes=MAX_REPORT_BYTES)
        if (report.get("kind") != "multiplayerspawners.e2e.report" or report.get("schema_version") != 1
                or report.get("status") != "pass" or report.get("artifact_node") != lane["artifact_node"]
                or report.get("minecraft") != lane["minecraft"] or report.get("loader") != lane["loader"]
                or report.get("java") != lane["java"] or report.get("scenario") != lane["scenario"]
                or report.get("contract_sha256") != expectation["contract_sha256"]):
            raise ValueError(f"the report of {lane['lane_id']} is not a passing run of this lane and contract")
        files.append(f"{base}/report.json")
        reports[lane["lane_id"]] = report
        lanes.append({"lane_id": lane["lane_id"], "java": lane["java"], "profile": expectation["profile"],
                      "status": "pass", "elapsed_s": report["elapsed_s"],
                      "jars": {"production_sha256": report["production_sha256"]}})
    frames = []
    for capture in expectation["captures"]:
        lane = next(item for item in expectation["lanes"] if item["lane_id"] == capture["lane_id"])
        steps = [item for item in reports[capture["lane_id"]]["roles"][capture["role"]]["steps"]
                 if item["id"] == capture["step"]]
        if len(steps) != 1:
            raise ValueError(f"the report of {capture['lane_id']} has {len(steps)} records of {capture['frame_id']}")
        step = steps[0]
        if step["status"] != "pass" or step["screenshot"] != f"{capture['role']}/{capture['step']}.png":
            raise ValueError(f"the report step of {capture['frame_id']} did not pass or names another screenshot")
        source = f"{run_path(lane)}/{step['screenshot']}"
        if not tree.exists(source):
            raise ValueError(f"missing screenshot {source}")
        files.append(source)
        frames.append({"frame_id": capture["frame_id"], "source_path": source, "runtime_evidence": step["message"]})
    comparisons = [{"comparison_id": comparison["comparison_id"]} for comparison in expectation["comparisons"]]
    return {"runtime_files": sorted(set(files)), "lanes": lanes, "frames": frames, "comparisons": comparisons}
