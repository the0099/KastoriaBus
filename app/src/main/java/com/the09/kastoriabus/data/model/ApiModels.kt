package com.the09.kastoriabus.data.model

// Data classes that match the JSON of rest.citybus.gr (see kastoria-api-notes.txt).
// Gson fills them by matching each property name to the JSON field name.

/** One bus stop, from /stops. */
data class Stop(
    val id: Long,
    val code: String,          // used in URLs, e.g. "16044"
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val lineCodes: List<String>,
    val routeCodes: List<String>,
)

/** One line with its colours and route variants, from /lines. */
data class Line(
    val id: Long,
    val code: String,          // e.g. "01"
    val name: String,
    val color: String,         // badge background, e.g. "#787878"
    val textColor: String,
    val borderColor: String,
    val routes: List<Route>,
)

/** One route variant of a line (inside [Line.routes]). */
data class Route(
    val id: Long,
    val code: String,          // e.g. "001"
    val name: String,
    val direction: Int,        // 1 or 2, exact meaning still unknown
)

/** One entry of /routes/{routeCode}/sequence: the n-th stop of a route. */
data class RouteStop(
    val sequence: Int,
    val code: String,          // stop code, join with [Stop.code]
)

/** The map shape of one route, from /lines/{lineCode}/points. */
data class RouteShape(
    val routeCode: String,
    val routePoints: List<RoutePoint>,
)

/** One GPS point of a route shape. The API sends the coordinates as strings
 *  ("40.521436"), but Gson converts them to Double for us. */
data class RoutePoint(
    val id: Long,
    val sequence: Int,
    val latitude: Double,
    val longitude: Double,
)

/** One scheduled pass of a bus through a stop, from /trips/stop/{stopCode}/day/{day}. */
data class Trip(
    val id: Long,
    val day: Int,
    val routeCode: String,
    val routeName: String,
    val lineCode: String,
    val lineName: String,
    val lineColor: String,
    val lineTextColor: String,
    val stopCode: String,
    val stopName: String,
    val tripTime: String,      // "HH:mm", Greek local time
    val tripTimeHour: Int,
    val tripTimeMinute: Int,
)

/** Body of /stops/live/{stopCode}. We have never seen a real non-empty answer,
 *  so every field is nullable: a missing field becomes null instead of a crash. */
data class LiveResponse(
    val vehicles: List<LiveVehicle>?,
)

/** A bus that is coming to the stop, with its current position. */
data class LiveVehicle(
    val vehicleCode: String?,
    val lineCode: String?,
    val routeCode: String?,
    val routeName: String?,
    val lineColor: String?,
    val lineTextColor: String?,
    val borderColor: String?,
    val departureMins: Int?,     // minutes until it reaches the stop
    val departureSeconds: Int?,  // seconds until it reaches the stop
    val latitude: Double?,
    val longitude: Double?,
)
