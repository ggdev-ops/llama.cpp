import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
}

repositories {
    google()
    mavenLocal()
    mavenCentral()
}

kotlin {
    androidLibrary {
        namespace = "ai.llm.common"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiTooling)
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.androidx.activity.compose)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.material.icons.extended)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiTooling)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation("ai.kgguf:library:1.0.0")
            api("klama.ai:lib:1.0.0-SNAPSHOT")
            implementation(libs.okio)
            implementation(libs.androidx.datastore.preferences)
            implementation(libs.kotlinx.coroutines.core)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
        }
    }
}
