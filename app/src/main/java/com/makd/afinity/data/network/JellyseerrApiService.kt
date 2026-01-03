package com.makd.afinity.data.network

import com.makd.afinity.data.models.jellyseerr.CreateRequestBody
import com.makd.afinity.data.models.jellyseerr.JellyfinLoginRequest
import com.makd.afinity.data.models.jellyseerr.JellyseerrRequest
import com.makd.afinity.data.models.jellyseerr.JellyseerrSearchResult
import com.makd.afinity.data.models.jellyseerr.JellyseerrUser
import com.makd.afinity.data.models.jellyseerr.LoginRequest
import com.makd.afinity.data.models.jellyseerr.LoginResponse
import com.makd.afinity.data.models.jellyseerr.RequestsResponse
import com.makd.afinity.data.models.jellyseerr.TvDetails
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface JellyseerrApiService {

    @POST("api/v1/auth/local")
    suspend fun loginLocal(@Body request: LoginRequest): Response<LoginResponse>

    @POST("api/v1/auth/jellyfin")
    suspend fun loginJellyfin(@Body request: JellyfinLoginRequest): Response<LoginResponse>

    @GET("api/v1/auth/me")
    suspend fun getCurrentUser(): Response<JellyseerrUser>

    @POST("api/v1/auth/logout")
    suspend fun logout(): Response<Unit>

    @POST("api/v1/request")
    suspend fun createRequest(@Body request: CreateRequestBody): Response<JellyseerrRequest>

    @GET("api/v1/request")
    suspend fun getRequests(
        @Query("take") take: Int = 20,
        @Query("skip") skip: Int = 0,
        @Query("filter") filter: String? = null,
        @Query("sort") sort: String? = "added"
    ): Response<RequestsResponse>

    @GET("api/v1/request/{requestId}")
    suspend fun getRequestById(@Path("requestId") requestId: Int): Response<JellyseerrRequest>

    @DELETE("api/v1/request/{requestId}")
    suspend fun deleteRequest(@Path("requestId") requestId: Int): Response<Unit>

    @POST("api/v1/request/{requestId}/approve")
    suspend fun approveRequest(@Path("requestId") requestId: Int): Response<JellyseerrRequest>

    @POST("api/v1/request/{requestId}/decline")
    suspend fun declineRequest(@Path("requestId") requestId: Int): Response<JellyseerrRequest>

    @GET("api/v1/search")
    suspend fun search(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en"
    ): Response<JellyseerrSearchResult>

    @GET("api/v1/search/multi")
    suspend fun searchMulti(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en"
    ): Response<JellyseerrSearchResult>

    @GET("api/v1/tv/{tvId}")
    suspend fun getTvDetails(@Path("tvId") tvId: Int): Response<TvDetails>

    @GET("api/v1/discover/trending")
    suspend fun getTrending(
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en"
    ): Response<JellyseerrSearchResult>

    @GET("api/v1/discover/movies")
    suspend fun getDiscoverMovies(
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en",
        @Query("sortBy") sortBy: String = "popularity.desc"
    ): Response<JellyseerrSearchResult>

    @GET("api/v1/discover/tv")
    suspend fun getDiscoverTv(
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en",
        @Query("sortBy") sortBy: String = "popularity.desc"
    ): Response<JellyseerrSearchResult>

    @GET("api/v1/discover/movies/upcoming")
    suspend fun getUpcomingMovies(
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en"
    ): Response<JellyseerrSearchResult>

    @GET("api/v1/discover/tv/upcoming")
    suspend fun getUpcomingTv(
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en"
    ): Response<JellyseerrSearchResult>
}