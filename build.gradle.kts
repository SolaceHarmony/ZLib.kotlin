plugins {
    kotlin("multiplatform") version "2.0.21" // Or your desired Kotlin version
}

kotlin {
    // jvm() // JVM target removed
    macosArm64("macos") {
        binaries {
            framework {
                baseName = "ZLib" // Replace with your framework name
            }
        }
    }

    linuxX64("linux") {
        binaries {
            executable() // Defines a default executable
        }
        compilations.getByName("main").defaultSourceSet.dependsOn(sourceSets.getByName("commonMain"))
        compilations.getByName("test").defaultSourceSet.dependsOn(sourceSets.getByName("commonTest"))
    }

    linuxArm64("linuxArm") {
        binaries {
            executable() // Defines a default executable
        }
        compilations.getByName("main").defaultSourceSet.dependsOn(sourceSets.getByName("commonMain"))
        compilations.getByName("test").defaultSourceSet.dependsOn(sourceSets.getByName("commonTest"))
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        // Ensure no jvmMain or jvmTest source sets are defined
        val macosMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
            }
        }
        // Explicitly define macosTest if not already present by convention,
        // linking it to commonTest.
        val macosTest by getting {
            dependsOn(commonTest)
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