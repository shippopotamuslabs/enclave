package com.browntowndev.enclave.feature.settings

import com.browntowndev.enclave.domain.model.config.ApiCredentialsId
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReusableCredentialsForProviderTest {
    @Test
    fun `includes saved media provider keys for the same API vendor`() {
        val credentials =
            reusableCredentialsForProvider(
                provider = ApiProvider.FAL,
                apiAssets = emptyList(),
                mediaAssets =
                    listOf(
                        MediaProviderAssetUi(
                            id = MediaProviderId("media-image"),
                            displayName = "fal.ai Images",
                            provider = ApiProvider.FAL,
                            capability = MediaCapability.IMAGE,
                            credentialAlias = "media-image-key",
                        ),
                    ),
            )

        assertEquals(1, credentials.size)
        assertEquals("fal.ai Images", credentials.first().displayName)
        assertEquals("media-image-key", credentials.first().credentialAlias)
        assertTrue(credentials.first().providerLabel.contains("Image"))
    }

    @Test
    fun `dedupes BYOK and media entries that share a credential alias`() {
        val credentials =
            reusableCredentialsForProvider(
                provider = ApiProvider.FAL,
                apiAssets =
                    listOf(
                        ApiModelAssetUi(
                            credentialsId = ApiCredentialsId("byok-1"),
                            displayName = "fal.ai BYOK",
                            provider = ApiProvider.FAL,
                            baseUrl = null,
                            credentialAlias = "fal-shared",
                            configurations = emptyList(),
                        ),
                    ),
                mediaAssets =
                    listOf(
                        MediaProviderAssetUi(
                            id = MediaProviderId("media-image"),
                            displayName = "fal.ai Images",
                            provider = ApiProvider.FAL,
                            capability = MediaCapability.IMAGE,
                            credentialAlias = "fal-shared",
                        ),
                    ),
            )

        assertEquals(1, credentials.size)
        assertEquals("fal-shared", credentials.first().credentialAlias)
    }

    @Test
    fun `excludes the media provider currently being edited`() {
        val editingId = MediaProviderId("media-video-draft")
        val credentials =
            reusableCredentialsForProvider(
                provider = ApiProvider.FAL,
                apiAssets = emptyList(),
                mediaAssets =
                    listOf(
                        MediaProviderAssetUi(
                            id = MediaProviderId("media-image"),
                            displayName = "fal.ai Images",
                            provider = ApiProvider.FAL,
                            capability = MediaCapability.IMAGE,
                            credentialAlias = "media-image-key",
                        ),
                        MediaProviderAssetUi(
                            id = editingId,
                            displayName = "fal.ai Video",
                            provider = ApiProvider.FAL,
                            capability = MediaCapability.VIDEO,
                            credentialAlias = "media-video-key",
                        ),
                    ),
                excludeMediaProviderId = editingId,
            )

        assertEquals(1, credentials.size)
        assertEquals("media-image-key", credentials.first().credentialAlias)
    }
}
