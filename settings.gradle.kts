pluginManagement {
    repositories {
        //gradlePluginPortal()
        // Snapshots of magictractor-plugin are likely to be in the local Maven repository.
        mavenLocal()
    }

    plugins {
        id("uk.co.magictractor.magictractor-settings-plugin") version "0.0.1-SNAPSHOT"
        id("uk.co.magictractor.magictractor-plugin") version "0.0.1-SNAPSHOT"
    }
}

plugins {
    id("uk.co.magictractor.magictractor-settings-plugin")
}


rootProject.name = "magictractor-json"
rootProject.buildFileName = "json.gradle.kts"
