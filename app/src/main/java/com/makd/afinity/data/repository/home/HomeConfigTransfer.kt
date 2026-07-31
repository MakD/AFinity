package com.makd.afinity.data.repository.home

import com.makd.afinity.BuildConfig
import com.makd.afinity.data.models.CustomHomeSection
import com.makd.afinity.data.models.CustomSectionCardStyle
import com.makd.afinity.data.models.CustomSectionExport
import com.makd.afinity.data.models.CustomSectionSourceType
import com.makd.afinity.data.models.DiscoveryConfig
import com.makd.afinity.data.models.DiscoveryDensity
import com.makd.afinity.data.models.DiscoveryExport
import com.makd.afinity.data.models.DiscoverySection
import com.makd.afinity.data.models.HomeConfigExport
import com.makd.afinity.data.models.HomeRow
import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.repository.media.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ImportResult {
    data class Ready(val plan: ImportPlan) : ImportResult

    data class Failed(val reason: ImportFailure) : ImportResult
}

enum class ImportFailure {
    NOT_AFINITY_CONFIG,
    NEWER_SCHEMA,
    UNREADABLE,
}

data class SkippedSection(val title: String, val reason: SkipReason)

enum class SkipReason {
    SOURCE_NOT_ON_SERVER,
    INVALID,
    LIMIT_REACHED,
}

data class ImportPlan(
    val sections: List<CustomHomeSection>,
    val skipped: List<SkippedSection>,
    val hiddenRows: Set<HomeRow>,
    val discovery: DiscoveryConfig,
    val existingSectionCount: Int,
    val exportedAt: String,
    val appVersion: String,
)

@Singleton
class HomeConfigTransfer
@Inject
constructor(
    private val customHomeSectionsRepository: CustomHomeSectionsRepository,
    private val homeLayoutPreferencesRepository: HomeLayoutPreferencesRepository,
    private val mediaRepository: MediaRepository,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun export(): String =
        withContext(Dispatchers.IO) {
            val discovery = homeLayoutPreferencesRepository.getDiscoveryConfig()
            val export =
                HomeConfigExport(
                    exportedAt = Instant.now().toString(),
                    appVersion = BuildConfig.VERSION_NAME,
                    hiddenRows = homeLayoutPreferencesRepository.getHiddenRows().map { it.key },
                    discovery =
                        DiscoveryExport(
                            density = discovery.density.key,
                            disabled = discovery.disabled.map { it.key },
                            overrides = discovery.overrides.entries.associate { it.key.key to it.value },
                        ),
                    customSections = customHomeSectionsRepository.getAll().map { it.toExport() },
                )
            json.encodeToString(HomeConfigExport.serializer(), export)
        }

    suspend fun parse(raw: String): ImportResult =
        withContext(Dispatchers.IO) {
            val export =
                try {
                    json.decodeFromString(HomeConfigExport.serializer(), raw)
                } catch (e: Exception) {
                    Timber.w(e, "Failed to parse home config")
                    return@withContext ImportResult.Failed(ImportFailure.UNREADABLE)
                }

            if (export.format != HomeConfigExport.FORMAT) {
                return@withContext ImportResult.Failed(ImportFailure.NOT_AFINITY_CONFIG)
            }
            if (export.schemaVersion > HomeConfigExport.SCHEMA_VERSION) {
                return@withContext ImportResult.Failed(ImportFailure.NEWER_SCHEMA)
            }

            val sections = mutableListOf<CustomHomeSection>()
            val skipped = mutableListOf<SkippedSection>()

            export.customSections.forEachIndexed { index, entry ->
                val section = entry.toDomain(index)
                when {
                    section == null ->
                        skipped.add(SkippedSection(entry.title, SkipReason.INVALID))
                    sections.size >= CustomHomeSection.MAX_SECTIONS ->
                        skipped.add(SkippedSection(entry.title, SkipReason.LIMIT_REACHED))
                    !sourceExists(section) ->
                        skipped.add(
                            SkippedSection(entry.title, SkipReason.SOURCE_NOT_ON_SERVER)
                        )
                    else -> sections.add(section)
                }
            }

            ImportResult.Ready(
                ImportPlan(
                    sections = sections,
                    skipped = skipped,
                    hiddenRows =
                        export.hiddenRows
                            .mapNotNull { HomeRow.fromKey(it) }
                            .filterNot { it.mandatory }
                            .toSet(),
                    discovery = export.discovery.toDomain(),
                    existingSectionCount = customHomeSectionsRepository.getAll().size,
                    exportedAt = export.exportedAt,
                    appVersion = export.appVersion,
                )
            )
        }

    suspend fun apply(plan: ImportPlan) =
        withContext(Dispatchers.IO) {
            customHomeSectionsRepository.clearForCurrentSession()
            plan.sections.forEach { customHomeSectionsRepository.create(it) }

            HomeRow.configurable.forEach { row ->
                homeLayoutPreferencesRepository.setRowVisible(row, row !in plan.hiddenRows)
            }

            homeLayoutPreferencesRepository.setDiscoveryDensity(plan.discovery.density)
            DiscoverySection.entries.forEach { section ->
                homeLayoutPreferencesRepository.setDiscoverySection(
                    section = section,
                    enabled = section !in plan.discovery.disabled,
                    maxCount = plan.discovery.overrides[section],
                )
            }
        }

    private suspend fun sourceExists(section: CustomHomeSection): Boolean {
        if (!section.sourceType.usesItemIds) return true
        val id =
            runCatching { UUID.fromString(section.primarySourceValue.orEmpty()) }.getOrNull()
                ?: return false
        return runCatching { mediaRepository.getItem(id) != null }.getOrDefault(false)
    }
}

