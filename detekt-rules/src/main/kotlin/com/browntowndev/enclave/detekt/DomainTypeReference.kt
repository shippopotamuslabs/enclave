package com.browntowndev.enclave.detekt

import org.jetbrains.kotlin.psi.KtImportDirective

internal object DomainTypeReference {
    const val DOMAIN_PACKAGE_PREFIX = "com.browntowndev.enclave.domain"

    private val ALLOWED_SIMPLE_TYPES =
        setOf(
            "String",
            "Int",
            "Boolean",
            "Float",
            "Double",
            "Long",
            "Short",
            "Byte",
            "Char",
            "Unit",
            "Modifier",
            "Any",
        )

    fun collectDomainImportedTypes(importDirective: KtImportDirective, current: Set<String>): Set<String> {
        val importPath = importDirective.importPath?.pathStr ?: return current
        if (!importPath.startsWith(DOMAIN_PACKAGE_PREFIX)) {
            return current
        }
        val simpleName =
            importDirective.importedName?.asString()
                ?: importPath.substringAfterLast('.')
        return current + simpleName
    }

    fun isAllowedType(typeText: String): Boolean {
        if (typeText.contains("->")) {
            return true
        }
        val root = typeText.substringBefore('<').trim()
        return root in ALLOWED_SIMPLE_TYPES
    }

    fun typeReferencesDomain(typeText: String, domainTypes: Set<String>): Boolean {
        val tokens =
            typeText
                .split(TOKEN_SPLIT_REGEX)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        return tokens.any { token ->
            token.startsWith(DOMAIN_PACKAGE_PREFIX) || token in domainTypes
        }
    }

    private val TOKEN_SPLIT_REGEX = Regex("[<>,\\s()?.]+")
}
