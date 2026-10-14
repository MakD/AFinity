package com.makd.afinity.data.network

import com.makd.afinity.data.models.lastfm.LastFmTopTracksResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface LastFmApiService {
    @GET("2.0/")
    suspend fun getArtistTopTracks(
        @Query("artist") artist: String,
        @Query("api_key") apiKey: String,
        @Query("limit") limit: Int,
        @Query("method") method: String = "artist.gettoptracks",
        @Query("autocorrect") autocorrect: Int = 1,
        @Query("format") format: String = "json",
    ): LastFmTopTracksResponse
}
