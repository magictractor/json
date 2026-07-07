// This file should not be edited.
// setting.gradle.kts is expected to be the same in all magictractor projects.
//
// Settings specific to the project should be placed in settings.project.gradle.kts.
// rootProject.name is expected to be set in settings.project.gradle.kts.
//
// settings.project-local.gradle.kts may also be used for project specific settings
// that are not version managed. This is typically used with includeBuild().

pluginManagement {
    // Snapshots of magictractor-plugin are likely to be in the local Maven repository.
    repositories {
        mavenLocal()
        gradlePluginPortal()
    }

    plugins {
        id("uk.co.magictractor.magictractor-settings-plugin") version "0.0.1-SNAPSHOT"
        id("uk.co.magictractor.magictractor-plugin") version "0.0.1-SNAPSHOT"
    }
  
    //includeBuild("../gradle")
}

plugins {
    // Common settings are set by the magictractor-settings-plugin defined in the magictractor-gradle project.
    // This will apply project.settings.gradle.kts and project-local.settings.gradle.kts (latter optional)
    // and set up a version catalog for commonly used libs.
    
    id("uk.co.magictractor.magictractor-settings-plugin")
}
