package com.browntowndev.enclave.feature.inference.appendonly

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.feature.inference.AnthropicInferenceServiceImpl
import com.browntowndev.enclave.feature.inference.BaseOpenAiSdkInferenceService
import com.browntowndev.enclave.feature.inference.GoogleInferenceServiceImpl
import com.browntowndev.enclave.feature.inference.LlamaInferenceServiceImpl
import com.browntowndev.enclave.feature.inference.RustCompatibleInferenceService

internal fun RustCompatibleInferenceService.clearAppendOnlyOnRustCompactionBoundary(revisionAfter: Long? = null) {
    when (this) {
        is BaseOpenAiSdkInferenceService -> this.clearAppendOnlyOnRustCompactionBoundary()
        is AnthropicInferenceServiceImpl -> this.clearAppendOnlyOnRustCompactionBoundary(revisionAfter)
        is GoogleInferenceServiceImpl -> this.clearAppendOnlyOnRustCompactionBoundary(revisionAfter)
        is LlamaInferenceServiceImpl -> this.clearAppendOnlyOnRustCompactionBoundary(revisionAfter)
    }
}

/** Fresh append-only state for a new user prompt (see FEAT-append-only §1.4 session scoping). */
internal fun RustCompatibleInferenceService.resetAppendOnlyForNewPrompt(chatId: ChatId? = null) {
    when (this) {
        is BaseOpenAiSdkInferenceService -> this.resetAppendOnlyForNewPrompt(chatId)
        is AnthropicInferenceServiceImpl -> this.resetAppendOnlyForNewPrompt(chatId)
        is GoogleInferenceServiceImpl -> this.resetAppendOnlyForNewPrompt(chatId)
    }
}
