# Project and release contract

This file is part of the repository-wide instruction set imported by `AGENTS.md`. Together, the
documents listed there are the source of truth for coding agents working anywhere below the
repository root. The two shared documents imported first (`docs/ai/shared/REPOSITORY.md` and
`docs/ai/shared/PUBLIC-EVIDENCE.md`) are managed by mod-base and hold the rules every mod shares;
this file and the other local imports hold only what is specific to MultiplayerSpawners.

## Documentation map

- `AGENTS.md` is the import-only manifest for the complete coding-agent instruction set. Its first
  two lines are the managed shared documents; the rest are listed in `site/mod-base.json`
  `template.agents_local`.
- `CONTRIBUTING.md` is the human-facing path from a fresh fork to a reviewed pull request.
- `README.md` is for users and builders.
- `docs/architecture/decisions/` records evidence-backed architectural decisions.
- `site/mod-base.json` and `scripts/pages/mod_base_adapter.py` connect this repository to the
  mod-base public-evidence kit; `scripts/ci/mod_base_kit.py` finds the pinned kit.

## What MultiplayerSpawners is

Per-player spawners for multiplayer servers: breaking a spawner disables it for that player
(and, with group disabling, nearby players) while it stays active for everyone else. It also puts
out torches placed near spawners and can treat player-placed spawners as vanilla ones.

`release/release-matrix.json` is the single authoritative inventory of supported Minecraft
versions, loaders, Java versions and dependency versions: 1.20.1 on Fabric and Forge, 1.21.1 to
26.3 on Fabric and NeoForge (34 lanes). It is validated by `scripts/release/matrix.py`. Never
duplicate that inventory in Gradle, scripts, workflows or documentation, and never infer support
from directory or branch names: the README shows a summary for users only.

## Source layout

- `common/`, `fabric/`, `forge/`, `neoforge/`: one Stonecutter source tree per module.
  Stonecutter generates each version's sources into `<module>/versions/<minecraft>/build/`, which
  is generated output and never edited or committed.
- Code that differs between Minecraft versions is a Stonecutter condition in the shared tree:

  ```java
  //? if >=1.21.2 {
  properties = properties.setId(ResourceKey.create(Registries.BLOCK, Constants.id(name)));
  //?} else {
  /*properties = ...;
  *///?}
  ```

  Keep conditions as narrow as the change: wrap the one call or signature, not a whole class.
  Prefer an API that exists in every version (for example `sendSystemMessage` over
  `displayClientMessage`) to a condition.
- Pure renames with no behaviour change are rewritten as the sources are generated, in
  `gradle/lane-conventions.gradle.kts` (`sourceRenames`), instead of a condition around every use.
- Resource format changes (`loot_tables/` to `loot_table/` from 1.21, item definitions from
  1.21.4, `forge_data` to `neoforge_data`) are handled in `common/build.gradle.kts`.
- Loader code only moves data: networking transport, events and registration. What to sync, when
  and in which batches is decided in `common` (`NetworkHandler`).
- The config is the mod's own reader and writer (`common/.../config/ModConfig.java`) of the files
  it has always used: `<world>/serverconfig/multiplayerspawners-server.toml` and
  `config/multiplayerspawners-client.toml`. Keep their keys and layout compatible.

## Task routing

| Change scope | Start from | Expected destination |
|---|---|---|
| Behaviour shared by every loader and version | `main` | `common/src/main` |
| One loader | `main` | `<loader>/src/main` |
| One Minecraft version or range | `main` | A Stonecutter condition or a rename rule in the shared tree |
| Supported version/loader inventory | `release/release-matrix.json` | A lane that builds and passes the server and multiplayer tests |
| Generated output or staged artifacts | Nowhere | Fix the tracked input instead |

Adding a Minecraft version: add its lanes to the matrix (dependency versions included), validate
it, build that version, then smoke-test it. A mixin target is a string that javac does not check,
so a version is not supported until `scripts/e2e/server_smoke.py` and `scripts/e2e/multiplayer.py`
pass for each of its lanes.

## Verification

From the smallest check to the aggregate gate:

