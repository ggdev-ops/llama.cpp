plugins {
    kotlin("jvm") version "2.3.10"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":lib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("com.varabyte.kotter:kotter:1.2.1")
    implementation("com.squareup.okio:okio:3.9.0")
}

application {
    mainClass.set("com.arm.aichat.main.MainKt")
}

tasks.withType<JavaExec> {
    // Tell JVM where to find the libllama-jni.so (now built in lib module)
    systemProperty("java.library.path", project(":lib").file("build/cmake"))
}
