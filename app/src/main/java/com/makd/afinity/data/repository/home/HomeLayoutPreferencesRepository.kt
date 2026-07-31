package com.makd.afinity.data.repository.home

import com.makd.afinity.data.database.dao.HomeLayoutPreferenceDao
import com.makd.afinity.data.database.entities.HomeLayoutPreferenceEntity
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.DiscoveryConfig
import com.makd.afinity.data.models.DiscoveryDensity
import com.makd.afinity.data.models.DiscoverySection
import com.makd.afinity.data.models.HomeRow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeLayoutPreferencesRepository
@Inject
constructor(
    private val dao: HomeLayoutPreferenceDao,
    private val sessionManager: SessionManager,
) {

    private fun sessionKey(): String? {
        val session = sessionManager.currentSession.value ?: return null
        if (session.serverId.isBlank()) return null
        return "${session.serverId}_${session.userId}"
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val hiddenRows: Flow<Set<HomeRow>> =
        sessionManager.currentSession.flatMapLatest { session ->
            val key =
                session?.takeIf { it.serverId.isNotBlank() }?.let { "${it.serverId}_${it.userId}" }
            if (key == null) {
                flowOf(emptySet())
            } else {
                dao.observeForSession(key).map { rows ->
                    rows.filterNot { it.visible }
                        .mapNotNull { HomeRow.fromKey(it.sectionKey) }
                        .filterNot { it.mandatory }
                        .toSet()
                }
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    val discoveryConfig: Flow<DiscoveryConfig> =
        sessionManager.currentSession.flatMapLatest { session ->
            val key =
                session?.takeIf { it.serverId.isNotBlank() }?.let { "${it.serverId}_${it.userId}" }
            if (key == null) {
                flowOf(DiscoveryConfig())
            } else {
                dao.observeForSession(key).map { rows -> rows.toDiscoveryConfig() }
            }
        }

    suspend fun getHiddenRows(): Set<HomeRow> {
        val key = sessionKey() ?: return emptySet()
        return try {
            dao.getForSession(key)
                .filterNot { it.visible }
                .mapNotNull { HomeRow.fromKey(it.sectionKey) }
                .filterNot { it.mandatory }
                .toSet()
        } catch (e: Exception) {
            Timber.e(e, "Failed to read hidden home rows")
            emptySet()
        }
    }

    suspend fun getDiscoveryConfig(): DiscoveryConfig {
        val key = sessionKey() ?: return DiscoveryConfig()
        return try {
            dao.getForSession(key).toDiscoveryConfig()
        } catch (e: Exception) {
            Timber.e(e, "Failed to read discovery config")
            DiscoveryConfig()
        }
    }

    private fun List<HomeLayoutPreferenceEntity>.toDiscoveryConfig(): DiscoveryConfig {
        val density =
            firstOrNull { it.sectionKey == DiscoverySection.DENSITY_KEY }
                ?.let { DiscoveryDensity.entries.getOrNull(it.position) }
                ?: DiscoveryDensity.default
        val disabled = mutableSetOf<DiscoverySection>()
        val overrides = mutableMapOf<DiscoverySection, Int>()
        forEach { row ->
            val section = DiscoverySection.fromKey(row.sectionKey) ?: return@forEach
            if (!row.visible) disabled.add(section)
            row.maxCount?.let { overrides[section] = it }
        }
        return DiscoveryConfig(density = density, disabled = disabled, overrides = overrides)
    }

    suspend fun setDiscoveryDensity(density: DiscoveryDensity) {
        val key = sessionKey() ?: return
        try {
            dao.upsert(
                HomeLayoutPreferenceEntity(
                    sessionKey = key,
                    sectionKey = DiscoverySection.DENSITY_KEY,
                    position = density.ordinal,
                    visible = true,
                    maxCount = null,
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to save discovery density")
        }
    }

    suspend fun setDiscoverySection(
        section: DiscoverySection,
        enabled: Boolean,
        maxCount: Int?,
    ) {
        val key = sessionKey() ?: return
        try {
            dao.upsert(
                HomeLayoutPreferenceEntity(
                    sessionKey = key,
                    sectionKey = section.key,
                    position = section.ordinal,
                    visible = enabled,
                    maxCount = maxCount?.coerceIn(1, section.ceiling),
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to save discovery section ${section.key}")
        }
    }

    suspend fun setRowVisible(row: HomeRow, visible: Boolean) {
        if (row.mandatory) return
        val key = sessionKey() ?: return
        try {
            dao.upsert(
                HomeLayoutPreferenceEntity(
                    sessionKey = key,
                    sectionKey = row.key,
                    position = row.ordinal,
                    visible = visible,
                    maxCount = null,
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to save home row visibility for ${row.key}")
        }
    }

    suspend fun resetRows() {
        val key = sessionKey() ?: return
        try {
            dao.deleteForSession(key)
        } catch (e: Exception) {
            Timber.e(e, "Failed to reset home layout preferences")
        }
    }
}