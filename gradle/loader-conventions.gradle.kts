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
