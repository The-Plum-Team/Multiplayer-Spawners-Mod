# MultiplayerSpawners

Per-player spawners for multiplayer servers. When a player breaks a spawner it is disabled **for
that player only**: it stops spawning while they are nearby and they see it go dark, while it
stays active for everyone else, so every player gets to clear the same dungeon.

- **Group disabling:** players near the one who hits a spawner get it disabled too, on every hit.
- **Breaking:** a disabled spawner can be broken through a clickable chat message (or always in
  Creative), after one or two hits.
- **Torches:** a torch placed within 7 blocks of a spawner is put out after a delay.
- **Respawn:** optionally, a disabled spawner becomes active again for its player after a set
  number of minutes (off by default), like Lootr's loot refresh.
- **Player-placed spawners** can behave like vanilla ones: they spawn for everyone, break normally
  and put out no torches, and show a dark steel look instead of the golden one.
- **Looks:** a spawner shows the mod's golden look while it spawns for you, and the vanilla look
  when it is empty or disabled for you.

**Works with** [Apothic Spawners](https://modrinth.com/mod/apothic-spawners) (its spawners are
disabled per player like vanilla ones, within their own activation range) and
[Ageing Spawners](https://modrinth.com/mod/ageing-spawners): a spawner that runs out is disabled for
the players who used it up and stays for everyone else, instead of disappearing for the whole server.

**Mods that break blocks for a player** on Forge and NeoForge, such as the 3x3 hammers of
[Tinkers' Construct](https://modrinth.com/mod/tinkers-construct) or the Break spell of
[Ars Nouveau](https://modrinth.com/mod/ars-nouveau), disable a spawner for that player like a hit,
instead of breaking it. A machine that breaks blocks for nobody leaves spawners as they are.

**Explosions** do not break spawners either: TNT a player lit (or a fireball they sent back)
disables a spawner for that player like a hit, and any other explosion, such as a creeper's, leaves
it as it is. Player-placed spawners treated as vanilla still blow up.

**Single-use spawners** — [Quark](https://modrinth.com/mod/quark)'s monster box and
[Apotheosis](https://modrinth.com/mod/apotheosis)' boss spawner — spawn their mobs or boss once and
then remove themselves. They now stay: used up only for the players near them when they trigger
(and for a player who mines one), they trigger again for the next players and keep who used them
across restarts. The block tag `multiplayerspawners:one_shot_spawners` lists them; a datapack can add
other blocks that work the same way, and `oneShotSpawnerRange` sets how near counts.

The [project site](https://the-plum-team.github.io/Multiplayer-Spawners-Mod/) shows what three real
players saw while testing every supported Minecraft version and loader on a dedicated server.

## Versions

One jar per loader and Minecraft version, all built from the `main` branch:

| Minecraft | Loaders | Java |
|---|---|---|
| 1.20.1 | Fabric, Forge | 17 |
| 1.21.1 – 1.21.11 | Fabric, NeoForge | 21 |
| 26.1 – 26.3 | Fabric, NeoForge | 25 |

The exact list, with every dependency version, is [`release/release-matrix.json`](release/release-matrix.json).
On Fabric the mod needs Fabric API.

## Configuration

| File | Settings |
|---|---|
| `<world>/serverconfig/multiplayerspawners-server.toml` | group disabling and its radius, respawn time, chat notices, single-use spawner range, torch delay, break attempts, break message, breaking with spawner tools, spawn eggs on spawners, vanilla player-placed spawners |
| `config/multiplayerspawners-client.toml` | debug logging |

Operators (level 2) can change the server settings in game; the change is saved to the file:

```
/multiplayerspawners toggle_placed_vanilla
/multiplayerspawners toggle_break_message
/multiplayerspawners toggle_break_tools
/multiplayerspawners toggle_spawn_eggs
/multiplayerspawners toggle_notices
/multiplayerspawners set_break_attempts <1-10>
/multiplayerspawners respawn [<minutes>]
/multiplayerspawners torch delay [<seconds>]
/multiplayerspawners torch info
```

**Breaking:** by default a disabled spawner can be broken through the chat link. With
`allowSpawnerBreakMessage = false` only Creative players can break spawners. With
`breakWithSpawnerTools = true`, an item of the item tag `multiplayerspawners:breaks_spawners`
breaks a spawner like vanilla, for everyone, and its mod decides what drops: add your mods'
spawner-moving tools to that tag with a datapack. Both together make spawners breakable only with
those tools.

**Spawn eggs:** a spawner is one block for the whole server, so changing its mob with a spawn egg
changes it for every player. With `spawnEggsChangeSpawners = false` only Creative players can do
it.

## Building

The build uses [Stonecutter](https://stonecutter.kikugie.dev/) and Architectury Loom. One Gradle
run builds one Minecraft version, chosen with `-PmpsTarget` (default: the matrix's
`default_target`):

```bash
./gradlew -PmpsTarget=1.21.1 buildTarget          # the jars of one version
python3 scripts/release/build_matrix.py           # every version, one Gradle run each
python3 scripts/e2e/server_smoke.py --minecraft 1.21.1   # start a real server with those jars
python3 scripts/e2e/multiplayer.py --minecraft 1.21.1    # play them with three real clients
```

Jars land in `<loader>/versions/<minecraft>/build/libs/`. Gradle finds the Java 17, 21 and 25
toolchains it needs on the machine.

### Testing

CI runs two tests on every lane (34 of them, see the table above) with the production jars:

- `scripts/e2e/server_smoke.py` starts the loader's real dedicated server, makes every mixin
  apply and checks that the config file is written.
- `scripts/e2e/multiplayer.py` plays the mod. It starts the dedicated server and three real
  Minecraft clients (Alice, Bob and Carol) of the same loader, each on its own virtual display,
  that join it. The players act with real mouse and keyboard input (xdotool), and the test checks
  what the server stores and what each player sees: a hit disables a spawner for the player and
  the players near them but not for the others, the spawner stops spawning only while nobody it is
  active for is near, each player sees it their own way (also after a restart and a rejoin), the
  chat link breaks it, a torch near it goes out, and a spawner a player places stays vanilla. The
  script's docstring lists every checkpoint; the screenshots of what each player saw are kept with
  the CI run. On `main`, CI also hands the captures to the
[mod-base](https://github.com/The-Plum-Team/mod-base) kit, which publishes them on the project site.

The multiplayer test needs Xvfb, xdotool and the hash-locked packages of `e2e/requirements.txt`:

```bash
sudo apt-get install xvfb xdotool
python3 -m pip install --only-binary=:all: --require-hashes -r e2e/requirements.txt
python3 scripts/e2e/multiplayer.py fabric-1.21.1       # one lane; --minecraft or --all for more
```

### Version-specific code

There is one source tree per module (`common`, `fabric`, `forge`, `neoforge`). Code that differs
between Minecraft versions is written with Stonecutter conditions, and Stonecutter generates each
version's sources from it:

```java
//? if >=1.21.2 {
properties = properties.setId(ResourceKey.create(Registries.BLOCK, Constants.id(name)));
//?}
```

Pure renames (for example `ResourceLocation` → `Identifier` in 1.21.11) are not written as
conditions: [`gradle/lane-conventions.gradle.kts`](gradle/lane-conventions.gradle.kts) rewrites
them while the sources are generated. Resource format changes (`loot_tables` → `loot_table`, item
definitions from 1.21.4) are handled in [`common/build.gradle.kts`](common/build.gradle.kts).

To add a Minecraft version, add its lanes to `release/release-matrix.json`, run
`python3 scripts/release/matrix.py` to validate it, then build that version and run both tests on it.

## License

[CC0-1.0](LICENSE), except the few files managed by The Plum Team's mod-base kit, which keep the
kit's terms: see [ADR 0001](docs/architecture/decisions/0001-adopt-mod-base.md).
