cmake_minimum_required(VERSION 3.22.1)

set(ENGINE_DIR ${CMAKE_CURRENT_SOURCE_DIR})
set(ENGINE_THIRDPARTY ${ENGINE_DIR}/thirdparty)
set(QNN_DEFAULT_ROOT /opt/qcom/aistack/qairt/2.47.0.260601)

if(NOT DEFINED QNN_SDK_ROOT AND EXISTS "${QNN_DEFAULT_ROOT}")
    set(QNN_SDK_ROOT "${QNN_DEFAULT_ROOT}" CACHE PATH "QAIRT/QNN SDK root")
endif()

option(ENCLAVE_DIFFUSION_FETCH_DEPS "Fetch native dependency submodules at configure time" OFF)
option(ENCLAVE_DIFFUSION_USE_QNN "Link real QNN graph execution" ON)
option(ENCLAVE_DIFFUSION_USE_MNN "Link real MNN CLIP inference" ON)

if(ENCLAVE_DIFFUSION_FETCH_DEPS)
    include(FetchContent)
    FetchContent_Declare(
        MNN
        GIT_REPOSITORY https://github.com/alibaba/MNN.git
        GIT_TAG master
        SOURCE_DIR ${ENGINE_THIRDPARTY}/MNN
    )
    FetchContent_Declare(
        tokenizers_cpp
        GIT_REPOSITORY https://github.com/mlc-ai/tokenizers-cpp.git
        GIT_TAG main
        SOURCE_DIR ${ENGINE_THIRDPARTY}/tokenizers-cpp
    )
    FetchContent_Declare(
        xtensor
        GIT_REPOSITORY https://github.com/xtensor-stack/xtensor.git
        GIT_TAG master
        SOURCE_DIR ${ENGINE_THIRDPARTY}/xtensor
    )
    FetchContent_MakeAvailable(MNN tokenizers_cpp xtensor)
else()
    message(STATUS "ENCLAVE_DIFFUSION_FETCH_DEPS=OFF; using checked-out dependency dirs when present.")
endif()

