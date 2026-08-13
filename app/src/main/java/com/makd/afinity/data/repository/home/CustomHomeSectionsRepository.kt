package com.makd.afinity.data.repository.home

import com.makd.afinity.data.database.dao.CustomHomeSectionDao
import com.makd.afinity.data.database.entities.CustomHomeSectionEntity
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.CustomHomeSection
import com.makd.afinity.data.models.CustomSectionCardStyle
import com.makd.afinity.data.models.CustomSectionSourceType
import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.media.LibraryFilters
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.time.MonthDay
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomHomeSectionsRepository
@Inject
constructor(
    private val dao: CustomHomeSectionDao,
    private val sessionManager: SessionManager,
) {

    private fun sessionKey(): String? {
        val session = sessionManager.currentSession.value ?: return null
        if (session.serverId.isBlank()) return null
        return "${session.serverId}_${session.userId}"
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val sections: Flow<List<CustomHomeSection>> =
        sessionManager.currentSession.flatMapLatest { session ->
            val key =
                session?.takeIf { it.serverId.isNotBlank() }?.let { "${it.serverId}_${it.userId}" }
            if (key == null) flowOf(emptyList())
            else dao.observeForSession(key).map { rows -> rows.map { it.toDomain() } }
        }

    suspend fun getAll(): List<CustomHomeSection> {
        val key = sessionKey() ?: return emptyList()
        return dao.getForSession(key).map { it.toDomain() }
    }

    suspend fun get(id: String): CustomHomeSection? = dao.getById(id)?.toDomain()

    suspend fun canAddMore(): Boolean {
        val key = sessionKey() ?: return false
        return dao.countForSession(key) < CustomHomeSection.MAX_SECTIONS
    }

    suspend fun upsert(section: CustomHomeSection): Boolean {
        val key = sessionKey() ?: return false
        return try {
            val position = if (section.position >= 0) section.position else dao.maxPosition(key) + 1
            dao.upsert(section.copy(position = position).toEntity(key))
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to save custom home section ${section.id}")
            false
        }
    }

    suspend fun create(section: CustomHomeSection): Boolean {
        if (!canAddMore()) return false
        return upsert(section.copy(id = UUID.randomUUID().toString(), position = -1))
    }

    suspend fun delete(id: String) {
        try {
            dao.deleteById(id)
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete custom home section $id")
        }
    }

    suspend fun reorder(orderedIds: List<String>) {
        try {
            dao.applyOrder(orderedIds)
        } catch (e: Exception) {
            Timber.e(e, "Failed to reorder custom home sections")
        }
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        val existing = dao.getById(id) ?: return
        dao.upsert(existing.copy(enabled = enabled))
    }

    suspend fun clearForCurrentSession() {
        val key = sessionKey() ?: return
        dao.deleteForSession(key)
    }

    private fun CustomHomeSectionEntity.toDomain(): CustomHomeSection =
        CustomHomeSection(
            id = id,
            position = position,
            title = title,
            sourceType =
                runCatching { CustomSectionSourceType.valueOf(sourceType) }
                    .getOrDefault(CustomSectionSourceType.GENRE),
            sourceValues =
                sourceValue.split(CustomHomeSection.SOURCE_DELIMITER).filter { it.isNotBlank() },
            includeItemTypes = includeItemTypes.split(',').filter { it.isNotBlank() },
            itemLimit = itemLimit,
            sortBy = runCatching { SortBy.valueOf(sortBy) }.getOrDefault(SortBy.NAME),
            sortDescending = sortDescending,
            randomOrder = randomOrder,
            cardStyle =
                runCatching { CustomSectionCardStyle.valueOf(cardStyle) }
                    .getOrDefault(CustomSectionCardStyle.PORTRAIT),
            enabled = enabled,
            seasonStart = seasonStart,
            seasonEnd = seasonEnd,
            filters =
                filtersJson
                    ?.takeIf { it.isNotBlank() }
                    ?.let {
                        runCatching { filtersCodec.decodeFromString<LibraryFilters>(it) }
                            .onFailure { e ->
                                Timber.w(e, "Dropping unreadable filters for custom section $id")
                            }
                            .getOrNull()
                    } ?: LibraryFilters(),
        )

    private fun CustomHomeSection.toEntity(key: String): CustomHomeSectionEntity =
        CustomHomeSectionEntity(
            id = id,
            sessionKey = key,
            position = position,
            title = title,
            sourceType = sourceType.name,
            sourceValue = sourceValues.joinToString(CustomHomeSection.SOURCE_DELIMITER),
            includeItemTypes = includeItemTypes.joinToString(","),
            itemLimit = itemLimit,
            sortBy = sortBy.name,
            sortDescending = sortDescending,
            randomOrder = randomOrder,
            cardStyle = cardStyle.name,
            enabled = enabled,
            seasonStart = seasonStart,
            seasonEnd = seasonEnd,
            filtersJson = if (filters.isEmpty) null else filtersCodec.encodeToString(filters),
        )

    companion object {
        private val filtersCodec = Json { ignoreUnknownKeys = true }

        fun isInSeason(
            section: CustomHomeSection,
            today: MonthDay = MonthDay.now(),
        ): Boolean {
            val start = section.seasonStart?.let { parseMonthDay(it) } ?: return true
            val end = section.seasonEnd?.let { parseMonthDay(it) } ?: return true
            return if (start <= end) {
                today >= start && today <= end
            } else {
                today >= start || today <= end
            }
        }

        private fun parseMonthDay(value: String): MonthDay? = runCatching {
            MonthDay.parse("--$value")
        }
            .getOrNull()
    }
}
