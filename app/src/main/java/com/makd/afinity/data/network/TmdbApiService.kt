package com.makd.afinity.data.network

import com.makd.afinity.data.models.tmdb.TmdbCollectionResponse
import com.makd.afinity.data.models.tmdb.TmdbCombinedCreditsResponse
import com.makd.afinity.data.models.tmdb.TmdbDetailsResponse
import com.makd.afinity.data.models.tmdb.TmdbImagesResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApiService {

    @GET("3/movie/{movie_id}")
    suspend fun getMovieDetails(
        @Path("movie_id") movieId: String,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "en-US",
        @Query("append_to_response") appendToResponse: String = "reviews,watch/providers",
    ): TmdbDetailsResponse

    @GET("3/tv/{series_id}")
    suspend fun getSeriesDetails(
        @Path("series_id") seriesId: String,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "en-US",
        @Query("append_to_response") appendToResponse: String = "reviews,watch/providers",
    ): TmdbDetailsResponse

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

    @GET("3/collection/{collection_id}")
    suspend fun getCollection(
        @Path("collection_id") collectionId: String,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "en-US",
    ): TmdbCollectionResponse

    @GET("3/person/{person_id}/combined_credits")
    suspend fun getPersonCombinedCredits(
        @Path("person_id") personId: String,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "en-US",
    ): TmdbCombinedCreditsResponse

    @GET("3/authentication")
    suspend fun validateApiKey(@Query("api_key") apiKey: String): Response<ResponseBody>
}
