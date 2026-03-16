cmake_minimum_required(VERSION 3.20)
project(llama-jni CXX)

# Find JNI
find_package(JNI REQUIRED)

if(NOT DEFINED LLAMA_SRC)
    message(FATAL_ERROR "LLAMA_SRC must be defined. Please provide the path to the llama.cpp source directory.")
endif()

message(STATUS "Using LLAMA_SRC: ${LLAMA_SRC}")

# Platform detection for optimizations
if(CMAKE_SYSTEM_PROCESSOR MATCHES "arm64|aarch64")
    set(GGML_SYSTEM_ARCH "ARM")
    set(GGML_CPU_KLEIDIAI ON)
elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "x86_64|AMD64")
    set(GGML_SYSTEM_ARCH "x86")
    set(GGML_CPU_KLEIDIAI OFF)
endif()

# Add the llama.cpp sub-project
add_subdirectory(${LLAMA_SRC} build-llama)

# Create the shared library for JNI
add_library(llama-jni SHARED llama_jni.cpp)

# Compile definitions
target_compile_definitions(llama-jni PRIVATE
    GGML_SYSTEM_ARCH=${GGML_SYSTEM_ARCH}
    GGML_CPU_KLEIDIAI=$<BOOL:${GGML_CPU_KLEIDIAI}>
)

# Include directories
target_include_directories(llama-jni PRIVATE
    ${JNI_INCLUDE_DIRS}
    ${LLAMA_SRC}
    ${LLAMA_SRC}/common
    ${LLAMA_SRC}/include
    ${LLAMA_SRC}/ggml/include
)

# Link against llama and common libraries
target_link_libraries(llama-jni PRIVATE 
    llama 
    common
    ${CMAKE_DL_LIBS}
    pthread
)

# Set output name to match System.loadLibrary("llama-jni")
set_target_properties(llama-jni PROPERTIES 
    OUTPUT_NAME "llama-jni"
    PREFIX "lib"
)
