package com.browntowndev.pocketcrew.core.ui.component.markdown

import androidx.compose.runtime.Stable
import com.hrm.markdown.renderer.MarkdownTheme

/** Stable SAM interface so Compose can skip StreamableMarkdownText when inputs are equal. */
@Stable
fun interface MarkdownThemeFactory {
    fun create(): MarkdownTheme
}
