package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests that InferenceFactoryImpl correctly maps each ModelType to its on-device engine.
 * The stub always returns on-device — API routing is a future ticket.
 */
class InferenceFactoryImplTest {

    // 7 distinct mock instances — one per qualifier
    private lateinit var fastEngine: LlmInferencePort
    private lateinit var thinkingEngine: LlmInferencePort
    private lateinit var mainEngine: LlmInferencePort
    private lateinit var draftOneEngine: LlmInferencePort
    private lateinit var draftTwoEngine: LlmInferencePort
    private lateinit var finalSynthEngine: LlmInferencePort
    private lateinit var visionEngine: LlmInferencePort
    private lateinit var loggingPort: LoggingPort

    private lateinit var factory: InferenceFactoryImpl

    @BeforeEach
    fun setUp() {
        fastEngine = mockk(relaxed = true, name = "fastEngine")
        thinkingEngine = mockk(relaxed = true, name = "thinkingEngine")
        mainEngine = mockk(relaxed = true, name = "mainEngine")
        draftOneEngine = mockk(relaxed = true, name = "draftOneEngine")
        draftTwoEngine = mockk(relaxed = true, name = "draftTwoEngine")
        finalSynthEngine = mockk(relaxed = true, name = "finalSynthEngine")
        visionEngine = mockk(relaxed = true, name = "visionEngine")
        loggingPort = mockk(relaxed = true)

        factory = InferenceFactoryImpl(
            fastOnDevice = fastEngine,
            thinkingOnDevice = thinkingEngine,
            mainOnDevice = mainEngine,
            draftOneOnDevice = draftOneEngine,
            draftTwoOnDevice = draftTwoEngine,
            finalSynthOnDevice = finalSynthEngine,
            visionOnDevice = visionEngine,
            loggingPort = loggingPort,
        )
    }

    // ========================================================================
    // Happy Path — Returns correct engine for each ModelType
    // ========================================================================

    @Test
    fun `returns correct on-device engine for FAST`() = runTest {
        val result = factory.getInferenceService(ModelType.FAST)
        assertSame(fastEngine, result)
    }

    @Test
    fun `returns correct on-device engine for THINKING`() = runTest {
        val result = factory.getInferenceService(ModelType.THINKING)
        assertSame(thinkingEngine, result)
    }

    @Test
    fun `returns correct on-device engine for MAIN`() = runTest {
        val result = factory.getInferenceService(ModelType.MAIN)
        assertSame(mainEngine, result)
    }

    @Test
    fun `returns correct on-device engine for DRAFT_ONE`() = runTest {
        val result = factory.getInferenceService(ModelType.DRAFT_ONE)
        assertSame(draftOneEngine, result)
    }

    @Test
    fun `returns correct on-device engine for DRAFT_TWO`() = runTest {
        val result = factory.getInferenceService(ModelType.DRAFT_TWO)
        assertSame(draftTwoEngine, result)
    }

    @Test
    fun `returns correct on-device engine for FINAL_SYNTHESIS`() = runTest {
        val result = factory.getInferenceService(ModelType.FINAL_SYNTHESIS)
        assertSame(finalSynthEngine, result)
    }

    @Test
    fun `returns correct on-device engine for VISION`() = runTest {
        val result = factory.getInferenceService(ModelType.VISION)
        assertSame(visionEngine, result)
    }

    @Test
    fun `returns correct on-device engine for every ModelType`() = runTest {
        val expectedMapping = mapOf(
            ModelType.FAST to fastEngine,
            ModelType.THINKING to thinkingEngine,
            ModelType.MAIN to mainEngine,
            ModelType.DRAFT_ONE to draftOneEngine,
            ModelType.DRAFT_TWO to draftTwoEngine,
            ModelType.FINAL_SYNTHESIS to finalSynthEngine,
            ModelType.VISION to visionEngine,
        )

        for ((modelType, expectedEngine) in expectedMapping) {
            val actual = factory.getInferenceService(modelType)
            assertSame(expectedEngine, actual, "Wrong engine returned for $modelType")
        }
    }

    // ========================================================================
    // Edge Case
    // ========================================================================

    @Test
    fun `multiple calls for same ModelType return same instance`() = runTest {
        val first = factory.getInferenceService(ModelType.FAST)
        val second = factory.getInferenceService(ModelType.FAST)
        assertSame(first, second)
    }

    // ========================================================================
    // Mutation Defense — InferenceFactory does not hardcode a single engine
    // ========================================================================

    @Test
    fun `does not return same instance for distinct ModelTypes`() = runTest {
        val results = ModelType.entries.map { factory.getInferenceService(it) }

        // All 7 engines should be referentially distinct
        for (i in results.indices) {
            for (j in results.indices) {
                if (i != j) {
                    assert(results[i] !== results[j]) {
                        "Engine for ${ModelType.entries[i]} and ${ModelType.entries[j]} " +
                            "are the same instance — factory is hardcoding a single engine"
                    }
                }
            }
        }
    }
}
