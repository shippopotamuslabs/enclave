package com.browntowndev.enclave.domain.model.download

import com.browntowndev.enclave.domain.model.config.UtilityType

/**
 * Resolves unique on-disk filenames when multiple catalog assets share the same remote
 * [CatalogFileIdentity.fileName] but represent different physical files (distinct SHA-256).
 *
 * Remote download URLs continue to use the catalog fileName; only [resolveLocalFileName]
 * is prefixed so utilities such as the image and prompt safety classifiers do not overwrite
 * each other at `models/model_quantized.onnx`.
 */
data class CatalogFileIdentity(
    val fileName: String,
    val sha256: String,
    val huggingFacePath: String? = null,
    val utilityType: UtilityType? = null,
)

object LocalFileNameDisambiguator {
    fun resolveLocalFileName(
        identity: CatalogFileIdentity,
        catalog: List<CatalogFileIdentity>,
    ): String {
        val shaVariants =
            catalog
                .filter { it.fileName == identity.fileName }
                .map { it.sha256 }
                .toSet()
        if (shaVariants.size <= 1) {
            return identity.fileName
        }
        val prefix =
            identity.huggingFacePath?.takeIf { it.isNotBlank() }
                ?: identity.utilityType?.apiValue
                ?: identity.sha256.take(8)
        val sanitizedPrefix = prefix.replace('/', '_').replace('\\', '_')
        return "${sanitizedPrefix}_${identity.fileName}"
    }

    /**
     * Catalog fileNames that were previously stored flat on disk but are now disambiguated
     * and no longer referenced as a local target by any expected asset.
     */
    fun orphanedLegacyFilenames(catalog: List<CatalogFileIdentity>): Set<String> {
        if (catalog.isEmpty()) {
            return emptySet()
        }
        val resolvedLocalNames =
            catalog
                .map { identity -> resolveLocalFileName(identity, catalog) }
                .toSet()
        return catalog
            .filter { identity -> resolveLocalFileName(identity, catalog) != identity.fileName }
            .map { it.fileName }
            .toSet()
            .filterNot { it in resolvedLocalNames }
            .toSet()
    }
}
