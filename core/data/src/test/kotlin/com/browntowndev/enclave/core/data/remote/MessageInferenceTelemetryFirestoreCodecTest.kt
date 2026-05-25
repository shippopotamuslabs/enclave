package com.browntowndev.enclave.core.data.remote

import com.browntowndev.enclave.core.data.mapper.telemetry.toEntity
import com.browntowndev.enclave.domain.telemetry.TelemetryTestFixtures
import com.google.firebase.firestore.DocumentSnapshot
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class MessageInferenceTelemetryFirestoreCodecTest {
    @Test
    fun `telemetry firestore round trip preserves provider cost and schema version`() {
        val entity = TelemetryTestFixtures.telOpenAi1.toEntity()
        val fields = messageInferenceTelemetryToFirestoreMap(entity)

        assertEquals("tel-openai-1", fields["id"])
        assertEquals("0.002500", fields["costUsdDecimal"])
        assertEquals(1, fields["telemetrySchemaVersion"])

        val doc = mockk<DocumentSnapshot>()
        every { doc.id } returns entity.id
        every { doc.getString("messageId") } returns entity.messageId
        every { doc.getString("chatId") } returns entity.chatId
        every { doc.getLong("sequenceIndex") } returns entity.sequenceIndex.toLong()
        every { doc.getString("providerKey") } returns entity.providerKey
        every { doc.getString("providerLabel") } returns entity.providerLabel
        every { doc.getString("modelId") } returns entity.modelId
        every { doc.getString("modelLabel") } returns entity.modelLabel
        every { doc.getString("sourceKind") } returns entity.sourceKind
        every { doc.getLong("requestStartedAtMs") } returns entity.requestStartedAtMs
        every { doc.getLong("firstOutputTokenAtMs") } returns entity.firstOutputTokenAtMs
        every { doc.getLong("completedAtMs") } returns entity.completedAtMs
        every { doc.getLong("inputTokens") } returns entity.inputTokens?.toLong()
        every { doc.getLong("outputTokens") } returns entity.outputTokens?.toLong()
        every { doc.getLong("reasoningTokens") } returns entity.reasoningTokens?.toLong()
        every { doc.getLong("cachedInputTokens") } returns entity.cachedInputTokens?.toLong()
        every { doc.getLong("cacheReadInputTokens") } returns entity.cacheReadInputTokens?.toLong()
        every { doc.getLong("cacheWriteInputTokens") } returns entity.cacheWriteInputTokens?.toLong()
        every { doc.getLong("cacheCreationInputTokens") } returns entity.cacheCreationInputTokens?.toLong()
        every { doc.getString("costUsdDecimal") } returns entity.costUsdDecimal
        every { doc.getString("costStatus") } returns entity.costStatus
        every { doc.getString("costSource") } returns entity.costSource
        every { doc.getString("appVersionName") } returns entity.appVersionName
        every { doc.getLong("appVersionCode") } returns entity.appVersionCode
        every { doc.getLong("telemetrySchemaVersion") } returns entity.telemetrySchemaVersion.toLong()
        every { doc.getLong("updated_at") } returns entity.updatedAt
        every { doc.getBoolean("deleted") } returns entity.deleted

        val restored = messageInferenceTelemetryFromFirestore(doc)
        assertNotNull(restored)
        assertEquals(entity.id, restored!!.id)
        assertEquals(entity.costUsdDecimal, restored.costUsdDecimal)
        assertEquals(entity.telemetrySchemaVersion, restored.telemetrySchemaVersion)
    }
}
