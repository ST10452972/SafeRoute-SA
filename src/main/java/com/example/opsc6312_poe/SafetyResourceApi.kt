package com.example.opsc6312_poe

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

/** OpenStreetMap Nominatim is used as the prototype's public safety-resource REST API. */
interface SafetyResourceApi {
    @GET("search")
    suspend fun search(
        @Header("User-Agent") userAgent: String = "SafeRouteSA-OPSC6312-Prototype/1.0",
        @Query("q") query: String,
        @Query("format") format: String = "jsonv2",
        @Query("limit") limit: Int = 5,
        @Query("countrycodes") country: String = "za"
    ): List<SafetyResource>
}

data class SafetyResource(
    val display_name: String = "",
    val lat: String = "",
    val lon: String = ""
)
