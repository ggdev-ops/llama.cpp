import org.gradle.api.tasks.Exec
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val llamaDir = file("build/llama.cpp")

val cloneLlamaCppIfNeeded by tasks.registering(Exec::class) {
    inputs.property("llamaDirExists", llamaDir.exists())
    outputs.dir(llamaDir)
    onlyIf { !llamaDir.exists() }
    commandLine("git", "clone", "https://github.com/ggerganov/llama.cpp.git", llamaDir.absolutePath)
    doFirst {
        println("Cloning llama.cpp to $llamaDir...")
        llamaDir.parentFile.mkdirs()
    }
    doLast {
        if (!llamaDir.exists()) {
            throw GradleException("Failed to clone llama.cpp to $llamaDir")
        }
        println("llama.cpp cloned successfully.")
    }
}

android {
    namespace = "klama.ai.compose"
    compileSdk = 36

    ndkVersion = "29.0.14206865"
    
    repositories {
        google()
        mavenLocal()
        mavenCentral()
    }

    defaultConfig {
        applicationId = "klama.ai.compose"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a")
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
    
    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    
    kotlin {
        jvmToolchain(21)
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

tasks.matching { it.name.contains("externalNativeBuild", ignoreCase = true) }.configureEach {
    dependsOn(cloneLlamaCppIfNeeded)
}

dependencies {
    implementation(project(":commonApp"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.uiToolingPreview)
}
