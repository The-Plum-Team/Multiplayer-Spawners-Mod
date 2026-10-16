import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.language.jvm.tasks.ProcessResources

// Shared by every Stonecutter node (common and each loader) of the -PmpsTarget version. The node
// script applies Loom first, then this file. Everything version-related comes from the release
// matrix row(s) of that version; nothing here names a Minecraft version.
@Suppress("UNCHECKED_CAST")
val matrix = gradle.extensions.extraProperties["mpsMatrix"] as Map<String, Any?>
@Suppress("UNCHECKED_CAST")
val lanes = gradle.extensions.extraProperties["mpsTargetArtifacts"] as List<Map<String, Any?>>
@Suppress("UNCHECKED_CAST")
val modProject = matrix["project"] as Map<String, Any?>
val minecraftVersion = gradle.extensions.extraProperties["mpsTarget"].toString()
val javaVersion = lanes.map { (it["java"] as Number).toInt() }.distinct().single()
val noRemap = lanes.map { it["no_remap"] as Boolean }.distinct().single()

// Minecraft version order, also across the 1.x -> 26.x renumbering: compares numeric parts.
fun versionParts(version: String): List<Int> =
    version.split('.').map { it.toInt() }.let { it + List(maxOf(0, 3 - it.size)) { 0 } }
fun compareVersions(a: String, b: String): Int =
    versionParts(a).zip(versionParts(b)).map { (x, y) -> x.compareTo(y) }.firstOrNull { it != 0 } ?: 0
val atLeast: (String) -> Boolean = { compareVersions(minecraftVersion, it) >= 0 }

extra["mpsMinecraft"] = minecraftVersion
extra["mpsJava"] = javaVersion
extra["mpsNoRemap"] = noRemap
extra["mpsAtLeast"] = atLeast
extra["mpsProject"] = modProject
extra["mpsLanes"] = lanes
// Without remapping, mod dependencies are plain dependencies: there is nothing to remap.
extra["mpsModImplementation"] = if (noRemap) "implementation" else "modImplementation"
extra["mpsModCompileOnly"] = if (noRemap) "compileOnly" else "modCompileOnly"

// Every node of a version is named after it (":common:1.21.1", ":fabric:1.21.1"), so a per-branch
// group keeps Gradle from taking the common node for the loader node that depends on it.
group = "com.theplumteam.multiplayerspawners.${project.parent!!.name}"
version = modProject["mod_version"].toString()

repositories {
    mavenCentral()
}
apply(from = rootProject.file("gradle/repository-policy.gradle.kts"))

extensions.configure<JavaPluginExtension> {
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(javaVersion)
    options.encoding = "UTF-8"
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

// Mixin configs are authored for Java 17; each lane gets its own Java level. They carry no
// reference map: Loom remaps the mixin annotations themselves when it remaps the jar.
tasks.named<ProcessResources>("processResources") {
    val mixinLevel = "JAVA_$javaVersion"
    inputs.property("mixinLevel", mixinLevel)
    filesMatching("*.mixins.json") {
        filter { line -> line.replace("\"JAVA_17\"", "\"$mixinLevel\"") }
    }
}

// Pure renames are rewritten as Stonecutter generates the sources, so the shared tree keeps one
// spelling instead of a //? if around every use.
val sourceRenames = buildList {
    // 1.21.11 renamed ResourceLocation to Identifier and nothing else about it.
    if (atLeast("1.21.11")) add(Regex("\\bResourceLocation\\b") to "Identifier")
    // 26.1 renamed Fabric API's play payload registries after the packet direction.
    if (atLeast("26.1")) add(Regex("\\bplayS2C\\(\\)") to "clientboundPlay()")
    // 26.3 renamed PushReaction.DESTROY to POPPED.
    if (atLeast("26.3")) add(Regex("\\bPushReaction\\.DESTROY\\b") to "PushReaction.POPPED")
}
if (sourceRenames.isNotEmpty()) {
    tasks.matching { it.name == "stonecutterGenerate" }.configureEach {
        inputs.property("mpsSourceRenames", sourceRenames.map { it.first.pattern + "=" + it.second })
        (this as CopySpec).filesMatching("**/*.java") {
            filter { line -> sourceRenames.fold(line) { text, (pattern, replacement) -> pattern.replace(text, replacement) } }
        }
    }
}

// Stonecutter stamps its own version and node into every jar manifest; leave them out so the jars
// depend only on their inputs.
tasks.withType<Jar>().configureEach {
    doFirst {
        manifest.attributes.keys.filter { it.startsWith("Stonecutter-") }.forEach { manifest.attributes.remove(it) }
    }
}
