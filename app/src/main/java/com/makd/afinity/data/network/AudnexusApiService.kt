package com.makd.afinity.data.network

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface AudnexusApiService {
    @GET("books/{asin}")
    suspend fun getBook(
        @Path("asin") asin: String,
        @Query("region") region: String? = null,
        @Query("update") update: Int = 1,
    ): Response<AudnexusBookResponse>
}

@Serializable
data class AudnexusBookResponse(
    val asin: String? = null,
    val rating: String? = null,
)