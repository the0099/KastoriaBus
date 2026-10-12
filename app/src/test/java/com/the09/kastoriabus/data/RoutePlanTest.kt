package com.the09.kastoriabus.data

import com.the09.kastoriabus.data.estimate.LatLon
import com.the09.kastoriabus.data.estimate.RoutePlanBuilder
import com.the09.kastoriabus.data.estimate.distanceMeters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePlanTest {

    // A straight 4 km road going north, with 5 stops 1 km apart.
    private val stops = (0..4).map { i ->
        Triple("S$i", "Stop $i", LatLon(40.50 + i * 0.008993, 21.26))
    }
    private val shape = (0..40).map { LatLon(40.50 + it * 0.0008993, 21.26) }

    private fun hm(h: Int, m: Int) = h * 3600 + m * 60

    // Two trips (10:00 and 12:00), each taking 3 minutes per km.
    private val times = mapOf(
        0 to listOf(hm(12, 0), hm(10, 0)), // unsorted on purpose
        2 to listOf(hm(10, 6), hm(12, 6)),
        3 to listOf(hm(10, 9), hm(12, 9)),
    )

    private val plan = RoutePlanBuilder.build("001", stops, shape, times)

    @Test
    fun stopsGetDistancesAlongRoute() {
        assertEquals(0.0, plan.stops[0].distance, 50.0)
        assertEquals(2000.0, plan.stops[2].distance, 50.0)
        assertEquals(4000.0, plan.stops[4].distance, 50.0)
    }

    @Test
    fun lastStopTimeIsGuessedFromAverageSpeed() {
        val trip = plan.trips.first()
        assertEquals(4, trip.anchors.last().stopIndex)
        assertEquals(hm(10, 12), trip.anchors.last().seconds) // 1 km more at 3 min/km
    }

    @Test
    fun noBusesBeforeFirstOrAfterLastTrip() {
        assertTrue(plan.busesAt(hm(9, 59)).isEmpty())
        assertTrue(plan.busesAt(hm(11, 0)).isEmpty())
    }

    @Test
    fun busHalfwayBetweenCheckpoints() {
        // 10:03 = halfway between stop 0 (10:00) and stop 2 (10:06) -> 1 km along = at stop 1.
        val bus = plan.busesAt(hm(10, 3)).single()
        assertEquals(hm(10, 0), bus.tripStartSeconds)
        assertEquals(0.0, distanceMeters(bus.position, stops[1].third), 60.0)
        assertEquals(1, bus.nextStopIndex)
    }

    @Test
    fun nextStopAndCountdown() {
        // 10:04 -> 1.33 km along: next stop is stop 2 (2 km), reached at 10:06.
        val bus = plan.busesAt(hm(10, 4)).single()
        assertEquals(2, bus.nextStopIndex)
        assertEquals(120.0, bus.secondsToNextStop.toDouble(), 15.0)
    }

    @Test
    fun anchorsSkipLastAndRepeatedStops() {
        val codes = listOf("A", "B", "C", "B", "D", "E")
        // B is visited twice and E is the last stop: neither can be a checkpoint.
        assertEquals(listOf(0, 2, 4), RoutePlanBuilder.chooseAnchorIndices(codes))
    }

    @Test
    fun anchorsAreLimitedAndSpread() {
        val codes = (1..53).map { "S$it" }
        val anchors = RoutePlanBuilder.chooseAnchorIndices(codes)
        assertEquals(RoutePlanBuilder.MAX_ANCHORS, anchors.size)
        assertEquals(0, anchors.first())
        assertEquals(51, anchors.last()) // second-to-last stop
    }
}
