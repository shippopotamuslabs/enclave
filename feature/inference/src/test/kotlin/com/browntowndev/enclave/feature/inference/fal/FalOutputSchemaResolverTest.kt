package com.browntowndev.enclave.feature.inference.fal

import com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel
import com.browntowndev.enclave.domain.model.media.ModelGenerationOutputSchema
import com.browntowndev.enclave.domain.model.media.OutputMediaKind
import com.browntowndev.enclave.domain.model.media.OutputMediaUrlPath
import com.browntowndev.enclave.feature.inference.RecordingApiModelCatalogPort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FalOutputSchemaResolverTest {
    private val apiModelCatalog = RecordingApiModelCatalogPort()

    private val resolver = FalOutputSchemaResolver(apiModelCatalog)

    private val testSchema =
        ModelGenerationOutputSchema(
            mediaUrlPaths =
                listOf(
                    OutputMediaUrlPath(segments = listOf("images", "*", "url"), kind = OutputMediaKind.IMAGE, priority = 1),
                ),
        )

    @Test
    fun resolve_returnsSchemaWhenModelFound() =
        runTest {
            apiModelCatalog.models["fal-ai/flux" to null] =
                DiscoveredApiModel(id = "fal-ai/flux", name = "FLUX", outputSchema = testSchema)

            val result = resolver.resolve("fal-ai/flux", "key", null)

            assertEquals(testSchema, result)
        }

    @Test
    fun resolve_returnsNullWhenModelNotFound() =
        runTest {
            apiModelCatalog.models["fal-ai/unknown" to null] = null

            val result = resolver.resolve("fal-ai/unknown", "key", null)

            assertNull(result)
        }

    @Test
    fun resolve_returnsNullWhenOutputSchemaIsNull() =
        runTest {
            apiModelCatalog.models["fal-ai/flux-kontext/max" to null] =
                DiscoveredApiModel(id = "fal-ai/flux-kontext/max", name = "FLUX Kontext Max", outputSchema = null)

            val result = resolver.resolve("fal-ai/flux-kontext/max", "key", null)

            assertNull(result)
        }

    @Test
    fun resolve_returnsNullWhenFetchThrows() =
        runTest {
            apiModelCatalog.failure = RuntimeException("network error")

            val result = resolver.resolve("fal-ai/error", "key", null)

            assertNull(result)
        }

    @Test
    fun resolve_returnsNullForEmptyModelId() =
        runTest {
            val result = resolver.resolve("", "key", null)

            assertNull(result)
            assertTrue(apiModelCatalog.fetchModelCalls.isEmpty())
        }

    @Test
    fun resolve_returnsNullForBlankModelId() =
        runTest {
            val result = resolver.resolve("   ", "key", null)

            assertNull(result)
            assertTrue(apiModelCatalog.fetchModelCalls.isEmpty())
        }

    @Test
    fun resolve_cachesResultOnSubsequentCalls() =
        runTest {
            apiModelCatalog.models["fal-ai/flux" to null] =
                DiscoveredApiModel(id = "fal-ai/flux", name = "FLUX", outputSchema = testSchema)

            val result1 = resolver.resolve("fal-ai/flux", "key", null)
            val result2 = resolver.resolve("fal-ai/flux", "key", null)

            assertEquals(testSchema, result1)
            assertEquals(testSchema, result2)
            assertEquals(1, apiModelCatalog.fetchModelCalls.size)
        }

    @Test
    fun resolve_cachesNullResultOnSubsequentCalls() =
        runTest {
            apiModelCatalog.models["fal-ai/missing" to null] = null

            val result1 = resolver.resolve("fal-ai/missing", "key", null)
            val result2 = resolver.resolve("fal-ai/missing", "key", null)

            assertNull(result1)
            assertNull(result2)
            assertEquals(1, apiModelCatalog.fetchModelCalls.size)
        }

    @Test
    fun resolve_cachesExceptionsOnSubsequentCalls() =
        runTest {
            apiModelCatalog.failure = RuntimeException("boom")

            val result1 = resolver.resolve("fal-ai/fail", "key", null)
            val result2 = resolver.resolve("fal-ai/fail", "key", null)

            assertNull(result1)
            assertNull(result2)
            assertEquals(1, apiModelCatalog.fetchModelCalls.size)
        }

    @Test
    fun resolve_differentiatesByBaseUrl() =
        runTest {
            apiModelCatalog.models["fal-ai/flux" to null] =
                DiscoveredApiModel(id = "fal-ai/flux", name = "FLUX", outputSchema = null)
            apiModelCatalog.models["fal-ai/flux" to "https://custom.run"] =
                DiscoveredApiModel(id = "fal-ai/flux", name = "FLUX", outputSchema = testSchema)

            val result1 = resolver.resolve("fal-ai/flux", "key", null)
            val result2 = resolver.resolve("fal-ai/flux", "key", "https://custom.run")

            assertNull(result1)
            assertEquals(testSchema, result2)
        }
}
