package com.browntowndev.enclave.core.ui.component.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.component.dialog.AddAlbumDialog
import com.browntowndev.enclave.core.ui.component.neo.NeoAlbumSelectionRow
import com.browntowndev.enclave.core.ui.component.neo.NeoModalBottomSheet
import com.browntowndev.enclave.core.ui.component.neo.NeoPrimaryButton
import com.browntowndev.enclave.core.ui.component.neo.NeoSectionHeader
import com.browntowndev.enclave.core.ui.component.neo.NeoSheetHeader
import com.browntowndev.enclave.core.ui.model.AlbumSelectionItem
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveToAlbumBottomSheet(
    albums: List<AlbumSelectionItem>,
    onDismiss: () -> Unit,
    onMoveToAlbum: (String) -> Unit,
    onAddAlbum: (String) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Save to Album",
) {
    var isAddingNewAlbum by remember { mutableStateOf(false) }

    NeoModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            NeoSheetHeader(
                title = title,
                onDismiss = onDismiss,
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(EnclaveNeoTokens.rowSpacing),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                item {
                    NeoPrimaryButton(
                        text = "Create New Album",
                        icon = Icons.Default.Add,
                        onClick = { isAddingNewAlbum = true },
                        modifier = Modifier.padding(bottom = EnclaveNeoTokens.rowSpacing),
                    )
                }

                if (albums.isNotEmpty()) {
                    item {
                        NeoSectionHeader(text = "Existing Albums")
                    }
                }

                items(
                    items = albums,
                    key = { album -> album.id },
                ) { album ->
                    NeoAlbumSelectionRow(
                        name = album.name,
                        itemCount = album.itemCount,
                        coverUri = album.coverUri,
                        coverMediaType = album.coverMediaType,
                        onClick = { onMoveToAlbum(album.id) },
                    )
                }
            }
        }
    }

    if (isAddingNewAlbum) {
        AddAlbumDialog(
            onDismiss = { isAddingNewAlbum = false },
            onConfirm = { name ->
                onAddAlbum(name)
                isAddingNewAlbum = false
            },
        )
    }
}
