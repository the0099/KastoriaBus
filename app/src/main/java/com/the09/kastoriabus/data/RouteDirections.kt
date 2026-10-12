package com.the09.kastoriabus.data

import com.the09.kastoriabus.data.estimate.LatLon
import com.the09.kastoriabus.data.estimate.distanceMeters

/** Μετάβαση (from Kastoria to the destination) or Επιστροφή (back to Kastoria). */
enum class Direction { OUTBOUND, RETURN }

/** How a route's stop list divides into the two directions. */
sealed interface RouteSplit {
    /** A short loop that never leaves town: no outbound/return. */
    data object Loop : RouteSplit

    /** The whole route goes one way, e.g. "Μεσοποταμία → Καστοριά". */
    data class OneWay(val direction: Direction) : RouteSplit

    /** Kastoria → destination → Kastoria in one route, turning at stop [turnIndex]. */
    data class RoundTrip(val turnIndex: Int) : RouteSplit

    /** The stop indexes of this route that belong to [direction], or null if none. */
    fun range(direction: Direction, stopCount: Int): IntRange? = when (this) {
        Loop -> 0 until stopCount
        is OneWay -> if (this.direction == direction) 0 until stopCount else null
        is RoundTrip -> if (direction == Direction.OUTBOUND) 0..turnIndex else turnIndex until stopCount
    }
}

/**
 * Works out Μετάβαση / Επιστροφή for a route. The API's "direction" field does NOT
 * mean this (route 001, a round trip, and route 031, which only comes back, both have 2),
 * and most main routes are ONE round trip: ΑΦΕΤΗΡΙΑ → village → ΑΦΕΤΗΡΙΑ.
 * So we decide from the stop positions (checked by hand against all 47 routes):
 *  - starts in town, ends outside       → all Μετάβαση
 *  - starts outside, ends in town       → all Επιστροφή
 *  - starts and ends in town            → round trip, split at the halfway point of the
 *    distance travelled (or at a clearly farther stop near it)
 *  - never more than 1.5 km from centre → a town loop, not split
 */
object RouteDirections {

    /** ΑΦΕΤΗΡΙΑ (stop 16001), the main terminal in the centre of Kastoria. */
    val KASTORIA_CENTRE = LatLon(40.521405, 21.261869)

    private const val TOWN_RADIUS = 3_000.0     // metres from the centre that count as "in town"
    private const val LOOP_RADIUS = 1_500.0     // a route that never goes farther is a town loop
    private const val CLEARLY_FARTHER = 500.0   // a stop must be this much farther to move the turn

    fun split(stops: List<LatLon>, centre: LatLon = KASTORIA_CENTRE): RouteSplit {
        if (stops.size < 2) return RouteSplit.Loop
        val fromCentre = stops.map { distanceMeters(centre, it) }
        val startsInTown = fromCentre.first() < TOWN_RADIUS
        val endsInTown = fromCentre.last() < TOWN_RADIUS

        return when {
            startsInTown && endsInTown -> {
                if (fromCentre.max() < LOOP_RADIUS) RouteSplit.Loop
                else RouteSplit.RoundTrip(turnIndex(stops, fromCentre))
            }
            startsInTown -> RouteSplit.OneWay(Direction.OUTBOUND)
            endsInTown -> RouteSplit.OneWay(Direction.RETURN)
            // Both ends outside town (rare): going away from Kastoria counts as Μετάβαση.
            fromCentre.last() > fromCentre.first() -> RouteSplit.OneWay(Direction.OUTBOUND)
            else -> RouteSplit.OneWay(Direction.RETURN)
        }
    }

    /**
     * The stop where a round trip turns back. We use the halfway point of the distance
     * travelled (straight-line "farthest stop" fails around the lake: on line 2 Κρεπένι is
     * farther from the centre than Πολυκάρπη itself). Only if that halfway stop is still in
     * the centre (a route that passes the terminal mid-way) do we use the farthest stop of
     * the middle part instead.
     */
    private fun turnIndex(stops: List<LatLon>, fromCentre: List<Double>): Int {
        val travelled = DoubleArray(stops.size)
        for (i in 1 until stops.size) travelled[i] = travelled[i - 1] + distanceMeters(stops[i - 1], stops[i])
        val total = travelled.last()
        val halfway = stops.indices.minBy { kotlin.math.abs(travelled[it] - total / 2) }

        val middle = stops.indices.filter { travelled[it] in total * 0.3..total * 0.7 }
        val farthest = middle.maxByOrNull { fromCentre[it] } ?: halfway
        val turn = if (fromCentre[halfway] < LOOP_RADIUS && fromCentre[farthest] > fromCentre[halfway] + CLEARLY_FARTHER) {
            farthest
        } else {
            halfway
        }
        // Each direction must have at least two stops.
        return turn.coerceIn(1, stops.lastIndex - 1)
    }
}
