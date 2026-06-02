package com.browntowndev.enclave.feature.settings.steps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.component.neo.NeoPrimaryButton
import com.browntowndev.enclave.core.ui.component.neo.NeoSecondaryButton
import com.browntowndev.enclave.core.ui.component.neo.NeoSectionHeader
import com.browntowndev.enclave.domain.model.profile.AnalysisPass

@Composable
internal fun AnalysisProgressStep(
    currentPass: Int,
    totalPasses: Int,
    isComplete: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
    ) {
        NeoSectionHeader(text = "Analysing your conversations")

        Spacer(Modifier.height(16.dp))

        if (errorMessage != null) {
            Text("Analysis failed: $errorMessage")
            Spacer(Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                NeoSecondaryButton(
                    text = "Back",
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.padding(horizontal = 4.dp))
                NeoPrimaryButton(
                    text = "Retry",
                    onClick = onRetry,
                    modifier = Modifier.weight(1f),
                )
            }
        } else if (isComplete) {
            Text("Analysis complete. Preparing your profile for review…")
        } else {
            val passName = AnalysisPass.fromIndex(currentPass - 1)?.displayName ?: "Preparing…"
            Text("Pass $currentPass of $totalPasses: $passName")
            Spacer(Modifier.height(12.dp))
            if (totalPasses > 0) {
                LinearProgressIndicator(
                    progress = { currentPass.toFloat() / totalPasses },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(16.dp))
            Text("This may take several minutes. You can leave the app — analysis continues in the background.")
        }
    }
}
