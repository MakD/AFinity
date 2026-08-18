package com.makd.afinity.data.store

import androidx.paging.PagingData
import androidx.paging.map
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityUserDataOwner
import com.makd.afinity.data.models.media.withUserData
import com.makd.afinity.data.models.media.withUserDataFrom
import com.makd.afinity.data.models.user.AfinityUserDataDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.UUID

fun <T : AfinityItem> Flow<PagingData<T>>.withUserDataOverlay(
    persisted: Flow<Map<UUID, AfinityUserDataDto>>,
    itemStore: ItemStore,
): Flow<PagingData<T>> =
    combine(persisted) { pagingData, rows ->
            if (rows.isEmpty()) {
                pagingData
            } else {
                pagingData.map { item ->
                    val row = rows[item.id] ?: return@map item
                    @Suppress("UNCHECKED_CAST")
                    item.withUserData(row) as T
                }
            }
        }
        .combine(itemStore.overlay) { pagingData, updates ->
            if (updates.isEmpty()) {
                pagingData
            } else {
                pagingData.map { item ->
                    val source = updates[item.id] ?: return@map item
                    @Suppress("UNCHECKED_CAST")
                    mergedWith(item, source) as T
                }
            }
        }

private fun mergedWith(item: AfinityItem, source: AfinityUserDataOwner): AfinityItem =
    if (source is AfinityItem && source::class == item::class) {
        source
    } else {
        item.withUserDataFrom(source)
    }