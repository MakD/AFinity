package com.makd.afinity.data.network

import com.makd.afinity.data.models.subtitles.SubtitleDbBundle
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface SubtitleDbApiService {
    @GET("v1/by-imdb/{imdbId}")
    suspend fun byImdb(
        @Path("imdbId") imdbId: String,
        @Query("lang") language: String?,
        @Query("limit") limit: Int = PAGE_SIZE,
        @Query("client") client: String = CLIENT,
    ): Response<SubtitleDbBundle>

    @GET("v1/by-imdb/{imdbId}/season/{season}/episode/{episode}")
    suspend fun byImdbEpisode(
        @Path("imdbId") imdbId: String,
        @Path("season") season: Int,
        @Path("episode") episode: Int,
        @Query("lang") language: String?,
        @Query("limit") limit: Int = PAGE_SIZE,
        @Query("client") client: String = CLIENT,
    ): Response<SubtitleDbBundle>

    @GET("v1/by-tmdb/movie/{tmdbId}")
    suspend fun byTmdbMovie(
        @Path("tmdbId") tmdbId: String,
        @Query("lang") language: String?,
        @Query("limit") limit: Int = PAGE_SIZE,
        @Query("client") client: String = CLIENT,
    ): Response<SubtitleDbBundle>

    @GET("v1/by-tmdb/tv/{tmdbId}/season/{season}/episode/{episode}")
    suspend fun byTmdbEpisode(
        @Path("tmdbId") tmdbId: String,
        @Path("season") season: Int,
        @Path("episode") episode: Int,
        @Query("lang") language: String?,
        @Query("limit") limit: Int = PAGE_SIZE,
        @Query("client") client: String = CLIENT,
    ): Response<SubtitleDbBundle>

    @GET("v1/by-title")
    suspend fun byTitle(
        @Query("q") query: String,
        @Query("type") type: String?,
        @Query("lang") language: String?,
        @Query("limit") limit: Int = PAGE_SIZE,
        @Query("client") client: String = CLIENT,
    ): Response<SubtitleDbBundle>

    @Streaming
    @GET("get/{subtitleId}")
    suspend fun download(
        @Path("subtitleId") subtitleId: Long,
        @Query("remove_watermark") removeWatermark: String = "start",
        @Query("client") client: String = CLIENT,
    ): Response<ResponseBody>

    companion object {
        const val BASE_URL = "https://api.thesubtitledb.org/"
        const val CLIENT = "afinity"
        const val PAGE_SIZE = 100
        val ALLOWED_HOSTS = setOf("api.thesubtitledb.org", "files.thesubtitledb.org")
    }
}
