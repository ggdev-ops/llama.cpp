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

tasks.withType<JavaExec> {
    val libraryPath = file("build/cmake").absolutePath
    systemProperty("java.library.path", libraryPath)
    // Also set it via environment variable just in case
    environment("LD_LIBRARY_PATH", libraryPath)
}

compose.desktop {
    application {
        mainClass = "klama.ai.compose.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "klama.ai.compose"
            packageVersion = "1.0.0"
        }
    }
}
