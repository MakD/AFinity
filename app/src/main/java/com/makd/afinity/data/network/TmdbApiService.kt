package com.makd.afinity.data.network

import com.makd.afinity.data.models.tmdb.TmdbImagesResponse
import com.makd.afinity.data.models.tmdb.TmdbReviewResponse
import okhttp3.ResponseBody
import retrofit2.Response
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

    @GET("3/movie/{movie_id}/images")
    suspend fun getMovieImages(
        @Path("movie_id") movieId: String,
        @Query("api_key") apiKey: String,
        @Query("include_image_language") includeImageLanguage: String = "en,null",
    ): TmdbImagesResponse

    @GET("3/tv/{series_id}/images")
    suspend fun getSeriesImages(
        @Path("series_id") seriesId: String,
        @Query("api_key") apiKey: String,
        @Query("include_image_language") includeImageLanguage: String = "en,null",
    ): TmdbImagesResponse

    @GET("3/authentication")
    suspend fun validateApiKey(@Query("api_key") apiKey: String): Response<ResponseBody>
}
