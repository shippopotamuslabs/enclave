package com.browntowndev.enclave.domain.model.inference

data class ToolCatalogEntry(
    val name: String,
    val description: String,
    val disclosureMode: ToolDisclosureMode,
    val requiredArguments: List<ToolArgumentHint> = emptyList(),
    val optionalArguments: List<ToolArgumentHint> = emptyList(),
)

data class ToolArgumentHint(
    val name: String,
    val type: String,
    val repeated: Boolean,
)

enum class ToolDisclosureMode {
    PRELOADED,
    DISCLOSE_ON_DEMAND,
}
