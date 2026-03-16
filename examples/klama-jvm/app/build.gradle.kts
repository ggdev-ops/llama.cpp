//import org.gradle.api.tasks.Exec

plugins {
    kotlin("jvm") version "2.3.10"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("com.varabyte.kotter:kotter:1.2.1")
    implementation("com.squareup.okio:okio:3.9.0")
}

application {
    mainClass.set("com.arm.aichat.main.MainKt")
}

tasks.withType<JavaExec> {
    // Tell JVM where to find the libllama-jni.so
    systemProperty("java.library.path", file("build/cmake"))
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

// Ensure native build happens before we run the app
tasks.named("run") {
    dependsOn(cmakeBuild)
}

tasks.named("assemble") {
    dependsOn(cmakeBuild)
}
