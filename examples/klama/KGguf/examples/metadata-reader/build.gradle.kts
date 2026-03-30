plugins {
    kotlin("jvm")
    application
}

repositories {
    mavenCentral()
}

application {
    mainClass.set("ai.kgguf.example.MainKt")
}

dependencies {
    implementation(project(":library"))
    implementation("com.squareup.okio:okio:3.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}

kotlin {
    jvmToolchain(21)
}
