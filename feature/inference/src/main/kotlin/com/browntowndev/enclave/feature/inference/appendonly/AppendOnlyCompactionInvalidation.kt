package com.browntowndev.enclave.feature.inference.appendonly

import com.browntowndev.enclave.feature.inference.AnthropicInferenceServiceImpl
import com.browntowndev.enclave.feature.inference.BaseOpenAiSdkInferenceService
import com.browntowndev.enclave.feature.inference.GoogleInferenceServiceImpl
import com.browntowndev.enclave.feature.inference.LlamaInferenceServiceImpl
import com.browntowndev.enclave.feature.inference.RustCompatibleInferenceService

internal fun RustCompatibleInferenceService.clearAppendOnlyOnRustCompactionBoundary() {
    when (this) {
        is BaseOpenAiSdkInferenceService -> clearAppendOnlyOnRustCompactionBoundary()
        is AnthropicInferenceServiceImpl -> clearAppendOnlyOnRustCompactionBoundary()
        is GoogleInferenceServiceImpl -> clearAppendOnlyOnRustCompactionBoundary()
        is LlamaInferenceServiceImpl -> clearAppendOnlyOnRustCompactionBoundary()
    }
}

/** Fresh append-only state for a new user prompt (see FEAT-append-only §1.4 session scoping). */
internal fun RustCompatibleInferenceService.resetAppendOnlyForNewPrompt() {
    when (this) {
        is BaseOpenAiSdkInferenceService -> resetAppendOnlyForNewPrompt()
        is AnthropicInferenceServiceImpl -> resetAppendOnlyForNewPrompt()
        is GoogleInferenceServiceImpl -> resetAppendOnlyForNewPrompt()
    }
}
