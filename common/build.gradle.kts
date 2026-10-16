import dev.architectury.plugin.ArchitectPluginExtension
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    java
}

val minecraftVersion = stonecutter.current.version
@Suppress("UNCHECKED_CAST")
val lanes = gradle.extensions.extraProperties["mpsTargetArtifacts"] as List<Map<String, Any?>>
val noRemap = lanes.map { it["no_remap"] as Boolean }.distinct().single()

// The common module compiles against vanilla through Loom's Fabric platform.
extra["loom.platform"] = "fabric"
apply(plugin = if (noRemap) "dev.architectury.loom-no-remap" else "dev.architectury.loom")
apply(plugin = "architectury-plugin")
apply(from = rootProject.file("gradle/lane-conventions.gradle.kts"))

@Suppress("UNCHECKED_CAST")
val atLeast = extra["mpsAtLeast"] as (String) -> Boolean
val modImplementation = extra["mpsModImplementation"].toString()

extensions.configure<BasePluginExtension> {
    archivesName.set("MultiplayerSpawners-common-$minecraftVersion")
}

extensions.configure<ArchitectPluginExtension> {
    minecraft = minecraftVersion
    common(lanes.map { it["loader"].toString() })
}

val loom = extensions.getByType<LoomGradleExtensionAPI>()
dependencies {
    "minecraft"("net.minecraft:minecraft:$minecraftVersion")
    if (!noRemap) {
        "mappings"(loom.officialMojangMappings())
    }
    // Fabric Loader brings Mixin and the @Environment annotations; nothing else from it is used.
    val fabricLane = lanes.single { it["loader"] == "fabric" }
    modImplementation("net.fabricmc:fabric-loader:${fabricLane["loader_version"]}")
}

// Stonecutter preprocesses src/main/java into this directory for the current version.
val generatedJava = layout.buildDirectory.dir("generated/stonecutter/main/java")
sourceSets.named("main") {
    java.setSrcDirs(listOf(generatedJava))
    resources.setSrcDirs(listOf(rootProject.file("common/src/main/resources")))
}
tasks.named("compileJava") { dependsOn("stonecutterGenerate") }
tasks.matching { it.name == "sourcesJar" }.configureEach { dependsOn("stonecutterGenerate") }

// Resource formats that changed with Minecraft. The authored tree keeps the 1.20.1 layout.
val itemDefinitions = layout.buildDirectory.dir("generated/item-definitions")
val generateItemDefinitions = tasks.register("generateItemDefinitions") {
    val output = itemDefinitions
    inputs.property("minecraft", minecraftVersion)
    outputs.dir(output)
    doLast {
        val dir = output.get().asFile.resolve("assets/multiplayerspawners/items")
        dir.mkdirs()
        // 1.21.4 resolves an item's model through an item definition instead of by its name.
        dir.resolve("unlit_torch.json").writeText(
            "{\n  \"model\": {\n    \"type\": \"minecraft:model\",\n" +
                "    \"model\": \"multiplayerspawners:item/unlit_torch\"\n  }\n}\n"
        )
    }
}

tasks.named<ProcessResources>("processResources") {
    inputs.property("minecraft", minecraftVersion)
    // 1.21 renamed the loot_tables directory to loot_table.
    if (atLeast("1.21")) {
        eachFile { path = path.replace("/loot_tables/", "/loot_table/") }
        includeEmptyDirs = false
    }
    // NeoForge (from 1.20.5) reads per-element lighting from neoforge_data; Forge from forge_data.
    if (atLeast("1.20.5")) {
        filesMatching("assets/multiplayerspawners/models/block/spawner_activated.json") {
            filter { line -> line.replace("\"forge_data\"", "\"neoforge_data\"") }
        }
    }
    if (atLeast("1.21.4")) {
        dependsOn(generateItemDefinitions)
        from(itemDefinitions)
    }
}