private fun CustomHomeSection.toExport(): CustomSectionExport =
    CustomSectionExport(
        title = title,
        sourceType = sourceType.name,
        sourceValues = sourceValues,
        includeItemTypes = includeItemTypes,
        itemLimit = itemLimit,
        sortBy = sortBy.name,
        sortDescending = sortDescending,
        randomOrder = randomOrder,
        cardStyle = cardStyle.name,
        enabled = enabled,
        seasonStart = seasonStart,
        seasonEnd = seasonEnd,
    )

private fun CustomSectionExport.toDomain(position: Int): CustomHomeSection? {
    if (title.isBlank() || sourceValues.isEmpty()) return null
    val type =
        runCatching { CustomSectionSourceType.valueOf(sourceType) }.getOrNull() ?: return null
    return CustomHomeSection(
            id = UUID.randomUUID().toString(),
            position = position,
            title = title,
            sourceType = type,
            sourceValues = sourceValues,
            includeItemTypes = includeItemTypes,
            itemLimit = itemLimit.coerceIn(1, 50),
            sortBy = runCatching { SortBy.valueOf(sortBy) }.getOrDefault(SortBy.NAME),
            sortDescending = sortDescending,
            randomOrder = randomOrder,
            cardStyle =
                runCatching { CustomSectionCardStyle.valueOf(cardStyle) }
                    .getOrDefault(CustomSectionCardStyle.PORTRAIT),
            enabled = enabled,
            seasonStart = seasonStart,
            seasonEnd = seasonEnd,
        )
        .withSanitizedItemTypes()
}

private fun DiscoveryExport.toDomain(): DiscoveryConfig =
    DiscoveryConfig(
        density = DiscoveryDensity.fromKey(density),
        disabled = disabled.mapNotNull { DiscoverySection.fromKey(it) }.toSet(),
        overrides =
            overrides
                .mapNotNull { (key, value) ->
                    DiscoverySection.fromKey(key)?.let { it to value.coerceIn(0, it.ceiling) }
                }
                .toMap(),
    )