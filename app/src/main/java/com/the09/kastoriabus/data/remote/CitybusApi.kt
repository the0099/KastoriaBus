package com.the09.kastoriabus.data.remote

import com.the09.kastoriabus.data.model.Line
import com.the09.kastoriabus.data.model.LiveResponse
import com.the09.kastoriabus.data.model.RouteShape
import com.the09.kastoriabus.data.model.RouteStop
import com.the09.kastoriabus.data.model.Stop
import com.the09.kastoriabus.data.model.Trip
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

const val CITYBUS_BASE_URL = "https://rest.citybus.gr/"
const val AGENCY_CODE = "115" // Kastoria

/**
 * The rest.citybus.gr endpoints. Retrofit writes the actual networking code for us
 * from these annotations. {lang} is "el" or "en".
 * The bearer token is added by [AuthInterceptor], so it doesn't appear here.
 */
interface CitybusApi {

    @GET("api/v1/{lang}/$AGENCY_CODE/stops")
    suspend fun getStops(@Path("lang") lang: String): List<Stop>

    @GET("api/v1/{lang}/$AGENCY_CODE/lines")
    suspend fun getLines(@Path("lang") lang: String): List<Line>

    @GET("api/v1/{lang}/$AGENCY_CODE/routes/{routeCode}/sequence")
    suspend fun getRouteSequence(
        @Path("lang") lang: String,
        @Path("routeCode") routeCode: String,
    ): List<RouteStop>

    // Note: this one has no {lang} segment.
    @GET("api/v1/$AGENCY_CODE/lines/{lineCode}/points")
    suspend fun getLineShapes(@Path("lineCode") lineCode: String): List<RouteShape>

    /** [day]: 0 = Sunday, 1 = Monday, ... 6 = Saturday. */
    @GET("api/v1/{lang}/$AGENCY_CODE/trips/stop/{stopCode}/day/{day}")
    suspend fun getStopTimetable(
        @Path("lang") lang: String,
        @Path("stopCode") stopCode: String,
        @Path("day") day: Int,
    ): List<Trip>

    // Wrapped in Response<> so we can read the 404 ("no buses coming")
    // ourselves instead of Retrofit throwing an exception.
    @GET("api/v1/{lang}/$AGENCY_CODE/stops/live/{stopCode}")
    suspend fun getLiveArrivals(
        @Path("lang") lang: String,
        @Path("stopCode") stopCode: String,
    ): Response<LiveResponse>
}
