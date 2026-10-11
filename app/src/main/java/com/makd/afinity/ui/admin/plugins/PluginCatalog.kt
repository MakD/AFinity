package com.makd.afinity.ui.admin.plugins

import java.util.UUID
import org.jellyfin.sdk.model.api.PackageInfo
import org.jellyfin.sdk.model.api.PluginInfo
import org.jellyfin.sdk.model.api.PluginStatus

data class CatalogPluginRow(
    val id: UUID,
    val name: String,
    val summary: String,
    val category: String,
    val owner: String,
    val latestVersion: String,
    val repositoryName: String,
    val repositoryUrl: String,
    val installedVersion: String?,
    val imageUrl: String? = null,
    val changelog: String = "",
) {
    val isInstalled: Boolean
        get() = installedVersion != null

    val hasUpdate: Boolean
        get() =
            installedVersion != null && comparePluginVersions(latestVersion, installedVersion) > 0
}

internal fun comparePluginVersions(first: String, second: String): Int {
    val firstParts = first.split('.').map { it.toIntOrNull() ?: 0 }
    val secondParts = second.split('.').map { it.toIntOrNull() ?: 0 }
    for (index in 0 until maxOf(firstParts.size, secondParts.size)) {
        val difference =
            firstParts.getOrElse(index) { 0 }.compareTo(secondParts.getOrElse(index) { 0 })
        if (difference != 0) return difference
    }
    return 0
}

internal fun buildCatalogRows(
    packages: List<PackageInfo>,
    installed: List<PluginInfo>,
): List<CatalogPluginRow> {
    val installedVersions =
        installed
            .filter { it.status != PluginStatus.DELETED }
            .groupBy { it.id }
            .mapValues { (_, versions) ->
                versions
                    .map { it.version }
                    .maxWithOrNull { first, second -> comparePluginVersions(first, second) }
            }
    return packages.mapNotNull { pluginPackage ->
        val latest =
            pluginPackage.versions.maxWithOrNull { first, second ->
                comparePluginVersions(first.version, second.version)
            } ?: return@mapNotNull null
        CatalogPluginRow(
            id = pluginPackage.guid,
            name = pluginPackage.name,
            summary = pluginPackage.overview.ifBlank { pluginPackage.description },
            category = pluginPackage.category,
            owner = pluginPackage.owner,
            latestVersion = latest.version,
            repositoryName = latest.repositoryName,
            repositoryUrl = latest.repositoryUrl,
            installedVersion = installedVersions[pluginPackage.guid],
            imageUrl = pluginPackage.imageUrl?.takeIf { it.isNotBlank() },
            changelog = latest.changelog.orEmpty().trim(),
        )
    }
}
