package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.makd.afinity.data.database.entities.WatchlistItemEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface WatchlistDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToWatchlist(item: WatchlistItemEntity)

    @Delete
    suspend fun removeFromWatchlist(item: WatchlistItemEntity)

    @Query("DELETE FROM watchlist WHERE itemId = :itemId")
    suspend fun removeFromWatchlistById(itemId: UUID)

    @Query("SELECT * FROM watchlist WHERE itemId = :itemId LIMIT 1")
    suspend fun getWatchlistItem(itemId: UUID): WatchlistItemEntity?

    @Query("SELECT * FROM watchlist ORDER BY addedAt DESC")
    suspend fun getAllWatchlistItems(): List<WatchlistItemEntity>

    @Query("SELECT * FROM watchlist ORDER BY addedAt DESC")
    fun getAllWatchlistItemsFlow(): Flow<List<WatchlistItemEntity>>

    @Query("SELECT * FROM watchlist WHERE itemType = :itemType ORDER BY addedAt DESC")
    suspend fun getWatchlistItemsByType(itemType: String): List<WatchlistItemEntity>

    @Query("SELECT COUNT(*) FROM watchlist")
    suspend fun getWatchlistCount(): Int

    @Query("SELECT COUNT(*) FROM watchlist")
    fun getWatchlistCountFlow(): Flow<Int>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE itemId = :itemId)")
    suspend fun isInWatchlist(itemId: UUID): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE itemId = :itemId)")
    fun isInWatchlistFlow(itemId: UUID): Flow<Boolean>

    @Query("DELETE FROM watchlist")
    suspend fun clearWatchlist()
}