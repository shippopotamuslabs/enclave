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

# CPU-only mode - disable all GPU backends for stability
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(GGML_OPENCL OFF CACHE BOOL "" FORCE)
set(GGML_CUDA OFF CACHE BOOL "" FORCE)
set(GGML_METAL OFF CACHE BOOL "" FORCE)

# =============================================================================
# KleidiAI Integration - CPU Backend Configuration
# =============================================================================
# Configure SVE (Scalable Vector Extensions) based on target:
#   - Pixel/Tensor G3: SVE OFF (hardware bug causes crashes)
#   - Samsung/Snapdragon: SVE ON (optimal performance)
# This variable is passed from Gradle via -DGGML_CPU_ARM_ENABLE_SVE

# Default to OFF if not specified (NEON fallback)
if(NOT DEFINED GGML_CPU_ARM_ENABLE_SVE)
    set(GGML_CPU_ARM_ENABLE_SVE OFF)
endif()

# Allow Gradle to override SVE setting per flavor
set(GGML_CPU_ARM_ENABLE_SVE ${GGML_CPU_ARM_ENABLE_SVE} CACHE BOOL "" FORCE)

# =============================================================================
# KleidiAI Micro-Kernels Configuration
# =============================================================================
# Enable KleidiAI for optimized ARM compute kernels
# Ref: https://github.com/ggerganov/llama.cpp/tree/master/ggml/src/ggml-cpu/clever/kleidi
# Disable OpenMP - it causes crashes on Android with custom threadpools
# and llama.cpp's internal threading works fine without it
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)

set(GGML_CPU_KLEIDIAI ON CACHE BOOL "" FORCE)

# Vendor KleidiAI locally to avoid FetchContent network issues
# IMPORTANT: Variable name must match FetchContent content name exactly (KleidiAI_Download -> KLEIDIAI_DOWNLOAD)
set(FETCHCONTENT_SOURCE_DIR_KLEIDIAI_DOWNLOAD
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/kleidiai-1.22.0
    CACHE PATH "" FORCE)

# Disable llamafile
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)

# =============================================================================
# ARM CPU Optimization
# =============================================================================
# Enable ARMv8.2-a features for better performance on Snapdragon 888
# dotprod = UDOT/SDOT instructions for faster matrix ops
# fp16 = half-precision float for better throughput
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -march=armv8.2-a+dotprod+fp16")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv8.2-a+dotprod+fp16")

# Log the configuration for debugging
message(STATUS "CPU Backend Configuration:")
message(STATUS "  GGML_CPU_ARM_ENABLE_SVE: ${GGML_CPU_ARM_ENABLE_SVE}")
message(STATUS "  GGML_OPENMP: ${GGML_OPENMP}")
message(STATUS "  GGML_CPU_KLEIDIAI: ${GGML_CPU_KLEIDIAI}")
message(STATUS "  GGML_LLAMAFILE: ${GGML_LLAMAFILE}")
message(STATUS "  CMAKE_C_FLAGS: ${CMAKE_C_FLAGS}")

# Source directory for llama.cpp - relative path from this CMakeLists.txt to root
set(LLAMA_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/llama.cpp)

find_package(Threads REQUIRED)

# =============================================================================
# DUAL LIBRARY BUILD STRATEGY
# =============================================================================
# Since llama.cpp uses global compile flags, we cannot easily build both variants
# in a single CMake pass. Instead, we use a two-phase approach:
#
# Phase 1: Build NEON variant first (SVE disabled) - this is our fallback
# Phase 2: Build SVE variant - using the same objects but different compile flags
#
# For the Fat APK, we output to the build directory to avoid conflicts with jniLibs source sets.
# The runtime detection in JniLlamaEngine.kt will choose the appropriate library.

# Build the primary (NEON) variant
set(NEON_BUILD_DIR ${CMAKE_BINARY_DIR}/neon)
# Output to cmake build directory - jniLibs are provided by the build script
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/cmake-output/${CMAKE_ANDROID_ARCH_ABI})

# Add llama.cpp subdirectory - builds with current SVE settings
# By default, we build with SVE disabled for maximum compatibility
if(NOT GGML_CPU_ARM_ENABLE_SVE)
    message(STATUS "Building NEON variant (SVE disabled)")
    add_subdirectory(${LLAMA_DIR} llama-build)
else()
    message(STATUS "Building SVE variant (SVE enabled)")
    add_subdirectory(${LLAMA_DIR} llama-build)
endif()

