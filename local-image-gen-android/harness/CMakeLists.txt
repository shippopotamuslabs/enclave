cmake_minimum_required(VERSION 3.22.1)
project(enclave_diffusion_harness CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(ENGINE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../src/main/cpp/engine)
set(QNN_DEFAULT_ROOT /opt/qcom/aistack/qairt/2.47.0.260601)
if(NOT DEFINED QNN_SDK_ROOT AND EXISTS "${QNN_DEFAULT_ROOT}")
    set(QNN_SDK_ROOT "${QNN_DEFAULT_ROOT}" CACHE PATH "QAIRT/QNN SDK root")
endif()

add_subdirectory(${ENGINE_DIR} ${CMAKE_BINARY_DIR}/engine)

set(HARNESS_SOURCES
    main.cpp
    harness_config.cpp
    harness_probes.cpp
)

function(configure_harness_target target_name)
    target_include_directories(${target_name} PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}/../src/main/cpp
    )
    if(DEFINED QNN_SDK_ROOT AND EXISTS "${QNN_SDK_ROOT}/include/QNN/QnnInterface.h")
        target_include_directories(${target_name} PRIVATE
            ${QNN_SDK_ROOT}/include
            ${QNN_SDK_ROOT}/include/QNN
        )
    endif()
    target_link_libraries(${target_name} PRIVATE enclave_diffusion_engine)
    install(TARGETS ${target_name} RUNTIME DESTINATION bin)
endfunction()

if(ANDROID)
    add_executable(enclave-diffusion-cli ${HARNESS_SOURCES})
    configure_harness_target(enclave-diffusion-cli)
else()
    add_executable(enclave-diffusion-harness ${HARNESS_SOURCES})
    configure_harness_target(enclave-diffusion-harness)
endif()
