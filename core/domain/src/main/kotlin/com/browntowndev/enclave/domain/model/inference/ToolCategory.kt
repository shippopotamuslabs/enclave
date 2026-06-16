package com.browntowndev.enclave.domain.model.inference

enum class ToolCategory(
    val tag: String,
) {
    MEMORY("memory"),
    WEB("web"),
    DISCOVERY("discovery"),
    CONTEXT("context"),
    ARTIFACT("artifact"),
    X("x"),
    VISION("vision"),
    MEDIA("media"),
    ;

    companion object {
        fun fromTag(tag: String): ToolCategory? = entries.find { it.tag == tag }
    }
}
