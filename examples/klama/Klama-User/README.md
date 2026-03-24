# 🤖 Klama-User: Professional AI Programming Tutor

**Klama-User** is a Compose Multiplatform application designed to provide a professional AI programming tutor experience. It leverages a high-performance C/C++ GGML tensor engine for local inference and integrates the **Klama Librarian** for efficient GGUF metadata management.

## 🌟 Key Features

- **Compose Multiplatform**: A unified UI for Android and Desktop (JVM) built with JetBrains Compose.
- **Local Inference**: Powered by a real C/C++ GGML engine (`llama.cpp` integration) for fast, private, and offline AI chat.
- **Smart Model Loading**: Uses the **Klama Librarian** (`klama-kmp`) to read GGUF metadata *before* importing the model into the engine's "Safe Zone".
- **Safe Zone Architecture**: Automatically copies selected GGUF files to an internal application directory to ensure consistent access and performance for the native engine.

---

## 🏗️ Project Structure

- `androidApp`: Android-specific implementation and JNI/C++ bridge.
- `desktopApp`: Desktop-specific implementation for Windows, macOS, and Linux.
- `commonApp`: Shared Compose UI, ViewModels, and domain logic.
  - `klama.ai.compose.engine`: Interface for the native inference engine.
  - `klama.ai.compose.io`: Platform-specific file picking and storage management.
  - `klama.ai.compose.domain`: Business logic for model management and chat.

---

## 🚀 Getting Started

### 1. Prerequisites
- **JDK 17+**
- **Android Studio** (for Android) or **IntelliJ IDEA** (for Desktop)
- **CMake & NDK** (for building the native C++ engine on Android)

### 2. Prepare the Klama Librarian
The app depends on a local version of the `klama-kmp` library. You must publish it to your `mavenLocal` first.

```bash
cd examples/klama/klama-kmp
./gradlew publishToMavenLocal
```

### 3. Build and Run
After publishing the library, you can build and run **Klama-User**.

#### For Android:
```bash
cd examples/klama/Klama-User
./gradlew :androidApp:installDebug
```

#### For Desktop:
```bash
cd examples/klama/Klama-User
./gradlew :desktopApp:run
```

---

## 📖 Documentation

Detailed documentation is available in the `docs/` directory:

- [**Architecture Overview**](docs/ARCHITECTURE.md): Deep dive into the integration of Kotlin and C++.
- [**GGUF Loading Flow**](docs/GGUF_LOADING.md): How models are selected, verified, and loaded.
- [**Native Engine Integration**](docs/NATIVE_ENGINE.md): Details on the JNI bridge and GGML tensor engine.

---

## 🤝 Relationship with Klama-KMP

**Klama-User** is the primary consumer of the `klama-kmp` (Librarian) library. 
1. **Selection**: User selects a `.gguf` file via a platform-specific `FilePicker`.
2. **Verification**: `klama-kmp` reads the header to verify it's a valid GGUF file and extracts metadata (Architecture, Name, Version).
3. **Import**: The file is copied to the app's internal "Safe Zone".
4. **Execution**: The local path is passed to the C++ engine for inference.

---

*“Your local programming mentor, powered by Klama.”* 🧠💻
