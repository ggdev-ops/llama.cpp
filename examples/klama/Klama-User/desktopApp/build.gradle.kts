import org.gradle.api.tasks.Exec
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.kotlinJvm)
}

repositories {
    google()
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation(project(":commonApp"))
    implementation(libs.compose.ui)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okio)
}

kotlin {
    jvmToolchain(21)
}



compose.desktop {
    application {
        mainClass = "ai.llm.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "ai.llm"
            packageVersion = "1.0.0"
        }
    }
}
