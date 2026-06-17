package com.browntowndev.enclave.feature.inference.fal

import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel
import com.browntowndev.enclave.domain.model.media.ModelGenerationOutputSchema
import com.browntowndev.enclave.domain.model.media.OutputMediaKind
import com.browntowndev.enclave.domain.model.media.OutputMediaUrlPath
import com.browntowndev.enclave.domain.port.repository.ApiModelCatalogPort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FalOutputSchemaResolverTest {
    private val apiModelCatalog = mockk<ApiModelCatalogPort>()

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
            coEvery {
                apiModelCatalog.fetchModel(ApiProvider.FAL, "key", "fal-ai/flux", null)
            } returns DiscoveredApiModel(id = "fal-ai/flux", name = "FLUX", outputSchema = testSchema)

            val result = resolver.resolve("fal-ai/flux", "key", null)

            assertEquals(testSchema, result)
        }

    @Test
    fun resolve_returnsNullWhenModelNotFound() =
        runTest {
            coEvery {
                apiModelCatalog.fetchModel(ApiProvider.FAL, "key", "fal-ai/unknown", null)
            } returns null

            val result = resolver.resolve("fal-ai/unknown", "key", null)

            assertNull(result)
        }

    @Test
    fun resolve_returnsNullWhenOutputSchemaIsNull() =
        runTest {
            coEvery {
                apiModelCatalog.fetchModel(ApiProvider.FAL, "key", "fal-ai/flux-kontext/max", null)
            } returns DiscoveredApiModel(id = "fal-ai/flux-kontext/max", name = "FLUX Kontext Max", outputSchema = null)

            val result = resolver.resolve("fal-ai/flux-kontext/max", "key", null)

            assertNull(result)
        }

    @Test
    fun resolve_returnsNullWhenFetchThrows() =
        runTest {
            coEvery {
                apiModelCatalog.fetchModel(ApiProvider.FAL, "key", "fal-ai/error", null)
            } throws RuntimeException("network error")

            val result = resolver.resolve("fal-ai/error", "key", null)

            assertNull(result)
        }

    @Test
    fun resolve_returnsNullForEmptyModelId() =
        runTest {
            val result = resolver.resolve("", "key", null)

            assertNull(result)
            coVerify(exactly = 0) { apiModelCatalog.fetchModel(any(), any(), any(), any()) }
        }

    @Test
    fun resolve_returnsNullForBlankModelId() =
        runTest {
            val result = resolver.resolve("   ", "key", null)

            assertNull(result)
            coVerify(exactly = 0) { apiModelCatalog.fetchModel(any(), any(), any(), any()) }
        }

    @Test
    fun resolve_cachesResultOnSubsequentCalls() =
        runTest {
            coEvery {
                apiModelCatalog.fetchModel(ApiProvider.FAL, "key", "fal-ai/flux", null)
            } returns DiscoveredApiModel(id = "fal-ai/flux", name = "FLUX", outputSchema = testSchema)

            val result1 = resolver.resolve("fal-ai/flux", "key", null)
            val result2 = resolver.resolve("fal-ai/flux", "key", null)

            assertEquals(testSchema, result1)
            assertEquals(testSchema, result2)
            coVerify(exactly = 1) { apiModelCatalog.fetchModel(any(), any(), any(), any()) }
        }

    @Test
    fun resolve_cachesNullResultOnSubsequentCalls() =
        runTest {
            coEvery {
                apiModelCatalog.fetchModel(ApiProvider.FAL, "key", "fal-ai/missing", null)
            } returns null

            val result1 = resolver.resolve("fal-ai/missing", "key", null)
            val result2 = resolver.resolve("fal-ai/missing", "key", null)

            assertNull(result1)
            assertNull(result2)
            coVerify(exactly = 1) { apiModelCatalog.fetchModel(any(), any(), any(), any()) }
        }

    @Test
    fun resolve_cachesExceptionsOnSubsequentCalls() =
        runTest {
            coEvery {
                apiModelCatalog.fetchModel(ApiProvider.FAL, "key", "fal-ai/fail", null)
            } throws RuntimeException("boom")

            val result1 = resolver.resolve("fal-ai/fail", "key", null)
            val result2 = resolver.resolve("fal-ai/fail", "key", null)

            assertNull(result1)
            assertNull(result2)
            coVerify(exactly = 1) { apiModelCatalog.fetchModel(any(), any(), any(), any()) }
        }

    @Test
    fun resolve_differentiatesByBaseUrl() =
        runTest {
            coEvery {
                apiModelCatalog.fetchModel(ApiProvider.FAL, "key", "fal-ai/flux", null)
            } returns DiscoveredApiModel(id = "fal-ai/flux", name = "FLUX", outputSchema = null)
            coEvery {
                apiModelCatalog.fetchModel(ApiProvider.FAL, "key", "fal-ai/flux", "https://custom.run")
            } returns DiscoveredApiModel(id = "fal-ai/flux", name = "FLUX", outputSchema = testSchema)

            val result1 = resolver.resolve("fal-ai/flux", "key", null)
            val result2 = resolver.resolve("fal-ai/flux", "key", "https://custom.run")

            assertNull(result1)
            assertEquals(testSchema, result2)
        }
}
