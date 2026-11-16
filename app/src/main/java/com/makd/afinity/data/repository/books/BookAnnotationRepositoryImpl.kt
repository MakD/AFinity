package com.makd.afinity.data.repository.books

import com.makd.afinity.data.database.dao.BookAnnotationDao
import com.makd.afinity.data.database.entities.BookAnnotationEntity
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.media.BookAnnotation
import com.makd.afinity.data.models.media.BookAnnotationKind
import com.makd.afinity.data.models.media.HighlightColor
import com.makd.afinity.data.repository.PreferencesRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@Singleton
class BookAnnotationRepositoryImpl
@Inject
constructor(
    private val dao: BookAnnotationDao,
    private val sessionManager: SessionManager,
    private val preferencesRepository: PreferencesRepository,
) : BookAnnotationRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(itemId: UUID): Flow<List<BookAnnotation>> =
        sessionManager.currentSession
            .map { session -> session?.let { it.serverId to it.userId.toString() } }
            .distinctUntilChanged()
            .flatMapLatest { account ->
                if (account == null) {
                    flowOf(emptyList())
                } else {
                    dao.observe(account.first, account.second, itemId.toString()).map { rows ->
                        rows.mapNotNull { it.toDomain() }
                    }
                }
            }

    override suspend fun save(itemId: UUID, annotation: BookAnnotation) {
        val session = sessionManager.currentSession.value ?: return
        dao.upsert(
            BookAnnotationEntity(
                id = annotation.id,
                serverId = session.serverId,
                userId = session.userId.toString(),
                itemId = itemId.toString(),
                kind = annotation.kind.name,
                locator = annotation.locatorJson,
                page = annotation.page,
                progression = annotation.progression,
                label = annotation.label,
                text = annotation.text,
                color = annotation.color?.name,
                createdAt = annotation.createdAt,
            ),
        )
    }

    override suspend fun setColor(id: String, color: HighlightColor) {
        dao.updateColor(id, color.name)
    }

    override suspend fun delete(id: String) {
        dao.delete(id)
    }

    override suspend fun lastHighlightColor(): HighlightColor =
        HighlightColor.fromName(preferencesRepository.getStringPreference(LAST_COLOR_KEY))
            ?: HighlightColor.YELLOW

    override suspend fun setLastHighlightColor(color: HighlightColor) {
        preferencesRepository.setStringPreference(LAST_COLOR_KEY, color.name)
    }

    private fun BookAnnotationEntity.toDomain(): BookAnnotation? {
        val parsedKind = BookAnnotationKind.entries.firstOrNull { it.name == kind } ?: return null
        return BookAnnotation(
            id = id,
            kind = parsedKind,
            locatorJson = locator,
            page = page,
            progression = progression,
            label = label,
            text = text,
            color = HighlightColor.fromName(color),
            createdAt = createdAt,
        )
    }

    private companion object {
        const val LAST_COLOR_KEY = "reader_highlight_color"
    }
}
