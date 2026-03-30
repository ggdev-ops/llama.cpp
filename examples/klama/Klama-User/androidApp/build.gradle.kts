plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    kotlin("android")
}


android {
    namespace = "ai.llm"
    compileSdk = 36

    ndkVersion = "29.0.14206865"
    
    repositories {
        google()
        mavenLocal()
        mavenCentral()
    }

    defaultConfig {
        applicationId = "ai.llm"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }
    
    
    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(21)
}


dependencies {
    implementation(project(":commonApp"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.uiToolingPreview)
}
