#include "chat_template_jinja.h"

#include "common/chat.h"
#include "llama.h"

#include <cstdlib>
#include <cstring>
#include <exception>
#include <string>
#include <vector>

namespace {

void string_replace_all(std::string & haystack, const std::string & needle, const std::string & replacement) {
    if (needle.empty()) {
        return;
    }
    size_t pos = 0;
    while ((pos = haystack.find(needle, pos)) != std::string::npos) {
        haystack.replace(pos, needle.size(), replacement);
        pos += replacement.size();
    }
}

// Work around upstream llama.cpp#21547: the bundled minijinja implements only
// `map(attribute=...)`, not `map('filter')`. The official Gemma 4 chat template
// (baked into the GGUF) uses `item_value | map('upper') | list` to uppercase an
// array of JSON-schema type names. When minijinja evaluates that node it throws
// `not_implemented_exception("map: filter-mapping not implemented")`, which
// forces the desktop engine onto a degraded `chatml` fallback.
//
// We patch the template string in-place before handing it to
// `common_chat_templates_init` as an explicit override. The replacement drops
// the `| map('upper') | list` and lets the template's own `format_argument`
// macro render the array directly. The only semantic loss is that multi-type
// schema fields (e.g. `["string", "null"]`) render lowercased — JSON schema
// type names are case-insensitive, so this does not affect tool-call validity.
//
// This is deliberately narrow: only the exact `map('upper')` expression is
// rewritten, and only when present. Non-Gemma templates pass through untouched.
// Remove this workaround once a llama.cpp release with the `map` filter fix
// lands in `llama-cpp-sys-2` and is bumped here.
std::string patch_map_filter_incompatibilities(const std::string & tmpl) {
    const std::string needle = "item_value | map('upper') | list";
    if (tmpl.find(needle) == std::string::npos) {
        return tmpl;
    }
    std::string patched = tmpl;
    string_replace_all(patched, needle, "item_value");
    return patched;
}

} // namespace

extern "C" {

enum enclave_jinja_status enclave_jinja_apply_chat_template(
    const llama_model * model,
    const enclave_jinja_message * messages,
    size_t n_messages,
    bool add_generation_prompt,
    bool enable_thinking,
    char ** out_prompt,
    char ** out_error) {
    if (model == nullptr || messages == nullptr || out_prompt == nullptr) {
        return ENCLAVE_JINJA_INVALID_ARGUMENT;
    }

    *out_prompt = nullptr;
    if (out_error != nullptr) {
        *out_error = nullptr;
    }

    auto set_error = [&](const char * msg) {
        if (out_error == nullptr) {
            return;
        }
        const size_t len = std::strlen(msg);
        *out_error = static_cast<char *>(std::malloc(len + 1));
        if (*out_error != nullptr) {
            std::memcpy(*out_error, msg, len + 1);
        }
    };

    try {
        common_chat_templates_inputs inputs;
        inputs.add_generation_prompt = add_generation_prompt;
        inputs.use_jinja = true;
        inputs.enable_thinking = enable_thinking;
        inputs.messages.reserve(n_messages);

        for (size_t i = 0; i < n_messages; ++i) {
            common_chat_msg msg;
            msg.role = messages[i].role != nullptr ? messages[i].role : "";
            msg.content = messages[i].content != nullptr ? messages[i].content : "";
            inputs.messages.push_back(std::move(msg));
        }

        // Fetch the model's default template, apply minijinja-compat patches,
        // and pass the result as an explicit override so `common_chat_templates`
        // does not re-read (and re-break on) the raw GGUF template.
        std::string tmpl_override;
        if (const char * raw = llama_model_chat_template(model, /* name */ nullptr)) {
            tmpl_override = patch_map_filter_incompatibilities(raw);
        }

        auto tmpls = common_chat_templates_init(model, tmpl_override);
        const common_chat_params params = common_chat_templates_apply(tmpls.get(), inputs);
        if (params.prompt.empty()) {
            set_error("common_chat_templates_apply returned an empty prompt");
            return ENCLAVE_JINJA_EMPTY_PROMPT;
        }

        *out_prompt = static_cast<char *>(std::malloc(params.prompt.size() + 1));
        if (*out_prompt == nullptr) {
            set_error("out of memory allocating prompt buffer");
            return ENCLAVE_JINJA_EXCEPTION;
        }
        std::memcpy(*out_prompt, params.prompt.c_str(), params.prompt.size() + 1);
        return ENCLAVE_JINJA_OK;
    } catch (const std::exception & e) {
        set_error(e.what());
        return ENCLAVE_JINJA_EXCEPTION;
    } catch (...) {
        set_error("unknown C++ exception during chat template apply");
        return ENCLAVE_JINJA_EXCEPTION;
    }
}

void enclave_jinja_string_free(char * ptr) {
    std::free(ptr);
}

} // extern "C"
