cmake_minimum_required(VERSION 3.20)
project(llama-jni CXX C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED true)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED true)

if(NOT DEFINED LLAMA_SRC)
    message(FATAL_ERROR "LLAMA_SRC must be defined. Please provide the path to the llama.cpp source directory.")
endif()

message(STATUS "Using LLAMA_SRC: ${LLAMA_SRC}")

if(DEFINED ANDROID_ABI)
    message(STATUS "Detected Android ABI: ${ANDROID_ABI}")
    if(ANDROID_ABI STREQUAL "arm64-v8a")
        set(GGML_SYSTEM_ARCH "ARM")
        set(GGML_CPU_KLEIDIAI ON)
        set(GGML_OPENMP ON)
    elseif(ANDROID_ABI STREQUAL "x86_64")
        set(GGML_SYSTEM_ARCH "x86")
        set(GGML_CPU_KLEIDIAI OFF)
        set(GGML_OPENMP OFF)
    else()
        message(FATAL_ERROR "Unsupported ABI: ${ANDROID_ABI}")
    endif()
else()
    # Desktop build
    find_package(JNI REQUIRED)
endif()

# Add the llama.cpp sub-project
add_subdirectory(${LLAMA_SRC} build-llama)

# Create the shared library for JNI
add_library(llama-jni SHARED llama_jni.cpp)

if(DEFINED ANDROID_ABI)
    target_compile_definitions(llama-jni PRIVATE
            GGML_SYSTEM_ARCH=${GGML_SYSTEM_ARCH}
            GGML_CPU_KLEIDIAI=$<BOOL:${GGML_CPU_KLEIDIAI}>
            GGML_OPENMP=$<BOOL:${GGML_OPENMP}>
    )
endif()

target_include_directories(llama-jni PRIVATE
        ${LLAMA_SRC}
        ${LLAMA_SRC}/common
        ${LLAMA_SRC}/include
        ${LLAMA_SRC}/ggml/include
        ${LLAMA_SRC}/ggml/src
        ${LLAMA_SRC}/vendor
)

if(NOT DEFINED ANDROID_ABI)
    target_include_directories(llama-jni PRIVATE ${JNI_INCLUDE_DIRS})
    target_link_libraries(llama-jni PRIVATE llama common)
else()
    target_link_libraries(llama-jni PRIVATE llama common android log)
endif()

set_target_properties(llama-jni PROPERTIES OUTPUT_NAME "llama-jni")
