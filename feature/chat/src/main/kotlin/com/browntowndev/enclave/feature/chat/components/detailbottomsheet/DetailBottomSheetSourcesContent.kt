package com.browntowndev.enclave.feature.chat.components.detailbottomsheet

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.component.neo.NeoSheetTabRow
import com.browntowndev.enclave.domain.model.chat.WebSearchSource
import com.browntowndev.enclave.feature.chat.components.WebSearchSourceRow
import java.net.URI

@Composable
internal fun DetailBottomSheetSourcesContent(
    sources: List<WebSearchSource>,
    onSourceClick: (String) -> Unit,
) {
    val webSources =
        remember(sources) {
            sources.filter { source ->
                val domain = runCatching { URI(source.url).host?.removePrefix("www.") }.getOrNull() ?: ""
                !domain.equals("x.com", ignoreCase = true) && !domain.equals("twitter.com", ignoreCase = true)
            }
        }
    val xSources =
        remember(sources) {
            sources.filter { source ->
                val domain = runCatching { URI(source.url).host?.removePrefix("www.") }.getOrNull() ?: ""
                domain.equals("x.com", ignoreCase = true) || domain.equals("twitter.com", ignoreCase = true)
            }
        }

    var selectedTab by remember { mutableIntStateOf(if (webSources.isNotEmpty()) 0 else 1) }
    val showTabs = webSources.isNotEmpty() && xSources.isNotEmpty()

    if (showTabs) {
        NeoSheetTabRow(
            selectedTabIndex = selectedTab,
            tabs = listOf("Web Sources", "X Sources"),
            onTabSelected = { selectedTab = it },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
        )
    }

    val activeSources = if (selectedTab == 0) webSources else xSources
    activeSources.forEachIndexed { index, source ->
        WebSearchSourceRow(
            source = source,
            onClick = onSourceClick,
        )
        if (index < activeSources.size - 1) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
        }
    }
}