```bash
git diff --check
python3 scripts/ci/mod_base_kit.py verify --network
python3 scripts/ci/mod_base_kit.py run template check --repo .
python3 scripts/release/matrix.py
./gradlew -PmpsTarget=<minecraft> buildTarget
python3 scripts/e2e/server_smoke.py --minecraft <minecraft>
python3 scripts/e2e/multiplayer.py --minecraft <minecraft>
python3 scripts/release/build_matrix.py && python3 scripts/e2e/server_smoke.py --all
python3 scripts/e2e/multiplayer.py --all
```

The server smoke test starts each lane's real dedicated server with the production jar and makes
Mixin apply every mixin at start (`-Dmultiplayerspawners.mixinAudit=true`). Client-only mixins do
not load on a server; check their targets against every version when changing them.

The multiplayer test (`scripts/e2e/multiplayer.py`) is where the client side is proven: it runs the
lane's real dedicated server and three real clients of the same loader on virtual displays, drives
the players with real input (xdotool) and the world over RCON, and checks the server's data, the
chat and each player's screen. Its docstring lists the checkpoints in order. When a change alters
what a player sees or is told, update the text or visual expectation in that script with it. It
needs Xvfb, xdotool and `e2e/requirements.txt` (installed with `--require-hashes`); CI runs it in
every version job and keeps the screenshots for seven days.

## Project site (mod-base Pages)

The project site at https://the-plum-team.github.io/Multiplayer-Spawners-Mod/ publishes the
multiplayer test's captures through the pinned kit.

- The `evidence` section of `e2e/scenario-contract.json` declares what is published: the scenario,
  its roles (the players), their ordered steps with each capture's title, expectation and review
  tier, and the comparisons between two captures of one role. The Build gate ignores it.
- `scripts/e2e/multiplayer.py` records each declared capture only from a check that passed, with
  that check's message, and writes `runs/<node>/multiplayer/report.json` and `<role>/<step>.png`
  below `build/multiplayer/evidence` when the whole lane passes (its `evidence` checkpoint).
- `scripts/pages/mod_base_adapter.py` maps them for the kit: one key per Minecraft version
  (`mc<minecraft>`), one lane per artifact node. `scripts/pages/mod_base_fixtures.py` synthesizes
  the same tree for the kit's conformance simulation.
- `.github/workflows/ci.yml` is the source workflow: on `main` its `Prepare public evidence for
  mc<minecraft>` jobs run the kit's `prepare-evidence` and `notify-pages` wakes `pages.yml`, which
  admits, re-verifies and deploys. Disabling `pages.yml` is the kill switch.

Adding, renaming or removing a capture is one change of the contract and of the
`self.capture`/`expect_view(capture=...)` call in `multiplayer.py` that takes it. Check the adapter
with the kit's simulation (it needs Pillow 12.3, as in `e2e/requirements.txt`):

```bash
python3 scripts/ci/mod_base_kit.py run conformance --repo . --keys mc1.20.1,mc26.3
```

## mod-base Build adapter

`scripts/ci/mod-base-build.json` connects this mod to mod-base's shared Build and packaged E2E
gates; `site/mod-base-build-activation.json` holds the mode (`disabled` until the gate
environment exists, then `shadow`, then `shared-build-and-e2e`).

- `scripts/ci/mod_base_build_adapter.py` is everything the hooks know about the mod: one target
  per Minecraft version, one lane per artifact node, and the obligations of
  `e2e/scenario-contract.json` (the server smoke test's checkpoints).
- `scripts/ci/mod_base_build_dispatch.py` runs a hook. `build_target` builds a copy of the tracked
  tree below `$TMPDIR`, because a candidate hook must leave its checkout untouched, and passes
  `-PmpsBuildIdentity` so that each jar embeds the commit it was built from. `run_lane` runs the
  server smoke test against the sealed jar.
- After changing any adapter file run `python3 scripts/ci/update_build_config.py`: the kit runs
  protected hooks only from files whose SHA-256 the config lists.
- `python3 scripts/ci/local_build_run.py <minecraft>` runs every hook of one target locally, in the
  kit's directory layout, and checks that candidate hooks leave the checkout untouched.
- `scripts/ci/tests` holds the adapter's tests; the `policy` hook runs them with the matrix
  validator.

