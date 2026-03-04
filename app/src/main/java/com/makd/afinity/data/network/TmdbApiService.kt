package com.makd.afinity.data.network

import com.makd.afinity.data.models.tmdb.TmdbReviewResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApiService {

    @GET("3/movie/{movie_id}/reviews")
    suspend fun getMovieReviews(
        @Path("movie_id") movieId: String,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "en-US",
        @Query("page") page: Int = 1,
    ): TmdbReviewResponse

    @GET("3/tv/{series_id}/reviews")
    suspend fun getSeriesReviews(
        @Path("series_id") seriesId: String,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "en-US",
        @Query("page") page: Int = 1,
    ): TmdbReviewResponse
}
