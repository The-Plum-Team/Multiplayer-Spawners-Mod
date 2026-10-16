# MultiplayerSpawners

Per-player spawners for multiplayer servers. When a player breaks a spawner it is disabled **for
that player only**: it stops spawning while they are nearby and they see it go dark, while it
stays active for everyone else, so every player gets to clear the same dungeon.

- **Group disabling:** players near the one who disabled a spawner get it disabled too.
- **Breaking:** a disabled spawner can be broken through a clickable chat message (or always in
  Creative), after one or two hits.
- **Torches:** a torch placed within 7 blocks of a spawner is put out after a delay.
- **Player-placed spawners** can behave like vanilla ones.

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
| `<world>/serverconfig/multiplayerspawners-server.toml` | group disabling and its radius, torch delay, break attempts, break message, vanilla player-placed spawners |
| `config/multiplayerspawners-client.toml` | debug logging |

Operators (level 2) can change the server settings in game; the change is saved to the file:

```
/multiplayerspawners toggle_placed_vanilla
/multiplayerspawners toggle_break_message
/multiplayerspawners set_break_attempts <1-10>
/multiplayerspawners torch delay [<seconds>]
/multiplayerspawners torch info
```

## Building

The build uses [Stonecutter](https://stonecutter.kikugie.dev/) and Architectury Loom. One Gradle
run builds one Minecraft version, chosen with `-PmpsTarget` (default: the matrix's
`default_target`):

```bash
./gradlew -PmpsTarget=1.21.1 buildTarget          # the jars of one version
python3 scripts/release/build_matrix.py           # every version, one Gradle run each
python3 scripts/e2e/server_smoke.py --minecraft 1.21.1   # start a real server with those jars
```

Jars land in `<loader>/versions/<minecraft>/build/libs/`. Gradle finds the Java 17, 21 and 25
toolchains it needs on the machine.

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
`python3 scripts/release/matrix.py` to validate it, then build and smoke-test that version.

## License

[CC0-1.0](LICENSE)
