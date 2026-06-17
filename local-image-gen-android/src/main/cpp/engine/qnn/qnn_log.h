#pragma once

#ifdef __ANDROID__
#include <android/log.h>

#define ENCLAVE_QNN_LOGE(...) \
    __android_log_print(ANDROID_LOG_ERROR, ENCLAVE_QNN_LOG_TAG, __VA_ARGS__)
#define ENCLAVE_QNN_LOGW(...) \
    __android_log_print(ANDROID_LOG_WARN, ENCLAVE_QNN_LOG_TAG, __VA_ARGS__)
#define ENCLAVE_QNN_LOGI(...) \
    __android_log_print(ANDROID_LOG_INFO, ENCLAVE_QNN_LOG_TAG, __VA_ARGS__)
#else
#include <cstdio>

#define ENCLAVE_QNN_LOGE(...) \
    std::fprintf(stderr, "E/enclave-qnn: " __VA_ARGS__); \
    std::fprintf(stderr, "\n")
#define ENCLAVE_QNN_LOGW(...) \
    std::fprintf(stderr, "W/enclave-qnn: " __VA_ARGS__); \
    std::fprintf(stderr, "\n")
#define ENCLAVE_QNN_LOGI(...) \
    std::fprintf(stdout, "I/enclave-qnn: " __VA_ARGS__); \
    std::fprintf(stdout, "\n")
#endif

#define ENCLAVE_QNN_LOG_TAG "enclave-qnn"
