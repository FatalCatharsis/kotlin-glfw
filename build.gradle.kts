import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithHostTests
import org.jetbrains.kotlin.konan.target.KonanTarget

plugins {
    kotlin("multiplatform") version "1.9.0"
    id("maven-publish")
}

group = "org.glfw"
version = "3.3.8"

repositories {
    mavenCentral()
}

val cmakeDirectory = project.buildDir.resolve("cmake")

kotlin {
    mingwX64()
    linuxX64()

    targets.withType<KotlinNativeTarget> {
        val main by compilations.getting {
            tasks {
                val cmakeTask = register<Exec>("cmake${project.name}${konanTarget.name}") {
                    description = "Generate makefiles for ${konanTarget.name} toolchain."

                    val commandString = listOf("cmake", "-S", ".", "-B", cmakeDirectory.toString(), "-G", getCmakeGenerator(konanTarget))
                    commandLine = if (konanTarget == KonanTarget.LINUX_X64) {
                        commandString + listOf("-D", "GLFW_USE_WAYLAND=1")
                    } else {
                        commandString
                    }
                }

                val makeTask = register<Exec>("make${project.name}${konanTarget.name}") {
                    dependsOn(cmakeTask)
                    description = "Builds the glfw static lib for ${konanTarget.name}."
                    workingDir = cmakeDirectory
                    commandLine = if (konanTarget == KonanTarget.MINGW_X64) {
                        listOf("mingw32-make")
                    } else {
                        listOf("make")
                    }
                }
                getByName(processResourcesTaskName).dependsOn(makeTask)
            }

            cinterops {
                create("glfw") {
                    includeDirs("${rootDir}/include")
                }
            }

            kotlinOptions {
                freeCompilerArgs = listOf("-include-binary", cmakeDirectory.resolve("src/libglfw3.a").toString())
            }
        }
    }

    sourceSets {
        val commonMain by getting
        val nativeMain by creating {
            dependsOn(commonMain)
        }
        val mingwX64Main by getting {
            dependsOn(nativeMain)
        }
        val linuxX64Main by getting {
            dependsOn(nativeMain)
        }
    }
}

publishing {
    repositories {
        maven {
            name = "wyatt-repo"
            url = uri("http://192.168.1.16:8081/repository/maven-releases")
            isAllowInsecureProtocol = true

            credentials {
                val wyattRepoUsername : String by project
                val wyattRepoPassword : String by project
                username = wyattRepoUsername
                password = wyattRepoPassword
            }
        }
    }
}

fun getCmakeGenerator(target : KonanTarget) : String = when (target) {
    KonanTarget.MINGW_X64 -> "MinGW Makefiles"
    KonanTarget.LINUX_X64 -> "Unix Makefiles"
    else -> throw RuntimeException("Cannot determine generate for current target")
}

//tasks {
//    val cmakeMingwX64 by registering(Exec::class) {
//        description = "Generate makefiles for MinGW-w64 toolchain."
//        commandLine = listOf("cmake", "-S", ".", "-B", "\"${cmakeDirectory}\"", "-G", "\"MinGW Makefiles\"")
//    }
//
//    val buildGlfwMingwX64 by registering(Exec::class) {
//        dependsOn(cmakeMingwX64)
//        description = "Builds the glfw static lib for mingw-w64."
//        commandLine = listOf("mingw32-make", "\"${cmakeDirectory}\"")
//    }
//    getByName("build").dependsOn(buildGlfwMingwX64)
//
//    val cmakeLinux by registering(Exec::class) {
//        description = "Generate makefiles for Unix c toolchains."
//        commandLine = listOf("cmake", "-S", ".", "-B", "\"${cmakeDirectory}\"", "-G", "\"Unix Makefiles\"", )
//    }
//    val buildGlfwLinux by registering(Exec::class) {
//        dependsOn(cmakeLinux)
//        description = "Builds the glfw static libs for linux. Note : Only works on linux environments."
//        commandLine = listOf("make", "\"${cmakeDirectory}\"")
//    }
//    getByName("linuxX64MainK").dependsOn(buildGlfwLinux)
//}