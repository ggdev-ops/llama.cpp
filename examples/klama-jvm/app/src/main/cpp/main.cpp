/**
 * @file main.cpp
 * @brief This file serves as a simple example demonstrating the basic initialization
 *        and shutdown of the llama.cpp backend within a C++ application.
 *        It is primarily for testing the integration of the llama.cpp library.
 *        Complex inference logic and JNI bindings are handled in `llama_jni.cpp`.
 */
#include <iostream>
#include <llama.h>

int main() {
    std::cout << "Starting llama.cpp Linux Gradle example..." << std::endl;

    // Initialize the backend
    llama_backend_init();

    std::cout << "llama.cpp backend initialized successfully!" << std::endl;

    // Clean up
    llama_backend_free();

    std::cout << "llama.cpp backend freed. Exiting." << std::endl;
    return 0;
}
