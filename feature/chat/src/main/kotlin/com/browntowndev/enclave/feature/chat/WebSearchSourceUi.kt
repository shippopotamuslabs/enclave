package com.browntowndev.enclave.feature.chat

import com.browntowndev.enclave.domain.model.chat.WebSearchSource

/** Presentation mirror of [WebSearchSource] so composables never take domain types (ENC-13). */
data class WebSearchSourceUi(
    val title: String,
    val url: String,
    val content: String,
    val extracted: Boolean,
)

fun WebSearchSource.toUi(): WebSearchSourceUi =
    WebSearchSourceUi(title = title, url = url, content = content, extracted = extracted)

fun List<WebSearchSource>.toUiList(): List<WebSearchSourceUi> = map { it.toUi() }
