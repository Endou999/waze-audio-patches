group = "app.pomee4"

patches {
    about {
        name = "Endou999 Waze audio patches (fork of pomee4)"
        description = "Adds a custom music app to the Waze audio player"
        source = "https://github.com/Endou999/waze-audio-patches.git"
        author = "pomee4"
        contact = "na"
        website = "https://github.com/Endou999/waze-audio-patches"
        license = "GPLv3"
    }
}

// Separate configuration so gson is available at runtime for the
// generatePatchesList task but never bundled into the APK.
val patchListGeneratorClasspath = configurations.create("patchListGeneratorClasspath")

dependencies {
    compileOnly(libs.gson)
    patchListGeneratorClasspath(libs.gson)
}

tasks {
    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"

        dependsOn(build)

        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("util.PatchListGeneratorKt")
    }

    // Used by gradle-semantic-release-plugin.
    publish {
        dependsOn("generatePatchesList")
    }
}
