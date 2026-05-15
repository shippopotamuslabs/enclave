@file:Suppress("MaxLineLength")

package com.browntowndev.pocketcrew.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.pocketcrew.core.ui.component.sheet.JumpFreeModalBottomSheet
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme

private data class TosSection(val heading: String, val body: String)

private val TOS_SECTIONS = listOf(
    TosSection(
        "1. Acceptance of Terms",
        "By using Pocket Crew, you confirm that you are at least 18 years of age and have the legal capacity to enter into these Terms. The App is not intended for use by anyone under 18. Note that third-party API providers whose keys you use through this App (OpenAI, Anthropic, Google, and others) independently require users to be at least 18 years of age.",
    ),
    TosSection(
        "2. Description of Service",
        "Pocket Crew is a personal AI assistant application that operates primarily on-device using local inference models (\"Local Models\"). It also optionally connects to third-party AI provider APIs (\"Cloud Providers\") using credentials you supply (\"Bring Your Own Key\" or \"BYOK\"). The developer does not supply, own, or control any Cloud Provider API keys used through the App.",
    ),
    TosSection(
        "3. User Responsibilities — API Keys",
        "You are solely responsible for:\n\n" +
            "• Obtaining your own API keys from third-party providers (OpenAI, Anthropic, Google, and others) and complying with each provider's terms of service.\n" +
            "• All usage, costs, and charges incurred through your API keys.\n" +
            "• The security of your API keys. You should treat your API keys as passwords. The developer is not responsible for unauthorized use of your keys.\n" +
            "• Any violation of a third-party provider's acceptable use policy resulting from your use of the App.",
    ),
    TosSection(
        "4. User Responsibilities — Generated Content",
        "You are solely responsible for all content generated through the App, including but not limited to text, images, video, audio, and any other media. You agree that you will not use the App to generate or distribute:\n\n" +
            "• Content that is illegal under applicable law.\n" +
            "• Content that infringes intellectual property rights of third parties.\n" +
            "• Content that is defamatory, harassing, threatening, or abusive.\n" +
            "• Child sexual abuse material or any content that sexually exploits minors.\n" +
            "• Malware, phishing content, or material designed to deceive or defraud.\n\n" +
            "The developer reserves the right to terminate access if misuse is reported or discovered.",
    ),
    TosSection(
        "5. Local Model Usage",
        "Local Models run entirely on your device. The developer does not control, audit, or guarantee the accuracy, safety, or fitness of any local model. Use of Local Models is entirely at your own risk. The developer is not responsible for any output produced by Local Models.",
    ),
    TosSection(
        "6. Disclaimer of Warranties",
        "THE APP IS PROVIDED \"AS IS\" AND \"AS AVAILABLE,\" WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED. TO THE MAXIMUM EXTENT PERMITTED BY APPLICABLE LAW, THE DEVELOPER EXPRESSLY DISCLAIMS ALL WARRANTIES, INCLUDING BUT NOT LIMITED TO IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, TITLE, AND NON-INFRINGEMENT. THE DEVELOPER DOES NOT WARRANT THAT THE APP WILL BE UNINTERRUPTED, ERROR-FREE, SECURE, OR FREE OF VIRUSES OR OTHER HARMFUL COMPONENTS.",
    ),
    TosSection(
        "7. Limitation of Liability",
        "TO THE MAXIMUM EXTENT PERMITTED BY APPLICABLE LAW, THE DEVELOPER SHALL NOT BE LIABLE FOR ANY INDIRECT, INCIDENTAL, SPECIAL, CONSEQUENTIAL, PUNITIVE, OR EXEMPLARY DAMAGES ARISING OUT OF OR IN CONNECTION WITH YOUR USE OF, OR INABILITY TO USE, THE APP — INCLUDING BUT NOT LIMITED TO LOSS OF DATA, LOSS OF PROFITS, LOSS OF BUSINESS, OR ANY HARM CAUSED BY AI-GENERATED CONTENT — EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.\n\n" +
            "IN NO EVENT SHALL THE DEVELOPER'S TOTAL CUMULATIVE LIABILITY TO YOU FOR ANY CLAIMS ARISING FROM THESE TERMS OR YOUR USE OF THE APP EXCEED THE AMOUNT YOU PAID FOR THE APP IN THE TWELVE (12) MONTHS PRECEDING THE CLAIM (OR USD \$10 IF THE APP WAS FREE).",
    ),
    TosSection(
        "8. Indemnification",
        "You agree to indemnify, defend, and hold harmless the developer from and against any claims, damages, losses, liabilities, costs, and expenses (including reasonable attorneys' fees) arising out of or relating to: (a) your use of the App; (b) your violation of these Terms; (c) your violation of any third-party rights, including API terms of service; or (d) any content you generate using the App.",
    ),
    TosSection(
        "9. Acceptable Use",
        "You agree not to use the App in any manner that:\n\n" +
            "• Violates any applicable local, state, national, or international law or regulation.\n" +
            "• Abuses, harasses, or harms any individual.\n" +
            "• Circumvents, disables, or interferes with security-related features of the App or any third-party service.\n" +
            "• Violates the terms of service of any third-party AI provider whose API you use through the App.",
    ),
    TosSection(
        "10. Privacy",
        "Pocket Crew is built on a zero-knowledge architecture. All user data — including chat messages, conversation history, memories, documents, API keys, model configurations, and generated media metadata — is encrypted on your device using a vault key derived solely from your personal vault password before any data leaves the device. The developer has no access to your vault password and therefore cannot decrypt, read, or access any of this data. Even in the event of a server-side breach, your content remains protected because only ciphertext is ever stored remotely. The developer does not sell or share your personal data with third parties for advertising. Cloud sync is optional and requires explicit sign-in.",
    ),
    TosSection(
        "11. Changes to These Terms",
        "The developer may update these Terms from time to time. When changes are made, the \"Last Updated\" date at the top will be revised. Continued use of the App after changes constitutes acceptance of the updated Terms.",
    ),
    TosSection(
        "12. Governing Law and Dispute Resolution",
        "These Terms are governed by the laws of the State of Delaware, United States, without regard to its conflict-of-law provisions. Any dispute arising from these Terms or your use of the App shall be resolved exclusively in the state or federal courts located in Delaware. You consent to the personal jurisdiction of such courts.",
    ),
    TosSection(
        "13. Entire Agreement",
        "These Terms constitute the entire agreement between you and the developer with respect to the App and supersede all prior or contemporaneous understandings or agreements. If any provision is held unenforceable, the remaining provisions will remain in full force and effect.",
    ),
    TosSection(
        "14. Contact",
        "If you have questions about these Terms, you can contact the developer through the in-app Feedback feature.",
    ),
)

internal val TOS_TEXT: String = TOS_SECTIONS.joinToString("\n\n") { "${it.heading}\n\n${it.body}" }

@Suppress("LongMethod")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TosBottomSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    JumpFreeModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Terms of Service",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Last Updated: May 15, 2026",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Please read these Terms of Service (\"Terms\") carefully before using Pocket Crew (the \"App\"). " +
                        "By downloading, installing, or using the App, you agree to be bound by these Terms. " +
                        "If you do not agree, do not use the App.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            items(TOS_SECTIONS) { section ->
                Text(
                    text = section.heading,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = section.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Preview(showBackground = true, name = "TOS Bottom Sheet")
@Composable
fun PreviewTosBottomSheet() {
    PocketCrewTheme {
        TosBottomSheet(onDismiss = {})
    }
}
