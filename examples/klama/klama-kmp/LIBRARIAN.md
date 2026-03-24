# 📖 The Klama Librarian: GGUF Metadata Reader

The **Librarian** is a high-performance, pure-Kotlin metadata parser for GGUF model files. It is designed to run on any platform supported by Kotlin Multiplatform (Android, JVM, Linux, iOS) without requiring the heavy `llama.cpp` native engine to be loaded.

## 🚀 Key Features
- **Zero Native Overhead**: Read model stats (Architecture, Tensors, Context) in milliseconds without loading `.so` or `.a` libraries.
- **Okio Powered**: Uses a unified I/O stream that works identically on Arch Linux file systems and Android ContentProviders.
- **KMP-Native**: 100% platform-independent logic in `commonMain`.

---

## 🛠️ Usage

### 1. Common Usage (Shared Logic)
In your shared Compose or business logic code, use the `GgufMetadataReader` interface with an Okio `BufferedSource`.

```kotlin
val reader = GgufMetadataReader.create()
source.use { s ->
    val metadata = reader.readStructuredMetadata(s)
    println("Model: ${metadata.basic.name}")
    println("Arch: ${metadata.architecture?.architecture}")
}
```

### 2. Android Implementation (Uri Support)
On Android, you typically deal with `content://` URIs. Use the provided extension function in `androidMain`:

```kotlin
// In your Activity or ViewModel
val reader = GgufMetadataReader.create()
val metadata = reader.readMetadata(context, fileUri)
```

### 3. JVM/Desktop Implementation (File Support)
On Desktop, you can work directly with `java.io.File`. Use the extension function in `jvmMain`:

```kotlin
val reader = GgufMetadataReader.create()
val file = File("path/to/model.gguf")
val metadata = reader.readMetadata(file)
```

---

## 🏗️ Architecture

### Peeking Magic
The Librarian uses Okio's `source.peek()` mechanism to verify file formats. This allows us to check the `GGUF` magic bytes at the start of a stream **without consuming them**, ensuring the stream is still valid for full parsing afterward.

### Endian Awareness
GGUF is a little-endian format. The Librarian leverages Okio's native little-endian methods:
- `readIntLe()`: For version and type codes.
- `readLongLe()`: For tensor and KV pair counts.

### Data Model
The `GgufMetadata` object provides a structured view of the model:
- `basic`: UUID, Name, and Size labels.
- `architecture`: Model family (llama, gemma, etc.) and quantization version.
- `dimensions`: Context length and embedding sizes.

---

## ⚠️ Requirements
- **Kotlin**: 2.0.0+
- **Okio**: 3.9.0+
- **Library Dependency**: `implementation("io.github.kotlin:library:1.0.0")` (local maven)

---

*“No C++ was harmed in the making of this metadata reader.”* 🏎️🔥🎓
