import org.gradle.api.tasks.Exec

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
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
    namespace = "com.arm.aichat"
    compileSdk = 36

    ndkVersion = "29.0.13113456"

    defaultConfig {
        minSdk = 33

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

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
        aarMetadata {
            minCompileSdk = 35
        }
    }
    externalNativeBuild {
        cmake {
            path("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)

        compileOptions {
            targetCompatibility = JavaVersion.VERSION_17
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    publishing {
        singleVariant("release") {
            withJavadocJar()
        }
    }
}

tasks.matching { it.name.contains("externalNativeBuild", ignoreCase = true) }.configureEach {
    dependsOn(cloneLlamaCppIfNeeded)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}
