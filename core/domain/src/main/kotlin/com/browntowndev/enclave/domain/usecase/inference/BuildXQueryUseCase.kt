package com.browntowndev.enclave.domain.usecase.inference

import com.browntowndev.enclave.domain.model.inference.XSearchParams
import javax.inject.Inject

class BuildXQueryUseCase
    @Inject
    constructor() {
        operator fun invoke(params: XSearchParams): String {
            val parts = mutableListOf<String>()

            params.query
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { parts.add(it) }
            params.keywords?.forEach { parts.add(it.trim()) }
            params.phrases?.forEach { parts.add("\"${it.trim()}\"") }
            params.hashtags?.forEach { parts.add("#${it.trim().removePrefix("#")}") }
            params.mentions?.forEach { parts.add("@${it.trim().removePrefix("@")}") }

            if (!params.any_keywords.isNullOrEmpty()) {
                val orPart = params.any_keywords.joinToString(" OR ") { it.trim() }
                parts.add("($orPart)")
            }

            params.exclude_keywords?.forEach { parts.add("-${it.trim()}") }

            params.from_user
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { parts.add("from:${it.removePrefix("@")}") }
            params.to_user
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { parts.add("to:${it.removePrefix("@")}") }

            if (params.has_geo) parts.add("has:geo")
            if (params.has_media) parts.add("has:media")

            params.place_country
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { parts.add("place_country:$it") }

            if (params.has_images) parts.add("has:images")
            if (params.has_videos) parts.add("has:videos")
            if (params.has_links) parts.add("has:links")

            params.language
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.let { parts.add("lang:$it") }

            val query = parts.joinToString(" ").trim()

            // Conjunction-required safeguard: operators like has:media cannot be used alone.
            // We ensure at least one standalone operator is present.
            val hasStandalone =
                params.query?.isNotEmpty() == true ||
                    params.keywords?.isNotEmpty() == true ||
                    params.any_keywords?.isNotEmpty() == true ||
                    params.phrases?.isNotEmpty() == true ||
                    params.hashtags?.isNotEmpty() == true ||
                    params.mentions?.isNotEmpty() == true ||
                    params.from_user?.isNotEmpty() == true ||
                    params.to_user?.isNotEmpty() == true

            if (!hasStandalone && query.isNotEmpty()) {
                require(false) {
                    "X search requires at least one standalone operator " +
                        "(keyword, phrase, hashtag, mention, or user filter) when using conjunction-required filters."
                }
            }

            return query
        }
    }
