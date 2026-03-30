# 📖 KGguf: GGUF Metadata Reader for Kotlin

**KGguf** is a high-performance, pure-Kotlin metadata parser for GGUF model files. It is designed to run on any platform supported by Kotlin Multiplatform (Android, JVM, Linux, iOS) without requiring the heavy `llama.cpp` native engine to be loaded.

## 🚀 Key Features
- **Zero Native Overhead**: Read model stats (Architecture, Tensors, Context) in milliseconds without loading `.so` or `.a` libraries.
- **Smart Inference**: Automatically detects model capabilities like Tool support, JSON support, and Instruct tuning.
- **Okio Powered**: Uses a unified I/O stream that works identically on Arch Linux file systems and Android ContentProviders.
- **KMP-Native**: 100% platform-independent logic in `commonMain`.

---

## 🛠️ Usage

### 1. Low-Level Metadata Access
Use `GgufMetadataReader` to get a structured view of the physical file content.

```kotlin
val reader = GgufMetadataReader.create()
source.use { s ->
    // Returns GgufMetadata (The "Dumb" container)
    val metadata = reader.readStructuredMetadata(s)
    println("Raw Arch: ${metadata.architecture?.architecture}")
}
```

### 2. High-Level Model Analysis
Wrap the metadata in a `GgufModel` to get human-readable insights and inferred capabilities.

```kotlin
val model = GgufModel(metadata)
val caps = model.inferCapabilities()

println("Model ID: ${model.toString()}")
println("Has Tool Support: ${caps.hasToolSupport}")
println("Quantization: ${caps.quantization?.label}")
```

---

## 🏗️ Architecture

**KGguf** follows a clean, two-layer architecture:

### 📥 Layer 1: The Parser (`GgufMetadataReader`)
Responsible for IO and byte-level parsing. It populates a `GgufMetadata` object which contains:
- **`basic`**: Name, UUID, and size labels.
- **`architecture`**: Model family and quantization version.
- **`raw`**: A complete map of all key-value pairs found in the GGUF file for advanced users.

### 🧠 Layer 2: The Analyzer (`GgufModel`)
The "Smart" layer that interprets raw metadata. It provides:
- **`Capabilities`**: Inferred features like `isInstruct`, `hasToolSupport`, and `hasJsonSupport`.
- **`Quantization`**: Mapping of technical `file_type` codes to readable names (e.g., `15` -> `Q4_K_M`).
- **`ParameterCount`**: Estimation of model size (e.g., `3B`, `7B`) from metadata labels.

---

## 🛠️ Implementation Details

### Endian Awareness
GGUF is a little-endian format. **KGguf** leverages Okio's native little-endian methods:
- `readIntLe()`: For version and type codes.
- `readLongLe()`: For tensor and KV pair counts.

### Chat Template Analysis
By analyzing the `tokenizer.chat_template` string, **KGguf** can detect if a model has native support for **Tool Calling** and **JSON Schemas** without needing a hardcoded model registry.

---

## ⚠️ Requirements
- **Kotlin**: 2.1.0+
- **Okio**: 3.9.0+
- **Library Dependency**: `implementation("ai.kgguf:library:1.0.0")`

---

*“No C++ was harmed in the making of this metadata reader.”* 🏎️🔥🎓
