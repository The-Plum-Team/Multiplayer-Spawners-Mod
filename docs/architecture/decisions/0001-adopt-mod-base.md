# 0001: Adopt mod-base

## Status

Accepted (2026-10).

## Context

MultiplayerSpawners moved to The Plum Team and to one branch that builds every supported
Minecraft version, like Quick Skin and Block Pops. Those mods share their Build and packaged E2E
gates, their public-evidence site and their repository rules through mod-base, a kit pinned at one
commit.

The mod is published under CC0-1.0; mod-base is published under All Rights Reserved terms by The
Plum Team, and adopting it copies a few of its files into this repository.

## Decision

Adopt mod-base at a released tag, in the order of its `docs/NEW-MOD.md`: the kit files first,
then the Build adapter in `disabled` mode, then `shadow`, then the shared gates.

The files mod-base manages here keep the kit's terms and are not part of the CC0 dedication:
`scripts/ci/mod_base_kit.py`, `docs/ai/shared/REPOSITORY.md`, `docs/ai/shared/PUBLIC-EVIDENCE.md`,
`.gitattributes` and the managed region of `.github/workflows/pages.yml`. Everything else,
including the files seeded once from the kit and owned by this repository from then on, is CC0.
Both projects belong to The Plum Team, which settles the question the kit's onboarding raises.

## Consequences

- Kit updates arrive only through `python3 scripts/ci/mod_base_kit.py bump --to vX.Y.Z` pull
  requests; managed files are never edited by hand.
- The Pages workflow stays disabled until the packaged E2E produces public evidence, because
  without it every hourly run would only report that nothing is ready.
- Agent guidance lives in `AGENTS.md` and its imports; there is deliberately no `CLAUDE.md`.
