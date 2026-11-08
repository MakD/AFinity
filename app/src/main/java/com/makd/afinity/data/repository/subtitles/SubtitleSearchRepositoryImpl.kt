package com.makd.afinity.data.repository.subtitles

import android.util.Base64
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.subtitles.DeviceSubtitleMeta
import com.makd.afinity.data.models.subtitles.ServerAddedSubtitle
import com.makd.afinity.data.models.subtitles.SubtitleDbBundle
import com.makd.afinity.data.models.subtitles.SubtitleDbRow
import com.makd.afinity.data.models.subtitles.SubtitleItemIdentity
import com.makd.afinity.data.network.SubtitleDbApiService
import com.makd.afinity.data.repository.JellyfinApiInvoker
import com.makd.afinity.data.repository.PreferencesRepository
import java.nio.charset.Charset
import java.nio.charset.IllegalCharsetNameException
import java.nio.charset.UnsupportedCharsetException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import okio.Buffer
import org.jellyfin.sdk.api.operations.LibraryApi
import org.jellyfin.sdk.api.operations.SubtitleApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.RemoteSubtitleInfo
import org.jellyfin.sdk.model.api.UploadSubtitleDto
import retrofit2.Response
import timber.log.Timber

@Singleton
class SubtitleSearchRepositoryImpl
@Inject
constructor(
    private val sessionManager: SessionManager,
    private val apiInvoker: JellyfinApiInvoker,
    private val preferencesRepository: PreferencesRepository,
    private val subtitleDbApi: SubtitleDbApiService,
    private val deviceStore: DeviceSubtitleStore,
) : SubtitleSearchRepository {

    private val seriesIdentityCache = ConcurrentHashMap<UUID, Map<String, String>>()

    override fun currentMode(): SubtitleSearchMode? {
        val session = sessionManager.currentSession.value ?: return null
        val canManage = session.canManageSubtitles ?: (session.isAdmin == true)
        return when {
            canManage && session.hasSubtitleProvider == true -> SubtitleSearchMode.SERVER
            canManage -> SubtitleSearchMode.UPLOAD
            else -> SubtitleSearchMode.DEVICE
        }
    }

    override fun currentScope(): SubtitleScope? =
        sessionManager.currentSession.value?.let { SubtitleScope(it.serverId, it.userId) }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun subtitleDbEnabledFlow(): Flow<Boolean> =
        sessionManager.currentSession.flatMapLatest { session ->
            if (session == null) flowOf(false)
            else
                preferencesRepository.getSubtitleDbEnabledFlow(
                    session.serverId,
                    session.userId.toString(),
                )
        }

    override suspend fun isSubtitleDbEnabled(): Boolean {
        val scope = currentScope() ?: return false
        return preferencesRepository.getSubtitleDbEnabled(scope.serverId, scope.userId.toString())
    }

    override suspend fun setSubtitleDbEnabled(enabled: Boolean) {
        val scope = currentScope() ?: return
        preferencesRepository.setSubtitleDbEnabled(
            scope.serverId,
            scope.userId.toString(),
            enabled,
        )
    }

    override suspend fun identity(item: AfinityItem): SubtitleItemIdentity {
        val own = item.providerIds.orEmpty()
        if (item !is AfinityEpisode) {
            return SubtitleItemIdentity(imdbId = own.imdb(), tmdbId = own.tmdb())
        }
        val series = seriesProviderIds(item.seriesId)
        return SubtitleItemIdentity(
            imdbId = own.imdb(),
            tmdbId = own.tmdb(),
            seriesImdbId = series.imdb(),
            seriesTmdbId = series.tmdb(),
            season = item.parentIndexNumber,
            episode = item.indexNumber,
        )
    }

    override suspend fun search(
        item: AfinityItem,
        language: String,
    ): Result<SubtitleSearchResult> {
        val mode = currentMode() ?: return Result.failure(IllegalStateException("No session"))
        return if (mode.usesSubtitleDb) {
            if (!isSubtitleDbEnabled()) return Result.failure(SubtitleDbNotEnabledException())
            searchSubtitleDb(item, language, mode)
        } else {
            searchServer(item, language).map { candidates ->
                SubtitleSearchResult(
                    mode = mode,
                    language = language,
                    candidates = candidates,
                    languages = listOf(SubtitleLanguageCount(language, candidates.size)),
                )
            }
        }
    }

    override suspend fun serverLanguageCounts(
        item: AfinityItem,
        languages: List<String>,
    ): Map<String, Int> = coroutineScope {
        languages
            .distinctBy { SubtitleLanguageCodes.toServer(it) }
            .map { language ->
                async { searchServer(item, language).getOrNull()?.let { language to it.size } }
            }
            .awaitAll()
            .filterNotNull()
            .toMap()
    }

    override suspend fun add(
        item: AfinityItem,
        candidate: SubtitleCandidate,
    ): Result<AddedSubtitle> {
        val mode = currentMode() ?: return Result.failure(IllegalStateException("No session"))
        val scope = currentScope() ?: return Result.failure(IllegalStateException("No session"))
        return try {
            val added =
                when (mode) {
                    SubtitleSearchMode.SERVER -> addOnServer(item, candidate, scope)
                    SubtitleSearchMode.UPLOAD -> uploadToServer(item, candidate, scope)
                    SubtitleSearchMode.DEVICE -> saveOnDevice(item, candidate, scope)
                }
            Result.success(added)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to add subtitle")
            Result.failure(e)
        }
    }

    private suspend fun searchServer(
        item: AfinityItem,
        language: String,
    ): Result<List<SubtitleCandidate>> {
        val serverLanguage = SubtitleLanguageCodes.toServer(language)
        return apiInvoker
            .apiResult { apiClient, _ ->
                SubtitleApi(apiClient).searchRemoteSubtitles(item.id, serverLanguage).content
            }
            .map { results -> results.mapNotNull { it.toCandidate(serverLanguage) } }
    }

    private suspend fun searchSubtitleDb(
        item: AfinityItem,
        language: String,
        mode: SubtitleSearchMode,
    ): Result<SubtitleSearchResult> =
        try {
            val dbLanguage = SubtitleLanguageCodes.toSubtitleDb(language)
            val bundle = lookUp(item, dbLanguage)
            val candidates =
                bundle
                    ?.subtitles
                    ?.items
                    .orEmpty()
                    .filter { it.isPlayable() }
                    .map { it.toCandidate() }
            val counts = bundle?.title?.subtitleLanguages.orEmpty()
            val languages =
                (counts + (dbLanguage to candidates.size))
                    .entries
                    .filter { it.value > 0 || it.key == dbLanguage }
                    .sortedByDescending { it.value }
                    .map { SubtitleLanguageCount(it.key, it.value) }
            Result.success(SubtitleSearchResult(mode, dbLanguage, candidates, languages))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    private suspend fun lookUp(item: AfinityItem, language: String): SubtitleDbBundle? {
        val identity = identity(item)
        val attempts =
            buildList<suspend () -> Response<SubtitleDbBundle>> {
                if (item is AfinityEpisode) {
                    val season = item.parentIndexNumber
                    val episode = item.indexNumber
                    identity.seriesTmdbId?.let { id ->
                        add { subtitleDbApi.byTmdbEpisode(id, season, episode, language) }
                    }
                    identity.seriesImdbId?.let { id ->
                        add { subtitleDbApi.byImdbEpisode(id, season, episode, language) }
                    }
                    if (isEmpty()) {
                        val query = "%s S%02dE%02d".format(item.seriesName, season, episode)
                        add { subtitleDbApi.byTitle(query, "tv", language) }
                    }
                } else {
                    identity.imdbId?.let { id -> add { subtitleDbApi.byImdb(id, language) } }
                    identity.tmdbId?.let { id -> add { subtitleDbApi.byTmdbMovie(id, language) } }
                    if (isEmpty()) {
                        val type = if (item is AfinityMovie) "movie" else null
                        add { subtitleDbApi.byTitle(item.name, type, language) }
                    }
                }
            }
        for (attempt in attempts) {
            val response = withContext(Dispatchers.IO) { attempt() }
            when {
                response.isSuccessful ->
                    return response.body()?.takeIf { !it.title?.name.isNullOrBlank() }
                response.code() == 404 -> continue
                else -> throw SubtitleDownloadException("TheSubtitleDB answered ${response.code()}")
            }
        }
        return null
    }

    private suspend fun addOnServer(
        item: AfinityItem,
        candidate: SubtitleCandidate,
        scope: SubtitleScope,
    ): AddedSubtitle {
        val before = externalSubtitleFiles(item.id).getOrThrow()
        apiInvoker
            .apiResult { apiClient, _ ->
                SubtitleApi(apiClient).downloadRemoteSubtitles(item.id, candidate.id)
            }
            .getOrThrow()
        return recordServerTrack(item, candidate, scope, before)
    }

    private suspend fun uploadToServer(
        item: AfinityItem,
        candidate: SubtitleCandidate,
        scope: SubtitleScope,
    ): AddedSubtitle {
        val content = downloadFromSubtitleDb(candidate)
        val before = externalSubtitleFiles(item.id).getOrThrow()
        val upload =
            UploadSubtitleDto(
                language = SubtitleLanguageCodes.toServer(candidate.language),
                format = candidate.format,
                isForced = false,
                isHearingImpaired = candidate.hearingImpaired,
                data = Base64.encodeToString(content, Base64.NO_WRAP),
            )
        apiInvoker
            .apiResult { apiClient, _ -> SubtitleApi(apiClient).uploadSubtitle(item.id, upload) }
            .getOrThrow()
        return recordServerTrack(item, candidate, scope, before)
    }

    private suspend fun recordServerTrack(
        item: AfinityItem,
        candidate: SubtitleCandidate,
        scope: SubtitleScope,
        before: Set<String>,
    ): AddedSubtitle {
        val fileName = awaitNewExternalSubtitle(item.id, before)
        if (fileName != null) {
            deviceStore.recordServerAdded(
                scope,
                item.id,
                identity(item),
                ServerAddedSubtitle(
                    fileName = fileName,
                    language = candidate.language,
                    addedAtMs = System.currentTimeMillis(),
                ),
            )
        } else {
            Timber.w("Server subtitle did not appear in time")
        }
        return AddedSubtitle.OnServer(fileName, candidate.language)
    }

    private suspend fun saveOnDevice(
        item: AfinityItem,
        candidate: SubtitleCandidate,
        scope: SubtitleScope,
    ): AddedSubtitle {
        val content = downloadFromSubtitleDb(candidate)
        val meta =
            DeviceSubtitleMeta(
                fileName =
                    DeviceSubtitleStore.fileName(
                        candidate.id,
                        candidate.language,
                        candidate.format,
                    ),
                subtitleId = candidate.id,
                language = candidate.language,
                format = candidate.format,
                releaseName = candidate.releaseName,
                hearingImpaired = candidate.hearingImpaired,
                aiTranscribed = candidate.aiTranscribed,
                runtimeTicksAtSave = item.runtimeTicks,
                savedAtMs = System.currentTimeMillis(),
            )
        val saved = deviceStore.save(scope, item.id, identity(item), meta, content)
        return AddedSubtitle.OnDevice(saved)
    }

    private suspend fun downloadFromSubtitleDb(candidate: SubtitleCandidate): ByteArray =
        withContext(Dispatchers.IO) {
            val id =
                candidate.id.toLongOrNull()
                    ?: throw SubtitleDownloadException("Not a TheSubtitleDB subtitle")
            val response = subtitleDbApi.download(id)
            if (!response.isSuccessful) {
                response.errorBody()?.close()
                throw SubtitleDownloadException("TheSubtitleDB answered ${response.code()}")
            }
            val body = response.body() ?: throw SubtitleDownloadException("Empty subtitle file")
            val bytes = body.use {
                val buffer = Buffer()
                val source = it.source()
                while (source.read(buffer, READ_CHUNK) != -1L) {
                    if (buffer.size > MAX_SUBTITLE_BYTES) {
                        throw SubtitleDownloadException("Subtitle file is too large")
                    }
                }
                if (it.contentType()?.subtype.equals("html", ignoreCase = true)) {
                    throw SubtitleDownloadException("Got a web page instead of a subtitle")
                }
                buffer.readByteArray()
            }
            if (bytes.isEmpty() || looksLikeHtml(bytes)) {
                throw SubtitleDownloadException("Got a web page instead of a subtitle")
            }
            toUtf8(bytes, candidate.encoding)
        }

    private suspend fun externalSubtitleFiles(itemId: UUID): Result<Set<String>> =
        apiInvoker.apiResult { apiClient, userId ->
            LibraryApi(apiClient)
                .getItem(itemId = itemId, userId = userId)
                .content
                .externalSubtitleFiles()
        }

    private suspend fun awaitNewExternalSubtitle(itemId: UUID, before: Set<String>): String? {
        repeat(TRACK_POLL_ATTEMPTS) {
            delay(TRACK_POLL_INTERVAL_MS)
            val now = externalSubtitleFiles(itemId).getOrNull() ?: return@repeat
            (now - before).firstOrNull()?.let {
                return it
            }
        }
        return null
    }

    private suspend fun seriesProviderIds(seriesId: UUID): Map<String, String> {
        seriesIdentityCache[seriesId]?.let {
            return it
        }
        val ids =
            apiInvoker
                .apiResult { apiClient, userId ->
                    LibraryApi(apiClient).getItem(itemId = seriesId, userId = userId).content
                }
                .getOrNull()
                ?.providerIds
                .orEmpty()
                .mapNotNull { (key, value) -> value?.let { key to it } }
                .toMap()
        if (ids.isNotEmpty()) seriesIdentityCache[seriesId] = ids
        return ids
    }

    private fun BaseItemDto.externalSubtitleFiles(): Set<String> {
        val streams =
            mediaSources.orEmpty().flatMap { it.mediaStreams.orEmpty() } + mediaStreams.orEmpty()
        return streams
            .filter { it.type == MediaStreamType.SUBTITLE && it.isExternal }
            .mapNotNull { externalSubtitleFileName(it.path) }
            .toSet()
    }

    private fun RemoteSubtitleInfo.toCandidate(serverLanguage: String): SubtitleCandidate? {
        val subtitleId = id ?: return null
        val playableFormat = this.format?.lowercase()?.let { SERVER_FORMATS[it] } ?: return null
        val parts = name.orEmpty().split(" - ")
        var rest = if (parts.size > 1) parts.drop(1) else parts
        val hearingImpairedTag = rest.firstOrNull()?.trim().equals("HI", ignoreCase = true)
        if (hearingImpairedTag && rest.size > 1) rest = rest.drop(1)
        return SubtitleCandidate(
            id = subtitleId,
            language = threeLetterIsoLanguageName ?: serverLanguage,
            releaseName = rest.joinToString(" - ").trim().ifBlank { subtitleId },
            format = playableFormat,
            hearingImpaired = hearingImpaired == true || hearingImpairedTag,
            aiTranscribed = aiTranslated == true || machineTranslated == true,
            providerName = providerName,
        )
    }

    private fun SubtitleDbRow.isPlayable(): Boolean = format?.lowercase() in PLAYABLE_FORMATS

    private fun SubtitleDbRow.toCandidate(): SubtitleCandidate =
        SubtitleCandidate(
            id = id.toString(),
            language = language,
            releaseName =
                releaseName?.lineSequence()?.firstOrNull()?.trim().orEmpty().ifBlank {
                    id.toString()
                },
            format = format.orEmpty().lowercase(),
            hearingImpaired = hearingImpaired,
            aiTranscribed = aiTranscribed,
            lineCount = cues,
            durationSeconds = durationSeconds,
            providerName = PROVIDER_NAME,
            encoding = encoding,
        )

    private fun Map<String, String>.imdb(): String? =
        entries
            .firstOrNull { it.key.equals("Imdb", ignoreCase = true) }
            ?.value
            ?.takeIf { it.isNotBlank() }

    private fun Map<String, String>.tmdb(): String? =
        entries
            .firstOrNull { it.key.equals("Tmdb", ignoreCase = true) }
            ?.value
            ?.takeIf { it.isNotBlank() }

    private fun looksLikeHtml(bytes: ByteArray): Boolean {
        val head = String(bytes, 0, minOf(bytes.size, 512), Charsets.UTF_8).trimStart('﻿')
        val start = head.trimStart().lowercase()
        return start.startsWith("<!doctype html") || start.startsWith("<html")
    }

    private fun toUtf8(bytes: ByteArray, encoding: String?): ByteArray {
        val name = encoding?.trim()?.lowercase().orEmpty()
        val utf8 = name.isEmpty() || name in UTF8_NAMES
        val text =
            if (utf8) {
                String(bytes, Charsets.UTF_8)
            } else {
                val charset =
                    try {
                        Charset.forName(name)
                    } catch (_: IllegalCharsetNameException) {
                        null
                    } catch (_: UnsupportedCharsetException) {
                        null
                    }
                String(bytes, charset ?: Charsets.UTF_8)
            }
        return text.removePrefix("﻿").toByteArray(Charsets.UTF_8)
    }

    companion object {
        const val PROVIDER_NAME = "TheSubtitleDB"
        private val PLAYABLE_FORMATS = setOf("srt", "ass", "ssa", "vtt")
        private val SERVER_FORMATS =
            mapOf(
                "srt" to "srt",
                "subrip" to "srt",
                "ass" to "ass",
                "ssa" to "ssa",
                "vtt" to "vtt",
                "webvtt" to "vtt",
            )
        private val UTF8_NAMES = setOf("utf-8", "utf8", "utf-8-sig", "ascii", "us-ascii")
        private const val MAX_SUBTITLE_BYTES = 10L * 1024 * 1024
        private const val READ_CHUNK = 8192L
        private const val TRACK_POLL_ATTEMPTS = 20
        private const val TRACK_POLL_INTERVAL_MS = 1000L
    }
}
