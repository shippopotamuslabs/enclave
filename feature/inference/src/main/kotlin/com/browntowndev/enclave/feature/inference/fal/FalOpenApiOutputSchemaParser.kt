package com.browntowndev.enclave.feature.inference.fal

import com.browntowndev.enclave.domain.model.media.ModelGenerationOutputSchema
import com.browntowndev.enclave.domain.model.media.OutputMediaKind
import com.browntowndev.enclave.domain.model.media.OutputMediaUrlPath
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object FalOpenApiOutputSchemaParser {
    fun parse(openApiDocument: JsonObject): ModelGenerationOutputSchema? {
        val schemas = openApiDocument["components"]?.jsonObject?.get("schemas")?.jsonObject ?: return null
        val resolver = OpenApiSchemaResolver(schemas)
        val paths =
            schemas.entries
                .mapNotNull { (name, element) -> (element as? JsonObject)?.let { name to it } }
                .filter { (name, schema) -> isOutputSchema(name, schema) }
                .flatMap { (_, schema) -> collectMediaPaths(schema, resolver, parentKey = null) }
                .distinctBy { it.segments }
                .sortedWith(compareBy<OutputMediaUrlPath> { it.priority }.thenBy { it.segments.joinToString(".") })
        return paths.takeIf { it.isNotEmpty() }?.let { ModelGenerationOutputSchema(mediaUrlPaths = it) }
    }

    private fun isOutputSchema(
        schemaName: String,
        schema: JsonObject,
    ): Boolean {
        val properties = schema["properties"]?.jsonObject ?: return false
        if (properties.containsKey("prompt")) return false
        if (properties.containsKey("status") && properties.containsKey("request_id")) return false
        if (properties.containsKey("status_url") || properties.containsKey("response_url")) return false
        return schemaName.contains("Output", ignoreCase = true) ||
            properties.keys.any(::isMediaPropertyName)
    }

    private fun collectMediaPaths(
        schema: JsonObject,
        resolver: OpenApiSchemaResolver,
        parentKey: String?,
    ): List<OutputMediaUrlPath> {
        val properties = schema["properties"]?.jsonObject ?: return emptyList()
        return properties.entries.flatMap { (key, property) ->
            collectPathsForProperty(
                key = key,
                property = property,
                resolver = resolver,
                parentKey = parentKey,
            )
        }
    }

    private fun collectPathsForProperty(
        key: String,
        property: JsonElement,
        resolver: OpenApiSchemaResolver,
        parentKey: String?,
    ): List<OutputMediaUrlPath> {
        val propertyObject = property as? JsonObject ?: return emptyList()
        val pathPrefix = listOfNotNull(parentKey, key).filter { it.isNotBlank() }
        val kind = key.toOutputMediaKind()
        return when {
            key.endsWith("_url", ignoreCase = true) && propertyObject.looksLikeString() ->
                listOf(path(pathPrefix, kind))
            else -> collectPathsForPropertyBody(propertyObject, pathPrefix, key, resolver, kind)
        }
    }

    private fun collectPathsForPropertyBody(
        propertyObject: JsonObject,
        pathPrefix: List<String>,
        key: String,
        resolver: OpenApiSchemaResolver,
        kind: OutputMediaKind,
    ): List<OutputMediaUrlPath> {
        val refSchema = propertyObject["\$ref"]?.jsonPrimitive?.contentOrNull?.let(resolver::resolveRef)
        if (refSchema != null) {
            return collectPathsForRefTarget(pathPrefix, refSchema, resolver, kind)
        }
        val arrayItemSchema = arrayItemFileSchema(propertyObject, resolver)
        if (arrayItemSchema != null) {
            return listOf(
                path(pathPrefix + OutputMediaUrlPath.ARRAY_ANY + "url", kind),
            )
        }
        if (propertyObject.looksLikeFileShape()) {
            return listOf(path(pathPrefix + "url", kind))
        }
        val nestedSchema = propertyObject.takeIf { it["properties"] != null } ?: return emptyList()
        return collectMediaPaths(nestedSchema, resolver, parentKey = key)
    }

    private fun arrayItemFileSchema(
        propertyObject: JsonObject,
        resolver: OpenApiSchemaResolver,
    ): JsonObject? {
        if (propertyObject["type"]?.jsonPrimitive?.contentOrNull != "array") return null
        val itemSchema =
            propertyObject["items"]?.jsonObject?.let { items ->
                items["\$ref"]?.jsonPrimitive?.contentOrNull?.let(resolver::resolveRef)
                    ?: items.takeIf { it.looksLikeFileShape() }
            }
        return itemSchema?.takeIf { it.looksLikeFileShape() }
    }

    private fun path(
        segments: List<String>,
        kind: OutputMediaKind,
    ): OutputMediaUrlPath =
        OutputMediaUrlPath(
            segments = segments,
            kind = kind,
            priority = PRIMARY_PATH_PRIORITY,
        )

    private fun collectPathsForRefTarget(
        pathPrefix: List<String>,
        refSchema: JsonObject,
        resolver: OpenApiSchemaResolver,
        kind: OutputMediaKind,
    ): List<OutputMediaUrlPath> =
        if (refSchema.looksLikeFileShape()) {
            listOf(
                OutputMediaUrlPath(
                    segments = pathPrefix + "url",
                    kind = kind,
                    priority = PRIMARY_PATH_PRIORITY,
                ),
            )
        } else {
            collectMediaPaths(refSchema, resolver, parentKey = pathPrefix.lastOrNull())
        }

    private fun String.toOutputMediaKind(): OutputMediaKind =
        when {
            contains("video", ignoreCase = true) -> OutputMediaKind.VIDEO
            contains("image", ignoreCase = true) -> OutputMediaKind.IMAGE
            contains("audio", ignoreCase = true) || contains("music", ignoreCase = true) -> OutputMediaKind.AUDIO
            else -> OutputMediaKind.ANY
        }

    private fun isMediaPropertyName(key: String): Boolean =
        key.endsWith("_url", ignoreCase = true) ||
            key.equals("video", ignoreCase = true) ||
            key.equals("videos", ignoreCase = true) ||
            key.equals("image", ignoreCase = true) ||
            key.equals("images", ignoreCase = true) ||
            key.equals("audio", ignoreCase = true) ||
            key.equals("file", ignoreCase = true) ||
            key.equals("output", ignoreCase = true)

    private fun JsonObject.looksLikeString(): Boolean = this["type"]?.jsonPrimitive?.contentOrNull == "string"

    private fun JsonObject.looksLikeFileShape(): Boolean {
        val properties = this["properties"]?.jsonObject ?: return false
        return properties.containsKey("url")
    }

    private class OpenApiSchemaResolver(
        private val schemas: JsonObject,
    ) {
        fun resolveRef(ref: String): JsonObject? {
            val name = ref.substringAfterLast('/')
            return schemas[name] as? JsonObject
        }
    }

    private const val PRIMARY_PATH_PRIORITY = 0
}
