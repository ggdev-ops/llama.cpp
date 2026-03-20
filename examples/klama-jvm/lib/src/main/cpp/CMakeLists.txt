cmake_minimum_required(VERSION 3.20)
project(llama-jni CXX)

# Find JNI
find_package(JNI REQUIRED)

if(NOT DEFINED LLAMA_SRC)
    message(FATAL_ERROR "LLAMA_SRC must be defined. Please provide the path to the llama.cpp source directory.")
endif()

message(STATUS "Using LLAMA_SRC: ${LLAMA_SRC}")

# Add the llama.cpp sub-project
add_subdirectory(${LLAMA_SRC} build-llama)

# Create the shared library for JNI
add_library(llama-jni SHARED llama_jni.cpp)

# Include directories
target_include_directories(llama-jni PRIVATE
    ${JNI_INCLUDE_DIRS}
    ${LLAMA_SRC}
    ${LLAMA_SRC}/common
    ${LLAMA_SRC}/include
    ${LLAMA_SRC}/ggml/include
    ${LLAMA_SRC}/vendor
)

# Link against llama and common libraries
target_link_libraries(llama-jni PRIVATE llama common)

# Set output name to match System.loadLibrary("llama-jni")
set_target_properties(llama-jni PROPERTIES OUTPUT_NAME "llama-jni")
