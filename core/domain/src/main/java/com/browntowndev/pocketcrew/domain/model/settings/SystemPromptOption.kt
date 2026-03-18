package com.browntowndev.pocketcrew.domain.model.settings

enum class SystemPromptOption(val displayName: String, val stubPrompt: String) {
    CUSTOM("Custom", ""),
    CONCISE("Concise", "You are a concise assistant. Provide short, direct answers without fluff."),
    FORMAL("Formal", "You are a formal assistant. Use professional language and proper etiquette."),
    RIGOROUS("Rigorous", "You are a rigorous assistant. Provide highly detailed, technical, and accurate explanations.")
}
