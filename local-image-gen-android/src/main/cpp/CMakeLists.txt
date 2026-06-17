cmake_minimum_required(VERSION 3.22.1)
project(local_image_gen_jni)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(NOT DEFINED QNN_SDK_ROOT)
    if(DEFINED ENV{QNN_SDK_ROOT})
        set(QNN_SDK_ROOT $ENV{QNN_SDK_ROOT})
    else()
        set(LOCAL_PROPERTIES "${CMAKE_CURRENT_SOURCE_DIR}/../../../../local.properties")
        if(EXISTS "${LOCAL_PROPERTIES}")
            file(STRINGS "${LOCAL_PROPERTIES}" QNN_SDK_LINE REGEX "^qnn\\.sdk\\.dir=")
            if(QNN_SDK_LINE)
                string(REPLACE "qnn.sdk.dir=" "" QNN_SDK_ROOT "${QNN_SDK_LINE}")
            endif()
        endif()
    endif()
endif()
if(NOT DEFINED QNN_SDK_ROOT OR "${QNN_SDK_ROOT}" STREQUAL "")
    set(QNN_SDK_ROOT "/opt/qcom/aistack/qairt/2.47.0.260601")
endif()

add_subdirectory(engine)

add_library(local_image_gen_jni SHARED
    local_image_gen_jni.cpp
    local_image_gen_runtime.cpp
    qnn_bridge.cpp
    mnn_bridge.cpp
)

find_library(log-lib log)

target_include_directories(local_image_gen_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/thirdparty
    ${CMAKE_CURRENT_SOURCE_DIR}/engine/include
)

target_link_libraries(local_image_gen_jni
    enclave_diffusion_engine
    ${log-lib}
    dl
)

target_compile_definitions(local_image_gen_jni PRIVATE
    LOCAL_IMAGE_GEN_JNI_VERSION=2
)
