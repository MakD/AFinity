package com.makd.afinity.data.repository.books

import com.makd.afinity.data.models.media.BookAnnotation
import com.makd.afinity.data.models.media.HighlightColor
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface BookAnnotationRepository {
    fun observe(itemId: UUID): Flow<List<BookAnnotation>>

    suspend fun save(itemId: UUID, annotation: BookAnnotation)

    suspend fun setColor(id: String, color: HighlightColor)

    suspend fun delete(id: String)

    suspend fun lastHighlightColor(): HighlightColor

    suspend fun setLastHighlightColor(color: HighlightColor)
}
