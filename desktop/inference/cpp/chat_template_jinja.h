#pragma once

#include <stdbool.h>
#include <stddef.h>

struct llama_model;

#ifdef __cplusplus
extern "C" {
#endif

enum enclave_jinja_status {
    ENCLAVE_JINJA_OK = 0,
    ENCLAVE_JINJA_INVALID_ARGUMENT = 1,
    ENCLAVE_JINJA_EXCEPTION = 2,
    ENCLAVE_JINJA_EMPTY_PROMPT = 3,
};

struct enclave_jinja_message {
    const char * role;
    const char * content;
};

/// Apply the model's GGUF Jinja chat template via llama.cpp `common_chat_templates`.
/// Mirrors Android's `common_chat_templates_apply` path in `llama-jni.cpp`.
///
/// On failure with ENCLAVE_JINJA_EXCEPTION or ENCLAVE_JINJA_EMPTY_PROMPT, a
/// heap-allocated C string describing the failure is written to `*out_error`
/// (when `out_error` is non-null). Free it with `enclave_jinja_string_free`.
enum enclave_jinja_status enclave_jinja_apply_chat_template(
    const struct llama_model * model,
    const struct enclave_jinja_message * messages,
    size_t n_messages,
    bool add_generation_prompt,
    bool enable_thinking,
    char ** out_prompt,
    char ** out_error);

void enclave_jinja_string_free(char * ptr);

#ifdef __cplusplus
}
#endif
