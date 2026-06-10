package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.chat.Role
import com.openai.models.responses.EasyInputMessage
import com.openai.models.responses.ResponseInputItem

/**
 * Builds Responses API input items with role-appropriate content types.
 *
 * Assistant history must not use [ResponseInputItem.Message.addInputTextContent]; the API only
 * accepts `output_text`/`refusal` for assistant content parts. Plain-string EasyInputMessage
 * avoids that mismatch while preserving typed `input_text` for user/system turns.
 */
internal object ResponsesInputMessageSupport {
    fun textInputItem(
        role: Role,
        content: String,
    ): ResponseInputItem =
        when (role) {
            Role.ASSISTANT -> assistantEasyMessage(content)
            Role.USER -> typedUserOrSystemMessage(ResponseInputItem.Message.Role.of("user"), content)
            Role.SYSTEM -> typedUserOrSystemMessage(ResponseInputItem.Message.Role.of("system"), content)
        }

    private fun assistantEasyMessage(content: String): ResponseInputItem =
        ResponseInputItem.ofEasyInputMessage(
            EasyInputMessage
                .builder()
                .role(EasyInputMessage.Role.ASSISTANT)
                .content(content)
                .build(),
        )

    private fun typedUserOrSystemMessage(
        role: ResponseInputItem.Message.Role,
        content: String,
    ): ResponseInputItem =
        ResponseInputItem.ofMessage(
            ResponseInputItem.Message
                .builder()
                .role(role)
                .addInputTextContent(content)
                .build(),
        )
}
