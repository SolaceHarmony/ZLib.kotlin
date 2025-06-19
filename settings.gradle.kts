rootProject.name = "Kognitive"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        maven("https://repo.kotlin.link")
        mavenCentral()
    }
}

include("shared")