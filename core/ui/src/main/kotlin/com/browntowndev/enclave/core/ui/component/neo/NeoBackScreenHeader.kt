package com.browntowndev.enclave.core.ui.component.neo

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme

@Composable
fun NeoBackScreenHeader(
    title: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    horizontal = EnclaveNeoTokens.screenHorizontalPadding,
                    vertical = 12.dp,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NeoIconButton(
            onClick = onBackClick,
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Go back",
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NeoBackScreenHeaderPreview() {
    EnclaveTheme {
        NeoBackScreenHeader(title = "Model Role Assignments", onBackClick = {})
    }
}
