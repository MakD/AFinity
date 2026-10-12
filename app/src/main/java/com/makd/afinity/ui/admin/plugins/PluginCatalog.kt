package com.makd.afinity.ui.admin.plugins

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.util.UUID
import org.jellyfin.sdk.model.api.PackageInfo
import org.jellyfin.sdk.model.api.PluginInfo
import org.jellyfin.sdk.model.api.PluginStatus

enum class PluginTab {
    INSTALLED,
    AVAILABLE,
}

data class PluginVersionRow(val version: String, val releasedAt: Instant?, val changelog: String)

data class CatalogPluginRow(
    val id: UUID,
    val name: String,
    val summary: String,
    val description: String,
    val category: String,
    val owner: String,
    val latestVersion: String,
    val repositoryName: String,
    val repositoryUrl: String,
    val installedVersion: String?,
    val imageUrl: String? = null,
    val versions: List<PluginVersionRow> = emptyList(),
) {
    val isInstalled: Boolean
        get() = installedVersion != null

    val hasUpdate: Boolean
        get() =
            installedVersion != null && comparePluginVersions(latestVersion, installedVersion) > 0
}

data class PluginCardRow(
    val id: UUID,
    val name: String,
    val summary: String,
    val category: String?,
    val imageUrl: String?,
    val version: String,
    val status: PluginStatus?,
    val isBuiltIn: Boolean,
    val catalog: CatalogPluginRow?,
) {
    val isInstalled: Boolean
        get() = status != null

    val hasUpdate: Boolean
        get() = catalog?.hasUpdate == true

    val installable: CatalogPluginRow?
        get() = catalog?.takeIf { !isInstalled || it.hasUpdate }
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

internal fun currentPluginVersions(installed: List<PluginInfo>): List<PluginInfo> =
    installed
        .groupBy { it.id }
        .values
        .mapNotNull { rows ->
            val live = rows.filter {
                it.status != PluginStatus.DELETED &&
                    it.status != PluginStatus.SUPERSEDED &&
                    it.status != PluginStatus.SUPERCEDED
            }
            live
                .ifEmpty { rows }
                .maxWithOrNull { first, second ->
                    comparePluginVersions(first.version, second.version)
                }
        }

internal fun pluginImageUrl(plugin: PluginInfo, baseUrl: String?): String? =
    if (plugin.hasImage && baseUrl != null) {
        "$baseUrl/Plugins/${plugin.id}/${plugin.version}/Image"
    } else {
        null
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
        val versions =
            pluginPackage.versions.sortedWith { first, second ->
                comparePluginVersions(second.version, first.version)
            }
        val latest = versions.firstOrNull() ?: return@mapNotNull null
        CatalogPluginRow(
            id = pluginPackage.guid,
            name = pluginPackage.name,
            summary = pluginPackage.overview.ifBlank { pluginPackage.description },
            description = pluginPackage.description.ifBlank { pluginPackage.overview },
            category = pluginPackage.category,
            owner = pluginPackage.owner,
            latestVersion = latest.version,
            repositoryName = latest.repositoryName,
            repositoryUrl = latest.repositoryUrl,
            installedVersion = installedVersions[pluginPackage.guid],
            imageUrl = pluginPackage.imageUrl?.takeIf { it.isNotBlank() },
            versions =
                versions.map { version ->
                    PluginVersionRow(
                        version = version.version,
                        releasedAt = parsePluginTimestamp(version.timestamp),
                        changelog = cleanChangelog(version.changelog),
                    )
                },
        )
    }
}

internal fun buildPluginCards(
    installed: List<PluginInfo>,
    packages: List<PackageInfo>?,
    baseUrl: String?,
): List<PluginCardRow> {
    val catalog = packages?.let { buildCatalogRows(it, installed) }.orEmpty().associateBy { it.id }
    val current = currentPluginVersions(installed)
    val installedIds = current.map { it.id }.toSet()
    val installedCards = current.map { plugin ->
        val entry = catalog[plugin.id]
        PluginCardRow(
            id = plugin.id,
            name = plugin.name,
            summary = entry?.summary ?: plugin.description,
            category = entry?.category,
            imageUrl = pluginImageUrl(plugin, baseUrl) ?: entry?.imageUrl,
            version = plugin.version,
            status = plugin.status,
            isBuiltIn = !plugin.canUninstall,
            catalog = entry,
        )
    }
    val availableCards =
        catalog.values
            .filter { it.id !in installedIds }
            .map { entry ->
                PluginCardRow(
                    id = entry.id,
                    name = entry.name,
                    summary = entry.summary,
                    category = entry.category,
                    imageUrl = entry.imageUrl,
                    version = entry.latestVersion,
                    status = null,
                    isBuiltIn = false,
                    catalog = entry,
                )
            }
    return installedCards + availableCards
}

private fun parsePluginTimestamp(timestamp: String?): Instant? {
    if (timestamp.isNullOrBlank()) return null
    return try {
        Instant.parse(timestamp)
    } catch (_: DateTimeParseException) {
        try {
            LocalDateTime.parse(timestamp).toInstant(ZoneOffset.UTC)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}

private fun cleanChangelog(changelog: String?): String =
    changelog
        .orEmpty()
        .lines()
        .map { it.trim().removePrefix("- ").removePrefix("* ") }
        .filter { it.isNotEmpty() }
        .joinToString("\n")
