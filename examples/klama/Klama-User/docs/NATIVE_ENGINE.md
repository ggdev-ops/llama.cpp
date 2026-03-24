# 🚅 Native Engine Integration

The core power of **Klama-User** comes from its high-performance C++ engine, which is built on top of `llama.cpp` and optimized for local device inference.

## 🛠️ Technology Stack
- **`llama.cpp`**: The primary backend for model loading and tensor computation.
- **GGML**: Used for low-level tensor operations.
- **JNI (Java Native Interface)**: The bridge connecting Kotlin's Compose layer with the native C++ engine.
- **CMake**: Manages the build system for the native components.

---

## 🏗️ JNI Architecture (Android)

The JNI layer is centered around `ai_chat.cpp`, which defines the following responsibilities:
1.  **Model Loading**: Maps the local GGUF file into memory.
2.  **Context Management**: Handles the creation and destruction of the llama context.
3.  **Inference Streaming**: Streams predicted tokens back to the Kotlin layer in real-time.
4.  **Sampling Parameters**: Manages temperature, Top-P, and Top-K settings.

---

## 🔄 Interaction Flow

1.  **`EngineBridge` (Kotlin)**: Receives a request to load a model path.
2.  **JNI Native Call**: Kotlin calls a `native` function, passing the model's file path.
3.  **Native Loader**: `llama.cpp` initializes the model and prepares the KV cache.
4.  **Token Generation**: When a user sends a prompt, the engine begins inference.
5.  **Streaming**: As tokens are generated, they are pushed back to the Kotlin UI through a listener or a state flow.

---

## 🛠️ Building the Engine

### Android
The native engine is automatically built as part of the Android build process using the **NDK**.
- **Configuration**: See `androidApp/src/main/cpp/CMakeLists.txt`.
- **Requirements**: Android NDK 26.0+ is recommended.

### Desktop
On desktop platforms, the engine is typically compiled as a shared library (`.so`, `.dll`, or `.dylib`) that is loaded dynamically at runtime by the JVM.

---

*“High-performance inference, right where your code is.”* 🚅💨
