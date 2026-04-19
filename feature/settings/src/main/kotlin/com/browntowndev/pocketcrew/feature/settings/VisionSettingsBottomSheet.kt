package com.browntowndev.pocketcrew.feature.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.browntowndev.pocketcrew.core.ui.component.PersistentTooltip
import com.browntowndev.pocketcrew.core.ui.component.sheet.JumpFreeModalBottomSheet
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisionSettingsBottomSheet(
    uiState: SettingsUiState,
    onDismiss: () -> Unit,
    onAlwaysUseVisionModelChange: (Boolean) -> Unit,
    onSetDefaultModel: (ModelType, LocalModelConfigurationId?, ApiModelConfigurationId?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isDrilledDown by remember { mutableStateOf(false) }

    JumpFreeModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        AnimatedContent(
            targetState = isDrilledDown,
            label = "VisionSettingsTransition"
        ) { drilledDown ->
            if (drilledDown) {
                AssignmentSelectionContent(
                    modifier = Modifier.padding(bottom = 24.dp),
                    slotLabel = "Vision",
                    localAssets = emptyList(), // Vision is API only usually
                    apiAssets = uiState.apiProvidersSheet.assets.filter { it.isMultimodal },
                    onDismiss = onDismiss,
                    onBack = { isDrilledDown = false },
                    onSelect = { localId, apiId ->
                        onSetDefaultModel(ModelType.VISION, localId, apiId)
                        isDrilledDown = false
                    }
                )
            } else {
                Column(modifier = Modifier.padding(bottom = 24.dp)) {
                    Text(
                        text = "Vision Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = {
                            Icon(imageVector = Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        headlineContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Always Use API Vision Model")
                                Spacer(Modifier.width(8.dp))
                                PersistentTooltip(
                                    description = "Route image inspection through the configured API vision model, even when the active Fast or Thinking model can see images."
                                )
                            }
                        },
                        trailingContent = {
                            Switch(
                                checked = uiState.home.alwaysUseVisionModel,
                                onCheckedChange = onAlwaysUseVisionModelChange
                            )
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp))

                    val currentVisionModel = uiState.assignments.assignments.find { it.modelType == ModelType.VISION }
                    
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { isDrilledDown = true }
                    ) {
                        ListItem(
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            leadingContent = {
                                Icon(imageVector = Icons.Default.Visibility, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            headlineContent = { Text("API Vision Model", fontWeight = FontWeight.Bold) },
                            supportingContent = { Text(currentVisionModel?.currentModelName ?: "None") },
                            trailingContent = {
                                Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                            }
                        )
                    }
                }
            }
        }
    }
}
