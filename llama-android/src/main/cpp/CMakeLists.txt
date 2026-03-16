cmake_minimum_required(VERSION 3.22.1)
project("llama-jni" C CXX)

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Set build type to Release for optimization
if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

# Android specific settings
set(CMAKE_ANDROID_STL_TYPE c++_shared)
set(CMAKE_ANDROID_NDK_DEPRECATED_HEADERS ON)

# Disable unnecessary llama.cpp targets BEFORE adding subdirectory
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)

# CPU-only mode - disable all GPU backends
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(GGML_OPENCL OFF CACHE BOOL "" FORCE)
set(GGML_CUDA OFF CACHE BOOL "" FORCE)
set(GGML_METAL OFF CACHE BOOL "" FORCE)

# CRITICAL: Disable SVE for ggml - this must be set BEFORE add_subdirectory
# Pixel 8 Pro / Tensor G3 crashes with SVE enabled due to incorrect vector length detection
set(GGML_CPU_ARM_ENABLE_SVE OFF CACHE BOOL "" FORCE)

# Source directory for llama.cpp - relative path from this CMakeLists.txt to root
set(LLAMA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/llama.cpp)

# Add llama.cpp as a subdirectory
add_subdirectory(${LLAMA_DIR} llama-build)

# Define our JNI library
add_library(llama-jni SHARED
    llama-jni.cpp
)

# Include directories
target_include_directories(llama-jni PRIVATE
    ${LLAMA_DIR}/src
    ${LLAMA_DIR}/include
    ${LLAMA_DIR}/common
    ${LLAMA_DIR}/ggml/include
)

# Link libraries - CPU only
target_link_libraries(llama-jni
    llama
    ggml
    ggml-cpu
    log
    android
)

# Compiler options for optimization
set_target_properties(llama-jni PROPERTIES
    COMPILE_OPTIONS "-O3;-fPIC;-ffunction-sections;-fdata-sections"
    LINK_OPTIONS "-Wl,--gc-sections"
)
