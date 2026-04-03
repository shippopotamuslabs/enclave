package com.browntowndev.pocketcrew.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ByokCustomHeadersRoute(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val handleBack: () -> Unit = {
        viewModel.onCleanupCustomHeaders()
        onNavigateBack()
    }

    ByokCustomHeadersScreen(
        headers = uiState.selectedApiModelConfig?.customHeaders ?: emptyList(),
        onNavigateBack = handleBack,
        onAddHeader = viewModel::onAddCustomHeader,
        onDeleteHeader = viewModel::onDeleteCustomHeader,
        onHeaderChange = viewModel::onCustomHeaderChange
    )

    BackHandler(onBack = handleBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ByokCustomHeadersScreen(
    headers: List<CustomHeaderUi>,
    onNavigateBack: () -> Unit,
    onAddHeader: () -> Unit,
    onDeleteHeader: (Int) -> Unit,
    onHeaderChange: (Int, CustomHeaderUi) -> Unit
) {
    val isDoneEnabled = headers.all { it.key.isNotBlank() && it.value.isNotBlank() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Custom Headers", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        enabled = isDoneEnabled
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onAddHeader) {
                        Icon(Icons.Default.Add, contentDescription = "Add Header")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            if (headers.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "No custom headers configured",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onAddHeader,
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Text("Add First Header")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)
                ) {
                    itemsIndexed(headers) { index, header ->
                        HeaderRow(
                            header = header,
                            onHeaderChange = { onHeaderChange(index, it) },
                            onDelete = { onDeleteHeader(index) }
                        )
                    }
                }
                
                Button(
                    onClick = onNavigateBack,
                    enabled = isDoneEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
fun HeaderRow(
    header: CustomHeaderUi,
    onHeaderChange: (CustomHeaderUi) -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = header.key,
            onValueChange = { onHeaderChange(header.copy(key = it)) },
            label = { Text("Name") },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(8.dp),
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next)
        )
        OutlinedTextField(
            value = header.value,
            onValueChange = { onHeaderChange(header.copy(value = it)) },
            label = { Text("Value") },
            modifier = Modifier.weight(1.5f),
            shape = RoundedCornerShape(8.dp),
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done)
        )
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete header",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}
