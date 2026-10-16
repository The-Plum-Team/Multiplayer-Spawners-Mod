import dev.kikugie.stonecutter.controller.flag.StonecutterFlag

plugins {
    id("dev.kikugie.stonecutter")
    id("dev.architectury.loom") version "1.17.480" apply false
    id("dev.architectury.loom-no-remap") version "1.17.480" apply false
    id("architectury-plugin") version "3.5.170" apply false
    id("com.gradleup.shadow") version "8.3.11" apply false
}

// Detached mode: every node compiles a preprocessed copy of its branch's src/main, and tracked
// files are never rewritten. Version-specific code lives in //? if comments in that one tree.
stonecutter active null

stonecutter {
    flags {
        set(StonecutterFlag.GENERATE_MANIFEST, false)
    }
}

@Suppress("UNCHECKED_CAST")
val targetArtifacts = gradle.extensions.extraProperties["mpsTargetArtifacts"] as List<Map<String, Any?>>
val target = gradle.extensions.extraProperties["mpsTarget"].toString()

tasks.register("buildTarget") {
    group = "build"
    description = "Builds every loader jar of -PmpsTarget=$target declared by release/release-matrix.json."
    dependsOn(targetArtifacts.map { it["gradle_task"].toString() })
}
