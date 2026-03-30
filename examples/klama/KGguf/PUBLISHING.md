# KGguf Library Guide: Local Publishing and Integration

This guide provides step-by-step instructions on how to publish the **KGguf** library to your local Maven repository and use it in a separate project.

---

## 1. Overview of the KGguf Library

**KGguf** is a pure Kotlin Multiplatform (KMP) library designed for reading and analyzing GGUF metadata.

- **Targets:** JVM, Android, Linux X64.
- **Key Components:**
    - `GgufMetadataReader`: The core interface for parsing GGUF files.
    - `GgufModel`: A high-level wrapper that provides insights into model capabilities (e.g., architecture, quantization, instruct vs. vision).
    - **Dependencies:** Uses [Okio](https://github.com/square/okio) for efficient I/O and [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines).

### Example Usage (from `metadata-reader` example)
```kotlin
val filePath = "model.gguf".toPath()
val reader = GgufMetadataReader.create()

runBlocking {
    val fileSystem = FileSystem.SYSTEM
    fileSystem.read(filePath) {
        runBlocking {
            val metadata = reader.readStructuredMetadata(this@read)
            val model = GgufModel(metadata)
            println("Model Architecture: ${model.metadata.architecture?.architecture}")
        }
    }
}
```

---

## 2. Publish to Local Maven Repository

The library uses the `vanniktech.mavenPublish` plugin, which makes local publishing straightforward.

### Steps:

1.  **Open a terminal** in the root directory of the `KGguf` project.
2.  **Run the publishing command:**
    ```bash
    ./gradlew :library:publishToMavenLocal
    ```
    *Note: Using `:library:publishToMavenLocal` targets only the library module. If you want to publish everything, just use `./gradlew publishToMavenLocal`.*

3.  **Verify the Publication:**
    Check your local Maven repository (usually located at `~/.m2/repository/`) to ensure the files were created.
    ```bash
    ls -R ~/.m2/repository/ai/kgguf/library/1.0.0/
    ```

---

## 3. Use the Library in Another Project

To use the locally published version of `KGguf` in a different Gradle project, follow these configuration steps.

### A. Add `mavenLocal()` Repository
In the **root** `settings.gradle.kts` (or `build.gradle.kts` of the consuming project), ensure `mavenLocal()` is included in the repositories block:

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenLocal() // Check local repository first
        mavenCentral()
    }
}
```

### B. Add the Dependency
In your module's `build.gradle.kts` (e.g., `app/build.gradle.kts` or `commonMain` source set for KMP):

#### For a standard Kotlin/JVM project:
```kotlin
dependencies {
    implementation("ai.kgguf:library:1.0.0")
    // Optional: Add Okio if you need to use the Path/FileSystem APIs
    implementation("com.squareup.okio:okio:3.9.0")
}
```

#### For a Kotlin Multiplatform project:
```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("ai.kgguf:library:1.0.0")
        }
    }
}
```

---

## 4. Troubleshooting

- **Version Conflict:** If you make changes to the library and republish, ensure you either increment the version in `library/build.gradle.kts` (e.g., `1.0.1-SNAPSHOT`) or clear the Gradle cache to see the updates in the consuming project.
- **Missing Targets:** Ensure the consuming project's target platform (e.g., JVM, Android) matches one of the targets supported by the library.
- **Gradle Sync:** Always perform a Gradle sync after adding `mavenLocal()` and the dependency.
