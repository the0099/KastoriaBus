package com.the09.kastoriabus.data.estimate

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A point on Earth. Plain Kotlin (not Android's Location) so it works in unit tests. */
data class LatLon(val lat: Double, val lon: Double)

/** Distance in metres between two points ("haversine" formula for a round Earth). */
fun distanceMeters(a: LatLon, b: LatLon): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val h = sin(dLat / 2).let { it * it } +
        cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).let { it * it }
    return 2 * r * asin(sqrt(h))
}

/**
 * A route drawn as a chain of points. It knows how far along the route each
 * point is, so we can ask "where is the point 3.6 km after the start?".
 */
class Polyline(val points: List<LatLon>) {
    /** cumulative[i] = metres from the first point to point i. */
    val cumulative: DoubleArray = DoubleArray(points.size).also { cum ->
        for (i in 1 until points.size) cum[i] = cum[i - 1] + distanceMeters(points[i - 1], points[i])
    }
    val length: Double get() = cumulative.lastOrNull() ?: 0.0

    /** The point that is [meters] along the route (clamped to the start/end). */
    fun pointAt(meters: Double): LatLon {
        if (points.size == 1 || meters <= 0) return points.first()
        if (meters >= length) return points.last()
        // Binary search for the segment that contains this distance.
        var lo = 0
        var hi = points.lastIndex
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (cumulative[mid] <= meters) lo = mid else hi = mid
        }
        val segment = cumulative[hi] - cumulative[lo]
        val f = if (segment == 0.0) 0.0 else (meters - cumulative[lo]) / segment
        val a = points[lo]
        val b = points[hi]
        return LatLon(a.lat + (b.lat - a.lat) * f, a.lon + (b.lon - a.lon) * f)
    }

    /**
     * Finds the route point closest to [p], looking only forward from [fromIndex]
     * (a bus never drives backwards along its route). It stops searching once it has
     * found a close point and the route has clearly moved away again, so on a route
     * that passes the same street twice we pick the first pass, not the later one.
     */
    fun closestIndex(p: LatLon, fromIndex: Int): Int {
        var best = fromIndex
        var bestDistance = Double.MAX_VALUE
        for (j in fromIndex until points.size) {
            val d = distanceMeters(p, points[j])
            if (d < bestDistance) {
                bestDistance = d
                best = j
            }
            if (bestDistance < 40 && d > bestDistance + 300) break
        }
        return best
    }
}