set(MNN_BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(MNN_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(MNN_LOW_MEMORY ON CACHE BOOL "" FORCE)
set(MNN_CPU_WEIGHT_DEQUANT_GEMM ON CACHE BOOL "" FORCE)
if(ANDROID)
    set(MNN_BUILD_FOR_ANDROID ON CACHE BOOL "" FORCE)
    set(MNN_BUILD_FOR_ANDROID_COMMAND ON CACHE BOOL "" FORCE)
    set(MNN_USE_LOGCAT ON CACHE BOOL "" FORCE)
else()
    set(MNN_BUILD_FOR_ANDROID OFF CACHE BOOL "" FORCE)
    set(MNN_BUILD_FOR_ANDROID_COMMAND OFF CACHE BOOL "" FORCE)
    set(MNN_USE_LOGCAT OFF CACHE BOOL "" FORCE)
endif()
set(MNN_OPENCL OFF CACHE BOOL "" FORCE)
set(MNN_SEP_BUILD OFF CACHE BOOL "" FORCE)

if(ENCLAVE_DIFFUSION_USE_MNN AND EXISTS "${ENGINE_THIRDPARTY}/MNN/CMakeLists.txt")
    add_subdirectory(${ENGINE_THIRDPARTY}/MNN ${CMAKE_BINARY_DIR}/mnn EXCLUDE_FROM_ALL)
else()
    message(WARNING "MNN not enabled or missing; CLIP runner will return unavailable.")
endif()

if(EXISTS "${ENGINE_THIRDPARTY}/tokenizers-cpp/CMakeLists.txt" AND
   EXISTS "${ENGINE_THIRDPARTY}/tokenizers-cpp/sentencepiece/CMakeLists.txt")
    add_subdirectory(${ENGINE_THIRDPARTY}/tokenizers-cpp ${CMAKE_BINARY_DIR}/tokenizers EXCLUDE_FROM_ALL)
else()
    message(WARNING "tokenizers-cpp missing; tokenizer runner will use hash fallback.")
endif()

set(ENCLAVE_DIFFUSION_HAS_QNN OFF)
if(ENCLAVE_DIFFUSION_USE_QNN AND DEFINED QNN_SDK_ROOT AND EXISTS "${QNN_SDK_ROOT}")
    if(EXISTS "${QNN_SDK_ROOT}/include/QNN/QnnInterface.h")
        set(ENCLAVE_DIFFUSION_HAS_QNN ON)
    else()
        message(WARNING "QNN headers not found under ${QNN_SDK_ROOT}; QNN disabled.")
    endif()
else()
    message(WARNING "QNN_SDK_ROOT not set or QNN disabled; graph runner will return unavailable.")
endif()

set(ENGINE_SOURCES
    ${ENGINE_DIR}/enclave_diffusion_engine.cpp
    ${ENGINE_DIR}/memory/stage_loader.cpp
    ${ENGINE_DIR}/memory/memory_policy.cpp
    ${ENGINE_DIR}/model_layout/pack_manifest.cpp
    ${ENGINE_DIR}/schedulers/dpm_multistep.cpp
    ${ENGINE_DIR}/schedulers/lcm.cpp
    ${ENGINE_DIR}/schedulers/scheduler_factory.cpp
    ${ENGINE_DIR}/pipeline/diffusion_pipeline.cpp
    ${ENGINE_DIR}/pipeline/latent_init.cpp
    ${ENGINE_DIR}/pipeline/image_io.cpp
    ${ENGINE_DIR}/runners/clip_prompt_utils.cpp
    ${ENGINE_DIR}/runners/tokenizer_runner.cpp
    ${ENGINE_DIR}/runners/mnn_clip_runner.cpp
    ${ENGINE_DIR}/runners/qnn_graph_runner.cpp
)

if(ENCLAVE_DIFFUSION_HAS_QNN)
    list(APPEND ENGINE_SOURCES
        ${ENGINE_DIR}/qnn/qnn_fastrpc.cpp
        ${ENGINE_DIR}/qnn/qnn_runtime.cpp
        ${ENGINE_DIR}/qnn/qnn_graph_info.cpp
        ${ENGINE_DIR}/qnn/qnn_tensor_io.cpp
        ${ENGINE_DIR}/qnn/enclave_qnn_model.cpp
        ${ENGINE_DIR}/qnn/enclave_qnn_app.cpp
    )
endif()

add_library(enclave_diffusion_engine STATIC ${ENGINE_SOURCES})

target_include_directories(enclave_diffusion_engine PUBLIC
    ${ENGINE_DIR}/include
    ${ENGINE_DIR}
    ${ENGINE_DIR}/..
)

if(EXISTS "${ENGINE_THIRDPARTY}/MNN/include")
    target_include_directories(enclave_diffusion_engine PRIVATE ${ENGINE_THIRDPARTY}/MNN/include)
endif()

if(EXISTS "${ENGINE_THIRDPARTY}/tokenizers-cpp/include")
    target_include_directories(enclave_diffusion_engine PRIVATE ${ENGINE_THIRDPARTY}/tokenizers-cpp/include)
endif()

if(EXISTS "${ENGINE_THIRDPARTY}/xtensor/include")
    target_include_directories(enclave_diffusion_engine PRIVATE
        ${ENGINE_THIRDPARTY}/xtensor/include
        ${ENGINE_THIRDPARTY}/xtl/include
    )
    target_compile_definitions(enclave_diffusion_engine PRIVATE XTENSOR_USE_XSIMD=0)
endif()

if(EXISTS "${ENGINE_THIRDPARTY}/stb")
    target_include_directories(enclave_diffusion_engine PRIVATE ${ENGINE_THIRDPARTY}/stb)
endif()

if(ENCLAVE_DIFFUSION_HAS_QNN)
    target_include_directories(enclave_diffusion_engine PRIVATE
        ${QNN_SDK_ROOT}/include
        ${QNN_SDK_ROOT}/include/QNN
    )
endif()

target_compile_definitions(enclave_diffusion_engine PRIVATE ENCLAVE_DIFFUSION_ENGINE=1)

if(TARGET MNN)
    target_compile_definitions(enclave_diffusion_engine PRIVATE ENCLAVE_DIFFUSION_HAS_MNN=1)
    target_link_libraries(enclave_diffusion_engine PRIVATE MNN)
endif()

if(TARGET tokenizers_cpp)
    target_compile_definitions(enclave_diffusion_engine PRIVATE ENCLAVE_DIFFUSION_HAS_TOKENIZERS_CPP=1)
    target_link_libraries(enclave_diffusion_engine PRIVATE tokenizers_cpp)
endif()

if(ENCLAVE_DIFFUSION_HAS_QNN)
    target_compile_definitions(enclave_diffusion_engine PRIVATE ENCLAVE_DIFFUSION_HAS_QNN=1)
endif()

if(ANDROID)
    target_link_libraries(enclave_diffusion_engine PUBLIC dl log)
else()
    target_link_libraries(enclave_diffusion_engine PUBLIC dl pthread)
endif()
