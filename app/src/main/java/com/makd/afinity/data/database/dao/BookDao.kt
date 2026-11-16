package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.makd.afinity.data.database.entities.AfinityAudiobookPartDto
import com.makd.afinity.data.database.entities.AfinityBookDto
import java.util.UUID

@Dao
interface BookDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertBook(book: AfinityBookDto)

    @Query("SELECT * FROM books WHERE id = :bookId AND serverId = :serverId")
    suspend fun getBook(bookId: UUID, serverId: String): AfinityBookDto?

    @Query(
        "SELECT * FROM books WHERE serverId = :serverId AND seriesName = :seriesName COLLATE NOCASE",
    )
    suspend fun getBooksInSeries(serverId: String, seriesName: String): List<AfinityBookDto>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudiobookPart(part: AfinityAudiobookPartDto)

    @Query("SELECT * FROM audiobook_parts WHERE id = :partId AND serverId = :serverId")
    suspend fun getAudiobookPart(partId: UUID, serverId: String): AfinityAudiobookPartDto?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAudiobookPartsIfAbsent(parts: List<AfinityAudiobookPartDto>)

    @Query(
        "UPDATE audiobook_parts SET bookPartCount = :count WHERE id IN (:partIds) AND serverId = :serverId",
    )
    suspend fun updateBookPartCount(partIds: List<UUID>, serverId: String, count: Int)

    @Query("SELECT bookPartCount FROM audiobook_parts WHERE id = :partId AND serverId = :serverId")
    suspend fun getBookPartCount(partId: UUID, serverId: String): Int?

    @Query("DELETE FROM books WHERE id = :bookId") suspend fun deleteBook(bookId: UUID)

    @Query("DELETE FROM audiobook_parts WHERE id = :partId")
    suspend fun deleteAudiobookPart(partId: UUID)
}
