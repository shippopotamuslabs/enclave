# Host-side native unit test for the `/generate` JSON request body builder.
#
# This is a standalone CMake project (NOT wired into the Android library build)
# so the pure body builder can be exercised on the host toolchain without the
# Android NDK. Run with:
#
#   cmake -S local-image-gen-android/src/test/cpp -B <build-dir>
#   cmake --build <build-dir>
#   ctest --test-dir <build-dir> --output-on-failure
#
# Covers scenarios INF-005 and MUT-004.
cmake_minimum_required(VERSION 3.22.1)
project(local_image_gen_native_tests CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

enable_testing()

add_executable(generate_request_body_test
    generate_request_body_test.cpp
)

# The production header lives in the main cpp source set; the body builder is an
# inline function there, so the production and test translation units share one
# implementation.
target_include_directories(generate_request_body_test PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/../../main/cpp
)

add_test(NAME generate_request_body_test COMMAND generate_request_body_test)
