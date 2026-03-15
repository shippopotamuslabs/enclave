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

# Disable Vulkan - known to crash on Mali GPUs (Tensor G3)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)

# Keep OpenCL enabled for potential Qualcomm Adreno GPU support
# Note: On Mali GPUs (Pixel 8 Pro), OpenCL runs in CPU-only mode due to device detection issues
set(GGML_OPENCL ON CACHE BOOL "" FORCE)

# Disable other backends
set(GGML_CUDA OFF CACHE BOOL "" FORCE)
set(GGML_METAL OFF CACHE BOOL "" FORCE)

# CRITICAL: Disable SVE for ggml - this must be set BEFORE add_subdirectory
# Pixel 8 Pro / Tensor G3 crashes with SVE enabled due to incorrect vector length detection
set(GGML_CPU_ARM_ENABLE_SVE OFF CACHE BOOL "" FORCE)

# Configure Vulkan using external Khronos headers for NDK compatibility
# This must be set BEFORE add_subdirectory for llama.cpp
set(Vulkan_INCLUDE_DIR "/home/sean/Code/pocket-crew/third_party/Vulkan-Headers/include" CACHE PATH "Vulkan include directory")
set(Vulkan_LIBRARY "/home/sean/Android/Sdk/ndk/28.2.13676358/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/34/libvulkan.so" CACHE FILEPATH "Vulkan library")
set(Vulkan_FOUND TRUE CACHE BOOL "" FORCE)

# Configure OpenCL using external Khronos headers for NDK compatibility
set(OpenCL_INCLUDE_DIR "/home/sean/Code/pocket-crew/third_party/OpenCL-Headers" CACHE PATH "OpenCL include directory")
set(OpenCL_FOUND TRUE CACHE BOOL "" FORCE)

# Add OpenCL include directory globally so ggml-opencl can find CL/cl.h
include_directories(BEFORE "${OpenCL_INCLUDE_DIR}")

# Add custom OpenCL ICD Loader cmake modules to CMake module path
list(APPEND CMAKE_MODULE_PATH "/home/sean/Code/pocket-crew/third_party/OpenCL-ICD-Loader/cmake")

# Configure OpenCL ICD Loader - build as static library
set(OPENCL_ICD_LOADER_BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(OPENCL_ICD_LOADER_HEADERS_DIR "${OpenCL_INCLUDE_DIR}" CACHE PATH "OpenCL ICD Loader headers dir")
set(ENABLE_OPENCL_LAYERS OFF CACHE BOOL "" FORCE)
set(ENABLE_OPENCL_LOADER_MANAGED_DISPATCH OFF CACHE BOOL "" FORCE)

# Add vulkan-shaders-gen to program search path for shader compilation
list(APPEND CMAKE_PROGRAM_PATH "/home/sean/Code/pocket-crew/third_party/llama.cpp/build-host/bin")

# Build OpenCL ICD Loader as static library first
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/OpenCL-ICD-Loader opencl-icd-loader)

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

# Link libraries - ensure ggml-opencl links against OpenCL ICD Loader
target_link_libraries(ggml-opencl PRIVATE OpenCL)

target_link_libraries(llama-jni
    llama
    ggml
    ggml-opencl
    OpenCL
    log
    vulkan
    android
)

# Compiler options for optimization
set_target_properties(llama-jni PROPERTIES
    COMPILE_OPTIONS "-O3;-fPIC;-ffunction-sections;-fdata-sections"
    LINK_OPTIONS "-Wl,--gc-sections"
)
