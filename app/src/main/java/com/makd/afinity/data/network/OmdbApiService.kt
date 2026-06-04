package com.makd.afinity.data.network

import com.makd.afinity.data.models.omdb.OmdbApiResult
import retrofit2.http.GET
import retrofit2.http.Query

interface OmdbApiService {
    @GET("/")
    suspend fun getTitleDetails(
        @Query("i") imdbId: String,
        @Query("apikey") apiKey: String,
    ): OmdbApiResult
}
