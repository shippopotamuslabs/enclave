package com.browntowndev.enclave.feature.chat.components.messagelist

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.feature.chat.R

@Composable
internal fun MessageListEmptyState(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxSize(),
    ) {
        Image(
            painter = painterResource(R.drawable.enclave_icon),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .width(123.dp)
                .height(160.dp),
        )
        Text(
            text = "Welcome to your Enclave",
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}