# =============================================================================
# CPU DETECTOR LIBRARY (Tiny, no llama.cpp dependency)
# =============================================================================
# This library is loaded FIRST to detect SVE before loading the main llama library
# Compiled without any SVE flags to ensure it works on all devices
add_library(cpudetect SHARED
    cpudetect.cpp
)

target_link_libraries(cpudetect
    log
    android
)

# =============================================================================
# MAIN LLAMA JNI LIBRARY
# =============================================================================
# Define JNI library - links to whatever llama.cpp variant was built
add_library(llama-jni SHARED
    llama-jni.cpp
)

add_library(mtmd STATIC
    ${LLAMA_DIR}/tools/mtmd/mtmd.cpp
    ${LLAMA_DIR}/tools/mtmd/mtmd-audio.cpp
    ${LLAMA_DIR}/tools/mtmd/mtmd-helper.cpp
    ${LLAMA_DIR}/tools/mtmd/clip.cpp
    ${LLAMA_DIR}/tools/mtmd/models/cogvlm.cpp
    ${LLAMA_DIR}/tools/mtmd/models/conformer.cpp
    ${LLAMA_DIR}/tools/mtmd/models/glm4v.cpp
    ${LLAMA_DIR}/tools/mtmd/models/internvl.cpp
    ${LLAMA_DIR}/tools/mtmd/models/kimivl.cpp
    ${LLAMA_DIR}/tools/mtmd/models/kimik25.cpp
    ${LLAMA_DIR}/tools/mtmd/models/nemotron-v2-vl.cpp
    ${LLAMA_DIR}/tools/mtmd/models/llama4.cpp
    ${LLAMA_DIR}/tools/mtmd/models/llava.cpp
    ${LLAMA_DIR}/tools/mtmd/models/minicpmv.cpp
    ${LLAMA_DIR}/tools/mtmd/models/paddleocr.cpp
    ${LLAMA_DIR}/tools/mtmd/models/pixtral.cpp
    ${LLAMA_DIR}/tools/mtmd/models/qwen2vl.cpp
    ${LLAMA_DIR}/tools/mtmd/models/qwen3vl.cpp
    ${LLAMA_DIR}/tools/mtmd/models/siglip.cpp
    ${LLAMA_DIR}/tools/mtmd/models/whisper-enc.cpp
    ${LLAMA_DIR}/tools/mtmd/models/mobilenetv5.cpp
    ${LLAMA_DIR}/tools/mtmd/models/youtuvl.cpp
)

# Include directories
target_include_directories(mtmd PUBLIC
    ${LLAMA_DIR}/tools/mtmd
)
target_include_directories(mtmd PRIVATE
    ${LLAMA_DIR}
    ${LLAMA_DIR}/vendor
)

target_include_directories(llama-jni PRIVATE
    ${LLAMA_DIR}/src
    ${LLAMA_DIR}/include
    ${LLAMA_DIR}/common
    ${LLAMA_DIR}/ggml/include
    ${LLAMA_DIR}/tools/mtmd
)

# Link libraries - CPU only
target_link_libraries(mtmd
    PUBLIC
    ggml
    llama
    PRIVATE
    Threads::Threads
)

target_link_libraries(llama-jni
    llama
    ggml
    ggml-cpu
    mtmd
    log
    android
    dl
)

# Compiler options for optimization
set_target_properties(llama-jni PROPERTIES
    COMPILE_OPTIONS "-O3;-fPIC;-ffunction-sections;-fdata-sections;-march=armv8.2-a+dotprod+fp16"
    LINK_OPTIONS "-Wl,--gc-sections"
)

# =============================================================================
# SECONDARY VARIANT BUILD (for Fat APK)
# =============================================================================
# For the Fat APK to include both SVE and NEON libraries, we need a second build.
# This is handled by Gradle flavors in build.gradle.kts which invoke CMake twice:
#   - Once with -DGGML_CPU_ARM_ENABLE_SVE=OFF (produces llama-jni-neon)
#   - Once with -DGGML_CPU_ARM_ENABLE_SVE=ON  (produces llama-jni-sve)
#
# The output names are controlled via CMAKE_SHARED_MODULE_NAME or target rename.
# Here we conditionally set the output name based on the SVE setting.

if(GGML_CPU_ARM_ENABLE_SVE)
    # SVE variant - rename output to llama-jni-sve
    set_target_properties(llama-jni PROPERTIES
        OUTPUT_NAME "llama-jni-sve"
    )
    message(STATUS "Final library output name: llama-jni-sve")
else()
    # NEON variant - rename output to llama-jni-neon
    set_target_properties(llama-jni PROPERTIES
        OUTPUT_NAME "llama-jni-neon"
    )
    message(STATUS "Final library output name: llama-jni-neon")
endif()
