package com.browntowndev.enclave.core.data.repository

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException

@Serializable
data class XSearchResponse(
    val data: List<XTweet>? = null,
    val includes: XIncludes? = null,
    val meta: XMeta? = null,
)

@Serializable
data class XTweet(
    val id: String,
    val text: String,
    val author_id: String? = null,
    val created_at: String? = null,
    val conversation_id: String? = null,
    val public_metrics: XPublicMetrics? = null,
    val attachments: XAttachments? = null,
    val entities: XEntities? = null,
    val referenced_tweets: List<XReferencedTweet>? = null,
)

@Serializable
data class XAttachments(
    val media_keys: List<String>? = null,
)

@Serializable
data class XEntities(
    val urls: List<XUrl>? = null,
)

@Serializable
data class XUrl(
    val expanded_url: String,
)

@Serializable
data class XReferencedTweet(
    val type: String,
    val id: String,
)

@Serializable
data class XUserPublicMetrics(
    val followers_count: Int = 0,
    val following_count: Int = 0,
)

@Serializable
data class XIncludes(
    val users: List<XUser>? = null,
    val media: List<XMedia>? = null,
    val tweets: List<XTweet>? = null,
)

@Serializable
data class XUser(
    val id: String,
    val name: String,
    val username: String,
    val verified: Boolean? = null,
    val public_metrics: XUserPublicMetrics? = null,
)

@Serializable
data class XMeta(
    val result_count: Int = 0,
    val next_token: String? = null,
)

@Serializable
data class XUserLookupResponse(
    val data: XUser? = null,
)

@Serializable
data class XUsersByIdsResponse(
    val data: List<XUser>? = null,
)

internal val xApiJson = Json { ignoreUnknownKeys = true }

internal fun sanitizePaginationToken(token: String?): String? =
    token
        ?.trim()
        ?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }

internal fun shouldFallbackTimelineToSearch(error: IOException): Boolean {
    val message = error.message.orEmpty().lowercase()
    if (message.contains("pagination")) return false
    return message.contains("http 403") ||
        message.contains("http 401") ||
        message.contains("http 400") ||
        message.contains("unsupported authentication")
}
