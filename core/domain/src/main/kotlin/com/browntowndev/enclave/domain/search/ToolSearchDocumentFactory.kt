package com.browntowndev.enclave.domain.search

import com.browntowndev.enclave.domain.model.inference.ToolCatalogEntry
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.inference.ToolSearchDocument

fun ToolCatalogEntry.toSearchDocument(): ToolSearchDocument {
    val argNames =
        (requiredArguments.map { it.name } + optionalArguments.map { it.name })
            .joinToString(" ")
    val tags = categoryTags.joinToString(" ")
    val searchableText =
        listOf(name, description, argNames, tags)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .lowercase()
    return ToolSearchDocument(toolName = name, searchableText = searchableText)
}

fun ToolDefinition.toSearchDocument(): ToolSearchDocument = catalogEntry().toSearchDocument()
