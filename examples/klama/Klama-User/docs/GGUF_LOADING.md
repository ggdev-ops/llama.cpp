# 📂 GGUF Loading & Lifecycle

Loading a model in **Klama-User** is a multi-step process designed for safety and performance.

## 🏁 Step 1: Selection
The process begins in the `ModelLoaderScreen` when the user clicks the "Select & Load GGUF Model" button.
- **`FilePicker`**: Launches the platform-native file selector.
- **Result**: Returns an Okio `BufferedSource` pointing to the external GGUF file.

## 🧐 Step 2: Validation (Klama Librarian)
Before anything else, we use the `klama-kmp` library to ensure the selected file is a valid GGUF model.
- **Header Peeking**: `GgufMetadataReader` peeks at the first 4 bytes to check for the `GGUF` magic number.
- **Metadata Extraction**: It reads the version, tensor count, and key-value pairs (like architecture name and model name).

## 📥 Step 3: Importing (The "Safe Zone")
On modern operating systems (especially Android), native C++ code often cannot access files from outside the app's internal directory.
- **`FileSystemProvider.copyToSafeZone`**: The `BufferedSource` is streamed into a file within the app's private, internal storage.
- **Benefit**: This ensures the GGML tensor engine has unrestricted read access to the file.

## ⚙️ Step 4: Engine Initialization
Once the file is safely in the internal storage, its **absolute path** is passed to the `EngineBridge`.
- **`EngineBridge.loadModel(pathToModel: String)`**: This bridge communicates with the native JNI layer.
- **`llama.cpp`**: The C++ engine initializes its internal state, loads the tensors, and prepares the KV cache for inference.

---

## 🔁 Lifecycle of the GGUF Path

1.  **URI/File (Source)**: Initial selection by the user.
2.  **Okio `BufferedSource`**: Abstract stream used for metadata reading and copying.
3.  **Local Path (Destination)**: Fixed file path in the application's internal "Safe Zone".
4.  **Native Pointer**: Reference to the model in memory within the GGML engine.

---

## 🛠️ Error Handling

- **`InvalidFileFormatException`**: Raised if the file is not a GGUF.
- **`KlamaIOException`**: Raised if copying to the Safe Zone fails (e.g., out of disk space).
- **Engine Error**: Reported if the native engine cannot parse the file (e.g., unsupported quantization).

---

*“Managing model lifecycle with precision and safety.”* 🛡️📦
