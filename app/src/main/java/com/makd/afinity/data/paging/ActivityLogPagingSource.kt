package com.makd.afinity.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.makd.afinity.data.models.admin.ActivityEntry
import com.makd.afinity.data.models.admin.ActivityFilter
import com.makd.afinity.data.repository.JellyfinApiInvoker
import org.jellyfin.sdk.api.operations.SystemApi
import org.jellyfin.sdk.model.api.ActivityLogEntry
import org.jellyfin.sdk.model.api.LogLevel

class ActivityLogPagingSource(
    private val apiInvoker: JellyfinApiInvoker,
    private val filter: ActivityFilter,
) : PagingSource<Int, ActivityEntry>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ActivityEntry> {
        val startIndex = params.key ?: 0
        val result = apiInvoker.apiResult { api, _ ->
            SystemApi(api)
                .getLogEntries(
                    startIndex = startIndex,
                    limit = params.loadSize,
                    hasUserId = filter.hasUserId,
                    type = filter.type,
                )
                .content
        }
        val page = result.getOrElse { e ->
            return LoadResult.Error(e)
        }
        val entries = page.items.map { it.toActivityEntry() }
        val loaded = startIndex + entries.size
        return LoadResult.Page(
            data = entries,
            prevKey = null,
            nextKey = if (entries.isEmpty() || loaded >= page.totalRecordCount) null else loaded,
        )
    }

    override fun getRefreshKey(state: PagingState<Int, ActivityEntry>): Int? = null
}

private fun ActivityLogEntry.toActivityEntry() =
    ActivityEntry(
        id = id,
        title = name,
        detail = shortOverview?.takeIf { it.isNotBlank() },
        type = type,
        date = date,
        isError = severity == LogLevel.ERROR || severity == LogLevel.CRITICAL,
    )
