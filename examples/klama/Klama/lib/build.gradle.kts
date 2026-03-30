import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.vanniktech.mavenPublish)
}

group = "klama.ai"
version = "1.0.0-SNAPSHOT"

val llamaDirProvider = layout.buildDirectory.dir("llama.cpp")

abstract class CloneLlamaTask : Exec() {
    @get:OutputDirectory
    abstract val destinationDir: DirectoryProperty

    @get:Input
    val repoUrl = "https://github.com/ggerganov/llama.cpp.git"

    init {
        onlyIf { !destinationDir.get().asFile.exists() }
    }

    override fun exec() {
        val dest = destinationDir.get().asFile
        commandLine("git", "clone", repoUrl, dest.absolutePath)
        println("Cloning llama.cpp to ${dest.absolutePath}...")
        super.exec()
    }
}

val cloneLlamaCppIfNeeded = tasks.register<CloneLlamaTask>("cloneLlamaCppIfNeeded") {
    destinationDir.set(llamaDirProvider)
}
val llamaDir = llamaDirProvider.get().asFile

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        val androidMain by getting
        val jvmMain by getting
    }
}

// Android native build setup for lib
android {
    namespace = "klama.ai"
    compileSdk = 36
    ndkVersion = "29.0.14206865"
    
    defaultConfig {
        minSdk = 33
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                arguments += "-DLLAMA_SRC=${llamaDir.absolutePath}"
                arguments += "-DCMAKE_BUILD_TYPE=Release"
                arguments += "-DCMAKE_MESSAGE_LOG_LEVEL=DEBUG"
                arguments += "-DCMAKE_VERBOSE_MAKEFILE=ON"
                arguments += "-DBUILD_SHARED_LIBS=ON"
                arguments += "-DLLAMA_BUILD_COMMON=ON"
                arguments += "-DLLAMA_OPENSSL=OFF"
                arguments += "-DGGML_NATIVE=OFF"
                arguments += "-DGGML_BACKEND_DL=ON"
                arguments += "-DGGML_CPU_ALL_VARIANTS=ON"
                arguments += "-DGGML_LLAMAFILE=OFF"
            }
        }
    }
    
    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

tasks.matching { it.name.contains("externalNativeBuild", ignoreCase = true) || it.name.contains("CMake", ignoreCase = true) }.configureEach {
    dependsOn(cloneLlamaCppIfNeeded)
}

val cmakeConfigure by tasks.registering(Exec::class) {
    dependsOn(cloneLlamaCppIfNeeded)
    workingDir(file("build/cmake"))
    commandLine(
        "cmake", "../../src/main/cpp",
        "-DLLAMA_SRC=${llamaDir.absolutePath}",
        "-DCMAKE_BUILD_TYPE=Release",
        "-DLLAMA_BUILD_COMMON=ON",
        "-DBUILD_SHARED_LIBS=OFF",
        "-DCMAKE_POSITION_INDEPENDENT_CODE=ON",
        "-DLLAMA_OPENSSL=OFF"
    )
    doFirst {
        workingDir.mkdirs()
    }
}

val cmakeBuild by tasks.registering(Exec::class) {
    dependsOn(cmakeConfigure)
    workingDir(file("build/cmake"))
    commandLine("cmake", "--build", ".")
}

val syncDesktopNativeLibs = tasks.register<Sync>("syncDesktopNativeLibs") {
    dependsOn(cmakeBuild)
    from(file("build/cmake")) {
        include("*.so", "*.dylib", "*.dll")
    }
    into(layout.buildDirectory.dir("desktopNativeLibs"))
}


mavenPublishing {
    publishToMavenCentral()

    coordinates(group.toString(), "lib", version.toString())

    pom {
        name = "Klama Library"
        description = "Klama AI Library"
        inceptionYear = "2024"
        url = "https://github.com/ggerganov/llama.cpp"
        licenses {
            license {
                name = "MIT"
                url = "https://opensource.org/licenses/MIT"
            }
        }
        developers {
            developer {
                id = "ggerganov"
                name = "Georgi Gerganov"
            }
        }
        scm {
            url = "https://github.com/ggerganov/llama.cpp"
        }
    }
}

kotlin.sourceSets.named("jvmMain") {
    resources.srcDir(syncDesktopNativeLibs)
}
