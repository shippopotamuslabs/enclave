package com.browntowndev.enclave.core.ui.component.neo

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun NeoStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    min: Int = 1,
    max: Int = 99,
) {
    val borderColor = neoBorderColor()
    var textValue by remember(value) { mutableStateOf(value.toString()) }

    Row(
        modifier =
            modifier
                .width(120.dp)
                .height(44.dp)
                .neoSurface(
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    borderColor = borderColor,
                    withShadow = false,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = textValue,
            onValueChange = { raw ->
                textValue = raw.filter { it.isDigit() }.take(2)
                textValue.toIntOrNull()?.let { parsed ->
                    onValueChange(parsed.coerceIn(min, max))
                }
            },
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            textStyle =
                TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        )
        VerticalDivider(
            thickness = EnclaveNeoTokens.borderWidth,
            color = borderColor,
        )
        NeoIconButton(
            onClick = { onValueChange((value - 1).coerceAtLeast(min)) },
            icon = Icons.Default.Remove,
            contentDescription = "Decrease",
        )
        VerticalDivider(
            thickness = EnclaveNeoTokens.borderWidth,
            color = borderColor,
        )
        NeoIconButton(
            onClick = { onValueChange((value + 1).coerceAtMost(max)) },
            icon = Icons.Default.Add,
            contentDescription = "Increase",
        )
    }
}
