cmake_minimum_required(VERSION 3.22.1)
project(local_image_gen_jni)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(local_image_gen_jni SHARED
    local_image_gen_jni.cpp
    local_image_gen_runtime.cpp
    local_image_gen_backend_client.cpp
    qnn_bridge.cpp
    mnn_bridge.cpp
)

find_library(log-lib log)

target_include_directories(local_image_gen_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/thirdparty
)

target_link_libraries(local_image_gen_jni
    ${log-lib}
    dl
)

target_compile_definitions(local_image_gen_jni PRIVATE
    LOCAL_IMAGE_GEN_JNI_VERSION=1
)