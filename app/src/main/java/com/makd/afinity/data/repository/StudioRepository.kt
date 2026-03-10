package com.makd.afinity.data.repository

import com.makd.afinity.data.database.AfinityDatabase
import com.makd.afinity.data.database.AfinityTypeConverters
import com.makd.afinity.data.database.entities.StudioCacheEntity
import com.makd.afinity.data.models.media.AfinityStudio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.hours

@Singleton
class StudioRepository
@Inject
constructor(private val jellyfinRepository: JellyfinRepository, database: AfinityDatabase) {
    private val studioCacheTTL = 6.hours.inWholeMilliseconds
    private val studioCacheDao = database.studioCacheDao()
    private val afinityTypeConverters = AfinityTypeConverters()

    private val _studios = MutableStateFlow<List<AfinityStudio>>(emptyList())
    val studios: StateFlow<List<AfinityStudio>> = _studios.asStateFlow()

    suspend fun loadStudios() {
        try {
            val cachedStudios = studioCacheDao.getAllCachedStudios()
            if (cachedStudios.isNotEmpty()) {
                val studiosList =
                    cachedStudios.mapNotNull { entity ->
                        afinityTypeConverters.toAfinityStudio(entity.studioData)
                    }

                if (studiosList.isNotEmpty()) {
                    _studios.value = studiosList
                    val currentTime = System.currentTimeMillis()
                    val isFresh = studioCacheDao.isStudioCacheFresh(studioCacheTTL, currentTime)
                    if (isFresh) return
                }
            }

            val studiosList: List<AfinityStudio> =
                jellyfinRepository.getStudios(
                    parentId = null,
                    limit = 15,
                    includeItemTypes = listOf("MOVIE", "SERIES"),
                )

            if (studiosList.isNotEmpty()) {
                val timestamp = System.currentTimeMillis()
                val studioEntities =
                    studiosList.mapIndexed { index: Int, studio: AfinityStudio ->
                        StudioCacheEntity(
                            studioId = studio.id.toString(),
                            studioData = afinityTypeConverters.fromAfinityStudio(studio) ?: "",
                            position = index,
                            cachedTimestamp = timestamp,
                        )
                    }
                studioCacheDao.replaceStudios(studioEntities)
            }

            _studios.value = studiosList
        } catch (e: Exception) {
            Timber.e(e, "Failed to load studios")
        }
    }

    suspend fun clearAllData() {
        try {
            studioCacheDao.deleteAllStudios()
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear studio caches")
        }
        _studios.value = emptyList()
    }
}
