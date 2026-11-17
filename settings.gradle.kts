pluginManagement {
    repositories {
        // The Plum Team's hash-checked mirror, on the team's own host, is searched first for
        // everything but what it never holds: Mojang's and Minecraft's files and the namespaces
        // Loom generates. A 404 falls through to the upstream repositories below.
        // -Pplum.mavenMirror=false is the emergency switch that leaves it out, as in
        // gradle/maven-mirror.gradle.kts.
        val plumMirrorSwitch = providers.gradleProperty("plum.mavenMirror").orNull
        require(plumMirrorSwitch == null || plumMirrorSwitch == "true" || plumMirrorSwitch == "false") {
            "plum.mavenMirror must be true or false, not '$plumMirrorSwitch'"
        }
        if (plumMirrorSwitch != "false") {
            maven("https://maven.quickskin.net/") {
                name = "PlumMavenMirror"
                // The mirror holds exactly the files in the verification metadata: a module's
                // .module file, without the .pom that only points to it.
                metadataSources { gradleMetadata(); mavenPom() }
                mavenContent { releasesOnly() }
                content {
                    excludeGroupByRegex("com\\.mojang(\\..*)?")
                    excludeGroupByRegex("de\\.oceanlabs\\.mcp(\\..*)?")
                    excludeGroup("loom")
                    excludeGroup("net.minecraft")
                    excludeGroupByRegex("net\\.minecraftforge\\.[0-9a-f]{64}")
                    excludeGroupByRegex("net\\.neoforged\\.fancymodloader\\.[0-9a-f]{64}")
                    excludeGroupByRegex("remapped\\..+")
                }
            }
        }
        maven("https://maven.fabricmc.net/") {
            name = "FabricPluginRepository"
            mavenContent { releasesOnly() }
            content {
                includeGroupByRegex("net\\.fabricmc(\\..*)?")
            }
        }
        maven("https://maven.architectury.dev/") {
            name = "ArchitecturyPluginRepository"
            mavenContent { releasesOnly() }
            content {
                includeGroupByRegex("dev\\.architectury(\\..*)?")
                includeGroup("architectury-plugin")
                includeGroup("com.github.architectury")
            }
        }
        maven("https://libraries.minecraft.net/") {
            name = "MojangPluginRepository"
            mavenContent { releasesOnly() }
            content {
                includeGroupByRegex("com\\.mojang(\\..*)?")
            }
        }
        maven("https://maven.minecraftforge.net/") {
            name = "ForgePluginRepository"
            mavenContent { releasesOnly() }
            content {
                includeGroupByRegex("net\\.minecraftforge(\\..*)?")
                includeGroupByRegex("de\\.oceanlabs\\.mcp(\\..*)?")
                includeGroupByRegex("org\\.spongepowered(\\..*)?")
                excludeGroupByRegex("net\\.minecraftforge\\.[0-9a-f]{64}")
            }
        }
        maven("https://maven.neoforged.net/releases") {
            name = "NeoForgePluginRepository"
            mavenContent { releasesOnly() }
            content {
                includeGroupByRegex("net\\.neoforged(\\..*)?")
            }
        }
        maven("https://maven.kikugie.dev/releases") {
            name = "KikugiePluginRepository"
            mavenContent { releasesOnly() }
            content {
                includeGroupByRegex("dev\\.kikugie(\\..*)?")
            }
        }
        mavenCentral {
            content {
                excludeGroupByRegex("net\\.fabricmc(\\..*)?")
                excludeGroupByRegex("dev\\.architectury(\\..*)?")
                excludeGroup("architectury-plugin")
                excludeGroup("com.github.architectury")
                excludeGroupByRegex("com\\.mojang(\\..*)?")
                excludeGroupByRegex("net\\.minecraftforge(\\..*)?")
                excludeGroupByRegex("de\\.oceanlabs\\.mcp(\\..*)?")
                excludeGroupByRegex("org\\.spongepowered(\\..*)?")
                excludeGroupByRegex("net\\.neoforged(\\..*)?")
                excludeGroupByRegex("dev\\.kikugie(\\..*)?")
            }
        }
        gradlePluginPortal {
            content {
                excludeGroupByRegex("net\\.fabricmc(\\..*)?")
                excludeGroupByRegex("dev\\.architectury(\\..*)?")
                excludeGroup("architectury-plugin")
                excludeGroup("com.github.architectury")
                excludeGroupByRegex("com\\.mojang(\\..*)?")
                excludeGroupByRegex("net\\.minecraftforge(\\..*)?")
                excludeGroupByRegex("de\\.oceanlabs\\.mcp(\\..*)?")
                excludeGroupByRegex("org\\.spongepowered(\\..*)?")
                excludeGroupByRegex("net\\.neoforged(\\..*)?")
                excludeGroupByRegex("dev\\.kikugie(\\..*)?")
            }
        }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "multiplayerspawners"

// release/release-matrix.json is the only list of supported Minecraft versions and loaders.
// One Gradle process configures exactly one Minecraft version (-PmpsTarget=<minecraft>), because
// Loom keeps per-version state that does not mix; scripts/release/build_matrix.py walks them all.
val matrixFile = settingsDir.resolve("release/release-matrix.json")
check(matrixFile.isFile) { "Missing release matrix: $matrixFile" }
@Suppress("UNCHECKED_CAST")
val matrix = groovy.json.JsonSlurper().parse(matrixFile) as Map<String, Any?>
check(matrix["schema_version"] == 1) { "Unsupported release matrix schema in $matrixFile" }
@Suppress("UNCHECKED_CAST")
val artifacts = matrix["artifacts"] as List<Map<String, Any?>>
check(artifacts.size == (matrix["lane_count"] as Number).toInt()) {
    "Release matrix lane_count does not match its ${artifacts.size} artifacts"
}
check(artifacts.map { it["artifact_node"] }.toSet().size == artifacts.size) {
    "Duplicate artifact node in $matrixFile"
}
val target = providers.gradleProperty("mpsTarget").orNull ?: matrix["default_target"].toString()
val targetArtifacts = artifacts.filter { it["minecraft"] == target }
check(targetArtifacts.isNotEmpty()) {
    "Unknown mpsTarget '$target'; the matrix lists ${artifacts.map { it["minecraft"] }.distinct()}"
}
gradle.extensions.extraProperties["mpsMatrix"] = matrix
gradle.extensions.extraProperties["mpsTarget"] = target
gradle.extensions.extraProperties["mpsTargetArtifacts"] = targetArtifacts

stonecutter {
    kotlinController = true
    centralScript = "build.gradle.kts"
    create(rootProject) {
        versions(target)
        branch("common") { versions(target) }
        targetArtifacts.map { it["loader"].toString() }.sorted().forEach { loader ->
            branch(loader) { versions(target) }
        }
    }
}
