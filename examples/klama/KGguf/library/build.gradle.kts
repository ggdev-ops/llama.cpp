import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.vanniktech.mavenPublish)
}

group = "ai.kgguf"
version = "1.0.0"

kotlin {
    jvm()
    androidLibrary {
        namespace = "ai.kgguf"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    
    linuxX64()

    sourceSets {
        commonMain.dependencies {
            implementation("com.squareup.okio:okio:3.9.0")
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    coordinates(group.toString(), "library", version.toString())

    pom {
        name = "KGguf"
        description = "A pure Kotlin GGUF Metadata Reader and Analyzer."
        inceptionYear = "2024"
        url = "https://github.com/kotlin/multiplatform-library-template/"
        licenses {
            license {
                name = "Apache-2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }
        developers {
            developer {
                id = "developer"
                name = "Klama Team"
            }
        }
        scm {
            url = "https://github.com/kotlin/multiplatform-library-template/"
        }
    }
}
