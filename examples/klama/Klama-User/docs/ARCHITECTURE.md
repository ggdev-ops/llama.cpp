# 🏗️ Klama-User Architecture Overview

The **Klama-User** project is built as a **Compose Multiplatform** application, which allows for a shared UI and business logic across different platforms (Android and Desktop).

## 🧩 Component Breakdown

### 📱 `commonApp` (The Core)
The `commonApp` module contains the heart of the application:
- **UI**: JetBrains Compose for Desktop/Android.
- **ViewModel**: `ChatViewModel` manages the state of the chat and the inference engine's status.
- **Domain**: `ModelManager` orchestrates the model lifecycle (picking, reading metadata, importing, loading).
- **Engine Interface**: `EngineBridge` defines how the UI communicates with the platform-specific inference engine.
- **IO Interface**: `FilePicker` and `FileSystemProvider` handle cross-platform file selection and storage.

### 📚 `klama-kmp` (The Librarian)
A lightweight Kotlin Multiplatform library used as a GGUF metadata parser. It allows **Klama-User** to:
1.  **Validate** GGUF files without loading them into memory.
2.  **Display** model information (architecture, name, quantization) to the user before starting the heavy inference engine.
3.  **Ensure** the file is valid before attempting an expensive copy operation to the "Safe Zone".

### 🚀 Platform Modules (`androidApp`, `desktopApp`)
These modules implement the `expect`/`actual` declarations from `commonApp`:
- **FilePicker**: Implementation of file picking (Android Intent/SAF vs. Swing/AWT on Desktop).
- **FileSystemProvider**: Providing paths to platform-specific "Safe Zone" directories (internal storage vs. user home).
- **EngineBridge**: Concrete implementation that talks to the platform-specific JNI/C++ code.

---

## 🌉 The C++/JNI Bridge (Android)

On Android, the inference engine is implemented using `llama.cpp` and a JNI bridge:
- **`ai_chat.cpp`**: The JNI implementation that initializes the `llama.cpp` model, handles sampling, and streams tokens back to Kotlin.
- **`EngineBridge` implementation**: Calls the native JNI methods and converts the results into a Kotlin `Flow<String>` for the UI.

## 📁 Safe Zone Strategy

To avoid access permission issues with the native C++ engine, **Klama-User** employs a "Safe Zone" strategy:
1.  User picks a file from any location (e.g., Downloads, SD Card).
2.  The app gets an Okio `BufferedSource` for the file.
3.  The app copies the entire file to its **internal storage** (the Safe Zone).
4.  The native engine is then given the **absolute path** to this internal file, ensuring it has full read/write permissions.

---

*“Bridging Kotlin's elegance with C++'s power.”* 🏎️💎
