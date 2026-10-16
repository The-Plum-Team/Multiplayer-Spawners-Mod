import org.gradle.language.jvm.tasks.ProcessResources

// Shared by the loader nodes (fabric, forge, neoforge) after lane-conventions.gradle.kts.
val loaderName = project.parent!!.name
val minecraftVersion = extra["mpsMinecraft"].toString()
@Suppress("UNCHECKED_CAST")
val modProject = extra["mpsProject"] as Map<String, Any?>

extensions.configure<BasePluginExtension> {
    archivesName.set("MultiplayerSpawners-$loaderName-$minecraftVersion")
}

// Stonecutter preprocesses <loader>/src/main/java into this directory for the current version.
val generatedJava = layout.buildDirectory.dir("generated/stonecutter/main/java")
extensions.configure<SourceSetContainer> {
    named("main") {
        java.setSrcDirs(listOf(generatedJava))
        resources.setSrcDirs(listOf(rootProject.file("$loaderName/src/main/resources")))
    }
}
tasks.named("compileJava") { dependsOn("stonecutterGenerate") }
tasks.matching { it.name == "sourcesJar" }.configureEach { dependsOn("stonecutterGenerate") }

// Values substituted into fabric.mod.json and (neoforge.)mods.toml.
@Suppress("UNCHECKED_CAST")
extra["mpsMetadataExpansions"] = mapOf(
    "mod_id" to modProject["mod_id"].toString(),
    "mod_name" to modProject["name"].toString(),
    "version" to modProject["mod_version"].toString(),
    "description" to modProject["description"].toString(),
    "authors" to (modProject["authors"] as List<String>).joinToString(", "),
    "license" to modProject["license"].toString(),
    "sources" to modProject["sources"].toString(),
    "issues" to modProject["issues"].toString(),
)

// The shared CI (mod-base) passes -PmpsBuildIdentity=<json>: which commit, tree and lane this jar
// was built from. It is embedded so that a later step can tell the jar it tests is the one built.
// Local builds pass nothing and embed nothing.
val buildIdentity = providers.gradleProperty("mpsBuildIdentity").orNull
if (buildIdentity != null) {
    val identityDir = layout.buildDirectory.dir("generated/build-identity")
    val writeIdentity = tasks.register("writeBuildIdentity") {
        inputs.property("identity", buildIdentity)
        outputs.dir(identityDir)
        doLast {
            // The CI names the build; this jar adds which lane and role it is.
            @Suppress("UNCHECKED_CAST")
            val fields = groovy.json.JsonSlurper().parseText(buildIdentity) as Map<String, Any?>
            val identity = (fields + mapOf("lane" to "$loaderName-$minecraftVersion", "role" to "production")).toSortedMap()
            val file = identityDir.get().asFile.resolve("META-INF/multiplayerspawners-build.json")
            file.parentFile.mkdirs()
            file.writeText(groovy.json.JsonOutput.toJson(identity) + "\n")
        }
    }
    tasks.named<ProcessResources>("processResources") {
        dependsOn(writeIdentity)
        from(identityDir)
    }
}
