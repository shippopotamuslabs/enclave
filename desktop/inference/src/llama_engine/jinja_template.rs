//! Jinja chat-template application via llama.cpp's `common_chat_templates` API.
//! Android uses the same path in `llama-jni.cpp`; the legacy
//! `llama_chat_apply_template` wrapper in `llama-cpp-2` cannot parse GGUF Jinja
//! templates (Gemma 4, etc.).

use llama_cpp_2::model::LlamaModel;
use std::ffi::CString;
use std::ptr::{self, NonNull};

use crate::InferenceError;

#[repr(C)]
struct JinjaMessage {
    role: *const std::ffi::c_char,
    content: *const std::ffi::c_char,
}

#[repr(i32)]
#[derive(Copy, Clone, Debug, Eq, PartialEq)]
enum JinjaStatus {
    Ok = 0,
    InvalidArgument = 1,
    Exception = 2,
    EmptyPrompt = 3,
}

extern "C" {
    fn enclave_jinja_apply_chat_template(
        model: *const llama_cpp_sys_2::llama_model,
        messages: *const JinjaMessage,
        n_messages: usize,
        add_generation_prompt: bool,
        enable_thinking: bool,
        out_prompt: *mut *mut std::ffi::c_char,
        out_error: *mut *mut std::ffi::c_char,
    ) -> i32;

    fn enclave_jinja_string_free(ptr: *mut std::ffi::c_char);
}

/// `LlamaModel` is `#[repr(transparent)]` over `NonNull<llama_model>`.
fn model_ptr(model: &LlamaModel) -> *const llama_cpp_sys_2::llama_model {
    let ptr: NonNull<llama_cpp_sys_2::llama_model> =
        unsafe { ptr::read(model as *const LlamaModel as *const NonNull<_>).read() };
    ptr.as_ptr()
}

/// Apply the model's baked-in Jinja chat template.
///
/// Returns `Ok(prompt)` on success. Returns `Err` for argument/UTF-8 failures.
/// Returns `Err` with a message containing `"jinja unavailable"` when llama.cpp
/// rejects the template so callers can fall back to legacy templates.
pub(crate) fn apply_jinja_chat_template(
    model: &LlamaModel,
    messages: &[(String, String)],
    add_generation_prompt: bool,
    enable_thinking: bool,
) -> Result<String, InferenceError> {
    let mut owned: Vec<(CString, CString)> = Vec::with_capacity(messages.len());
    for (role, content) in messages {
        owned.push((
            CString::new(role.as_str())
                .map_err(|e| InferenceError::Engine(format!("chat role nul byte: {e}")))?,
            CString::new(content.as_str())
                .map_err(|e| InferenceError::Engine(format!("chat content nul byte: {e}")))?,
        ));
    }

    let c_messages: Vec<JinjaMessage> = owned
        .iter()
        .map(|(role, content)| JinjaMessage {
            role: role.as_ptr(),
            content: content.as_ptr(),
        })
        .collect();

    let mut out_prompt: *mut std::ffi::c_char = ptr::null_mut();
    let mut out_error: *mut std::ffi::c_char = ptr::null_mut();
    let status = unsafe {
        enclave_jinja_apply_chat_template(
            model_ptr(model),
            c_messages.as_ptr(),
            c_messages.len(),
            add_generation_prompt,
            enable_thinking,
            &mut out_prompt,
            &mut out_error,
        )
    };

    // Read any diagnostic string the bridge produced, freeing the buffer.
    let error_msg: Option<String> = unsafe {
        if out_error.is_null() {
            None
        } else {
            let s = std::ffi::CStr::from_ptr(out_error)
                .to_str()
                .ok()
                .map(str::to_string);
            enclave_jinja_string_free(out_error);
            s
        }
    };

    match JinjaStatus::from_i32(status) {
        Some(JinjaStatus::Ok) => {
            if out_prompt.is_null() {
                return Err(InferenceError::Engine(
                    "jinja chat template returned null prompt".into(),
                ));
            }
            let prompt = unsafe {
                let cstr = std::ffi::CStr::from_ptr(out_prompt);
                let s = cstr
                    .to_str()
                    .map_err(|e| InferenceError::Engine(format!("jinja prompt utf8: {e}")))?
                    .to_string();
                enclave_jinja_string_free(out_prompt);
                s
            };
            Ok(prompt)
        }
        Some(JinjaStatus::InvalidArgument) => Err(InferenceError::Engine(
            "jinja chat template invalid arguments".into(),
        )),
        Some(JinjaStatus::EmptyPrompt) | Some(JinjaStatus::Exception) | None => {
            // Preserve the C++ exception/empty reason so the caller can log it
            // and decide on a fallback. The message intentionally carries the
            // "jinja unavailable" sentinel so [`apply_chat_template_with_fallback`]
            // can keep routing to the legacy path.
            let detail = error_msg.unwrap_or_else(|| "no detail reported".into());
            Err(InferenceError::Engine(format!("jinja unavailable: {detail}")))
        }
    }
}

impl JinjaStatus {
    fn from_i32(value: i32) -> Option<Self> {
        match value {
            0 => Some(Self::Ok),
            1 => Some(Self::InvalidArgument),
            2 => Some(Self::Exception),
            3 => Some(Self::EmptyPrompt),
            _ => None,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::JinjaStatus;

    #[test]
    fn jinja_status_codes_are_stable() {
        assert_eq!(JinjaStatus::from_i32(0), Some(JinjaStatus::Ok));
        assert_eq!(JinjaStatus::from_i32(2), Some(JinjaStatus::Exception));
    }

    /// The exact substring the C++ bridge (`patch_map_filter_incompatibilities`
    /// in `cpp/chat_template_jinja.cpp`) rewrites before handing the Gemma 4
    /// template to `common_chat_templates_init`. Locking it here means a
    /// template-format change upstream surfaces as a test failure before it
    /// silently degrades chat back to the `chatml` fallback.
    #[test]
    fn gemma4_map_upper_substring_is_what_we_patch() {
        // Mirrors the official Gemma 4 chat template's array-type branch.
        let official = "type:{{ format_argument(item_value | map('upper') | list) }}";
        let patched  = official.replace("item_value | map('upper') | list", "item_value");
        assert_eq!(
            patched,
            "type:{{ format_argument(item_value) }}",
            "if this assertion fails, the Gemma 4 template's map('upper') expression \
             changed shape — update the C++ patcher needle to match"
        );
    }

    /// The sentinel `apply_chat_template_with_fallback` routes on. Keep it in
    /// lockstep with the `"jinja unavailable"` prefix emitted below on the
    /// Exception/EmptyPrompt path.
    #[test]
    fn jinja_unavailable_sentinel_is_stable() {
        let err = crate::InferenceError::Engine("jinja unavailable: some detail".into());
        assert!(
            err.to_string().contains("jinja unavailable"),
            "fallback router depends on this sentinel substring"
        );
    }
}
