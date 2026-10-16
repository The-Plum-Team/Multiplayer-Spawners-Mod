import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import dev.architectury.plugin.ArchitectPluginExtension
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import net.fabricmc.loom.task.RemapJarTask
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    java
}

val minecraftVersion = stonecutter.current.version
@Suppress("UNCHECKED_CAST")
val lanes = gradle.extensions.extraProperties["mpsTargetArtifacts"] as List<Map<String, Any?>>
val lane = lanes.single { it["loader"] == "forge" }
val noRemap = lane["no_remap"] as Boolean
val commonPath = ":common:$minecraftVersion"
evaluationDependsOn(commonPath)
val commonProject = project(commonPath)
val commonJar = commonProject.tasks.named("jar")

extra["loom.platform"] = "forge"
apply(plugin = if (noRemap) "dev.architectury.loom-no-remap" else "dev.architectury.loom")
apply(plugin = "architectury-plugin")
apply(plugin = "com.gradleup.shadow")
apply(from = rootProject.file("gradle/lane-conventions.gradle.kts"))
apply(from = rootProject.file("gradle/loader-conventions.gradle.kts"))

extensions.configure<ArchitectPluginExtension> {
    minecraft = minecraftVersion
    platformSetupLoomIde()
    forge()
}

repositories {
    maven("https://maven.minecraftforge.net/")
}

val loom = extensions.getByType<LoomGradleExtensionAPI>()
loom.forge.mixinConfig("multiplayerspawners.mixins.json")
val common: Configuration = configurations.create("common") {
    isCanBeResolved = true
    isCanBeConsumed = false
}
val shadowBundle: Configuration = configurations.create("shadowBundle") {
    isCanBeResolved = true
    isCanBeConsumed = false
}
configurations.named("compileClasspath") { extendsFrom(common) }
configurations.named("runtimeClasspath") { extendsFrom(common) }
configurations.named("developmentForge") { extendsFrom(common) }

dependencies {
    "minecraft"("net.minecraft:minecraft:$minecraftVersion")
    if (!noRemap) {
        "mappings"(loom.officialMojangMappings())
    }
    "forge"("net.minecraftforge:forge:${lane["loader_version"]}")

    // Loom publishes a namedElements variant only when it remaps; an unobfuscated lane takes the
    // common jar as it is.
    if (noRemap) {
        common(project.files(commonJar))
    } else {
        common(project(path = commonPath, configuration = "namedElements")) { isTransitive = false }
    }
    shadowBundle(project.files(commonProject.tasks.named("transformProductionForge")))
}

tasks.named<ProcessResources>("processResources") {
    @Suppress("UNCHECKED_CAST")
    val metadata = lane["metadata"] as Map<String, Any?>
    @Suppress("UNCHECKED_CAST")
    val expansions = (project.extra["mpsMetadataExpansions"] as Map<String, String>) + mapOf(
        "minecraft_requirement" to metadata["minecraft"].toString(),
        "loader_requirement" to metadata["loader"].toString(),
        "loader_api_requirement" to metadata["loader_api"].toString(),
    )
    inputs.properties(expansions)
    filesMatching("META-INF/mods.toml") { expand(expansions) }
}

tasks.named<ShadowJar>("shadowJar") {
    configurations = listOf(shadowBundle)
    // Without remapping the shadow jar is itself the release jar.
    archiveClassifier.set(if (noRemap) "" else "dev-shadow")
}
if (noRemap) {
    tasks.named<Jar>("jar") { archiveClassifier.set("dev") }
} else {
    tasks.named<RemapJarTask>("remapJar") {
        inputFile.set(tasks.named<ShadowJar>("shadowJar").flatMap { it.archiveFile })
    }
}
