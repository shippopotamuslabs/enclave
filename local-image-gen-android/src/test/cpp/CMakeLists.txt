cmake_minimum_required(VERSION 3.22.1)
project(enclave_diffusion_host_tests CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

enable_testing()

set(ENGINE_CPP ${CMAKE_CURRENT_SOURCE_DIR}/../../main/cpp)

function(add_engine_test name)
    add_executable(${name} ${name}.cpp
        ${ENGINE_CPP}/engine/memory/stage_loader.cpp
        ${ENGINE_CPP}/engine/model_layout/pack_manifest.cpp
        ${ENGINE_CPP}/engine/schedulers/dpm_multistep.cpp
        ${ENGINE_CPP}/engine/schedulers/lcm.cpp
        ${ENGINE_CPP}/engine/schedulers/scheduler_factory.cpp
        ${ENGINE_CPP}/engine/pipeline/latent_init.cpp
    )
    target_include_directories(${name} PRIVATE
        ${ENGINE_CPP}
        ${ENGINE_CPP}/engine
        ${ENGINE_CPP}/engine/include
    )
    add_test(NAME ${name} COMMAND ${name})
endfunction()

add_engine_test(stage_order_test)
add_engine_test(scheduler_dpm_test)
add_engine_test(scheduler_lcm_test)
add_engine_test(latent_init_test)
add_engine_test(pack_manifest_test)

add_executable(generate_request_body_test generate_request_body_test.cpp)
target_include_directories(generate_request_body_test PRIVATE
    ${ENGINE_CPP}
)
add_test(NAME generate_request_body_test COMMAND generate_request_body_test)
