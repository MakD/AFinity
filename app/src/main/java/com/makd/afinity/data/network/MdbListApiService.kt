package com.makd.afinity.data.network

import com.makd.afinity.data.models.mdblist.MdbListApiResult
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface MdbListApiService {
    @GET("tmdb/{contentType}/{tmdbId}")
    suspend fun getRatings(
        @Path("contentType") contentType: String,
        @Path("tmdbId") tmdbId: String,
        @Query("apikey") apiKey: String,
    ): MdbListApiResult
}
