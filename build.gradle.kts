plugins {
    kotlin("multiplatform") version "2.0.21" // Or your desired Kotlin version
}

kotlin {
    macosArm64("macos") {
        binaries {
            framework {
                baseName = "ZLib" // Replace with your framework name
            }
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
            }
        }
        val macosMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
            }
        }
    }

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
        binaries.all {
            freeCompilerArgs += "-Xallocator=mimalloc"
        }
    }



}

repositories {
    mavenCentral()
}

dependencies {
    // Add any dependencies your project needs here.
}