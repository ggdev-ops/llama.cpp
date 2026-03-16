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
