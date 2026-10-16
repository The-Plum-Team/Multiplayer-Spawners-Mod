#!/usr/bin/env python3
"""Rewrite scripts/ci/mod-base-build.json with the current SHA-256 of every adapter file.

The kit runs protected hooks from a copy that holds exactly the listed files at exactly these
hashes, so run this after changing any of them (and check the result in with them).
"""

from __future__ import annotations

import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CONFIG = ROOT / "scripts" / "ci" / "mod-base-build.json"
FILES = ("scripts/ci/mod_base_build_adapter.py", "scripts/ci/mod_base_build_dispatch.py",
         "scripts/ci/policy_suite.py")


def main() -> None:
    config = json.loads(CONFIG.read_text(encoding="utf-8"))
    config["adapter"]["files"] = [{"path": path, "sha256": hashlib.sha256((ROOT / path).read_bytes()).hexdigest()}
                                  for path in FILES]
    CONFIG.write_text(json.dumps(config, indent=2, sort_keys=True) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
