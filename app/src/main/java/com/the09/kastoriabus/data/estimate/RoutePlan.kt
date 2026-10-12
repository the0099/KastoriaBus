package com.the09.kastoriabus.data.estimate

/** A stop of a route, with how far along the route (in metres) it is. */
data class PlanStop(val code: String, val name: String, val position: LatLon, val distance: Double)

/** "This trip is at stop number [stopIndex] at [seconds] after midnight." */
data class Anchor(val stopIndex: Int, val seconds: Int)

/** One bus trip of the day, known by its times at a few checkpoint stops. */
data class PlanTrip(val anchors: List<Anchor>) {
    val startSeconds: Int get() = anchors.first().seconds
    val endSeconds: Int get() = anchors.last().seconds
}

/** Where we think one bus is right now. */
data class EstimatedBus(
    val routeCode: String,
    val tripStartSeconds: Int,    // when it left the first stop
    val position: LatLon,
    val nextStopIndex: Int,       // the stop it is driving towards
    val secondsToNextStop: Int,
)

/**
 * Everything needed to estimate where the buses of one route are, for one day.
 * Built once (see [RoutePlanBuilder]); after that, [busesAt] is pure maths:
 * no network, so the map can move the buses every few seconds for free.
 */
class RoutePlan(
    val routeCode: String,
    val stops: List<PlanStop>,
    private val path: Polyline,
    val trips: List<PlanTrip>,
) {
    /** All buses of this route on the road at [nowSeconds] (seconds after midnight). */
    fun busesAt(nowSeconds: Int): List<EstimatedBus> = trips.mapNotNull { trip ->
        if (nowSeconds < trip.startSeconds || nowSeconds > trip.endSeconds) return@mapNotNull null

        // Between which two checkpoints is the bus? Assume steady speed between them.
        val b = trip.anchors.indexOfFirst { it.seconds >= nowSeconds }.coerceAtLeast(1)
        val from = trip.anchors[b - 1]
        val to = trip.anchors[b]
        val dFrom = stops[from.stopIndex].distance
        val dTo = stops[to.stopIndex].distance
        val f = if (to.seconds == from.seconds) 1.0
                else (nowSeconds - from.seconds).toDouble() / (to.seconds - from.seconds)
        val distance = dFrom + (dTo - dFrom) * f.coerceIn(0.0, 1.0)

        val next = stops.indexOfFirst { it.distance >= distance - 1 }.let { if (it == -1) stops.lastIndex else it }
        EstimatedBus(
            routeCode = routeCode,
            tripStartSeconds = trip.startSeconds,
            position = path.pointAt(distance),
            nextStopIndex = next,
            secondsToNextStop = (timeAtDistance(trip, stops[next].distance) - nowSeconds).coerceAtLeast(0),
        )
    }

    /** When the trip passes the point [distance] metres along the route. */
    private fun timeAtDistance(trip: PlanTrip, distance: Double): Int {
        val anchors = trip.anchors
        for (i in 1 until anchors.size) {
            val a = anchors[i - 1]
            val b = anchors[i]
            val da = stops[a.stopIndex].distance
            val db = stops[b.stopIndex].distance
            if (distance <= db) {
                val f = if (db == da) 1.0 else ((distance - da) / (db - da)).coerceIn(0.0, 1.0)
                return (a.seconds + (b.seconds - a.seconds) * f).toInt()
            }
        }
        return anchors.last().seconds
    }
}

/** Builds a [RoutePlan] from the raw API data. Pure logic, so it is unit-tested. */
object RoutePlanBuilder {

    /** How many checkpoint stops per route we download timetables for. */
    const val MAX_ANCHORS = 6

    /**
     * Picks the checkpoint stops: spread evenly along the route, never the last stop
     * (it has no departures in the timetable) and never a stop the route visits twice
     * (we couldn't tell which visit a time belongs to).
     */
    fun chooseAnchorIndices(stopCodes: List<String>): List<Int> {
        if (stopCodes.size < 2) return emptyList()
        val counts = stopCodes.groupingBy { it }.eachCount()
        val candidates = (0 until stopCodes.lastIndex).filter { counts[stopCodes[it]] == 1 }
        if (candidates.size <= MAX_ANCHORS) return candidates
        return (0 until MAX_ANCHORS).map { k -> candidates[k * (candidates.size - 1) / (MAX_ANCHORS - 1)] }.distinct()
    }

    /**
     * @param stops the route's stops in driving order: code, name, position
     * @param shape the route drawing from the API, or empty to just join the stops
     * @param timesAtAnchors for each checkpoint stop index, this route's times there
     *        today, in seconds after midnight
     */
    fun build(
        routeCode: String,
        stops: List<Triple<String, String, LatLon>>,
        shape: List<LatLon>,
        timesAtAnchors: Map<Int, List<Int>>,
    ): RoutePlan {
        val path = Polyline(if (shape.size >= 2) shape else stops.map { it.third })

        // Find each stop on the drawing, going forward only.
        var searchFrom = 0
        var lastDistance = 0.0
        val planStops = stops.map { (code, name, position) ->
            val index = path.closestIndex(position, searchFrom)
            searchFrom = index
            lastDistance = maxOf(lastDistance, path.cumulative[index])
            PlanStop(code, name, position, lastDistance)
        }

        // The k-th time at every checkpoint belongs to the k-th trip of the day.
        // Only use checkpoints that agree on the number of trips (the most common count).
        val sorted = timesAtAnchors.mapValues { it.value.sorted() }.filterValues { it.isNotEmpty() }
        val tripCount = sorted.values.groupingBy { it.size }.eachCount().maxByOrNull { it.value }?.key ?: 0
        val usable = sorted.filterValues { it.size == tripCount }.toSortedMap()

        val trips = (0 until tripCount).mapNotNull { k ->
            val anchors = usable.map { (stopIndex, times) -> Anchor(stopIndex, times[k]) }.toMutableList()
            if (anchors.size < 2) return@mapNotNull null
            // Times must go forward along the route; otherwise the data doesn't fit, skip it.
            if (anchors.zipWithNext().any { (a, b) -> b.seconds < a.seconds }) return@mapNotNull null
            // The last stop has no time in the timetable: guess it from the trip's average speed.
            val last = anchors.last()
            if (last.stopIndex < planStops.lastIndex) {
                val first = anchors.first()
                val metres = planStops[last.stopIndex].distance - planStops[first.stopIndex].distance
                val seconds = last.seconds - first.seconds
                if (metres > 0 && seconds > 0) {
                    val remaining = planStops.last().distance - planStops[last.stopIndex].distance
                    anchors += Anchor(planStops.lastIndex, last.seconds + (remaining * seconds / metres).toInt())
                }
            }
            PlanTrip(anchors)
        }
        return RoutePlan(routeCode, planStops, path, trips)
    }
}
