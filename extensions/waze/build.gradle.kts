extension {
    name = "extensions/waze-audio.mpe"
}

android {
    namespace = "app.pomee4.waze"

    defaultConfig {
        minSdk = 32
    }
}

configurations.configureEach {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    exclude(group = "org.jetbrains", module = "annotations")
}
