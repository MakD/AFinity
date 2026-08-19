package com.makd.afinity.data.network

import com.makd.afinity.data.models.wikidata.SparqlResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface WikidataApiService {
    @GET("sparql") suspend fun query(@Query("query") sparql: String): SparqlResponse
}