package com.makd.afinity.data.network

import com.makd.afinity.data.models.audiobookshelf.AuthorizeResponse
import com.makd.afinity.data.models.audiobookshelf.AudiobookshelfUser
import com.makd.afinity.data.models.audiobookshelf.BatchLocalSessionRequest
import com.makd.afinity.data.models.audiobookshelf.BatchSyncResponse
import com.makd.afinity.data.models.audiobookshelf.ItemResponse
import com.makd.afinity.data.models.audiobookshelf.ItemsInProgressResponse
import com.makd.afinity.data.models.audiobookshelf.LibrariesResponse
import com.makd.afinity.data.models.audiobookshelf.LibraryItemsResponse
import com.makd.afinity.data.models.audiobookshelf.LibraryResponse
import com.makd.afinity.data.models.audiobookshelf.LoginRequest
import com.makd.afinity.data.models.audiobookshelf.LoginResponse
import com.makd.afinity.data.models.audiobookshelf.MediaProgress
import com.makd.afinity.data.models.audiobookshelf.MediaProgressSyncData
import com.makd.afinity.data.models.audiobookshelf.PersonalizedView
import com.makd.afinity.data.models.audiobookshelf.SeriesListResponse
import com.makd.afinity.data.models.audiobookshelf.PlaybackSession
import com.makd.afinity.data.models.audiobookshelf.PlaybackSessionRequest
import com.makd.afinity.data.models.audiobookshelf.ProgressUpdateRequest
import com.makd.afinity.data.models.audiobookshelf.SearchResponse
import com.makd.afinity.data.models.audiobookshelf.SyncResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface AudiobookshelfApiService {

    @POST("api/authorize")
    suspend fun authorize(): Response<AuthorizeResponse>

    @POST("login")
    suspend fun login(@Body credentials: LoginRequest): Response<LoginResponse>

    @GET("api/libraries")
    suspend fun getLibraries(): Response<LibrariesResponse>

    @GET("api/libraries/{libraryId}")
    suspend fun getLibrary(
        @Path("libraryId") id: String,
        @Query("include") include: String? = null
    ): Response<LibraryResponse>

    @GET("api/libraries/{libraryId}/items")
    suspend fun getLibraryItems(
        @Path("libraryId") id: String,
        @Query("minified") minified: Int = 1,
        @Query("limit") limit: Int = 100,
        @Query("page") page: Int = 0,
        @Query("sort") sort: String? = null,
        @Query("desc") desc: Int? = null,
        @Query("filter") filter: String? = null,
        @Query("include") include: String? = null,
        @Query("collapseseries") collapseseries: Int? = null
    ): Response<LibraryItemsResponse>

    @GET("api/libraries/{libraryId}/series")
    suspend fun getSeries(
        @Path("libraryId") id: String,
        @Query("sort") sort: String = "name",
        @Query("desc") desc: Int = 0,
        @Query("filter") filter: String = "all",
        @Query("limit") limit: Int = 100,
        @Query("page") page: Int = 0,
        @Query("minified") minified: Int = 1,
        @Query("include") include: String = "progress"
    ): Response<SeriesListResponse>

    @GET("api/libraries/{libraryId}/personalized")
    suspend fun getPersonalized(
        @Path("libraryId") id: String,
        @Query("limit") limit: Int? = null,
        @Query("include") include: String? = null
    ): Response<List<PersonalizedView>>

    @GET("api/libraries/{libraryId}/search")
    suspend fun search(
        @Path("libraryId") id: String,
        @Query("q") query: String,
        @Query("limit") limit: Int? = null
    ): Response<SearchResponse>

    @GET("api/items/{itemId}")
    suspend fun getItem(
        @Path("itemId") id: String,
        @Query("expanded") expanded: Int = 1,
        @Query("include") include: String? = "progress"
    ): Response<ItemResponse>

    @GET("api/me")
    suspend fun getMe(): Response<AudiobookshelfUser>

    @GET("api/me/items-in-progress")
    suspend fun getItemsInProgress(
        @Query("limit") limit: Int? = null
    ): Response<ItemsInProgressResponse>

    @PATCH("api/me/progress/{itemId}")
    suspend fun updateProgress(
        @Path("itemId") id: String,
        @Body progress: ProgressUpdateRequest
    ): Response<MediaProgress>

    @PATCH("api/me/progress/{itemId}/{episodeId}")
    suspend fun updateEpisodeProgress(
        @Path("itemId") itemId: String,
        @Path("episodeId") episodeId: String,
        @Body progress: ProgressUpdateRequest
    ): Response<MediaProgress>

    @POST("api/items/{itemId}/play")
    suspend fun startPlaybackSession(
        @Path("itemId") itemId: String,
        @Body request: PlaybackSessionRequest
    ): Response<PlaybackSession>

    @POST("api/items/{itemId}/play/{episodeId}")
    suspend fun startEpisodePlaybackSession(
        @Path("itemId") itemId: String,
        @Path("episodeId") episodeId: String,
        @Body request: PlaybackSessionRequest
    ): Response<PlaybackSession>

    @POST("api/session/{sessionId}/sync")
    suspend fun syncPlaybackSession(
        @Path("sessionId") id: String,
        @Body syncData: MediaProgressSyncData
    ): Response<SyncResponse>

    @POST("api/session/{sessionId}/close")
    suspend fun closePlaybackSession(
        @Path("sessionId") id: String,
        @Body syncData: MediaProgressSyncData? = null
    ): Response<Unit>

    @POST("api/session/local-all")
    suspend fun syncAllLocalSessions(
        @Body request: BatchLocalSessionRequest
    ): Response<BatchSyncResponse>
}
