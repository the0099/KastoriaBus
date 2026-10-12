package com.the09.kastoriabus.data

import com.the09.kastoriabus.data.estimate.LatLon
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteDirectionsTest {

    private val centre = RouteDirections.KASTORIA_CENTRE

    /** A point [km] kilometres north of the centre (1 km ≈ 0.008993° of latitude). */
    private fun north(km: Double) = LatLon(centre.lat + km * 0.008993, centre.lon)
    private fun east(km: Double) = LatLon(centre.lat, centre.lon + km * 0.01183)

    @Test
    fun outAndBackIsSplitAtTheFarEnd() {
        // Centre -> 8 km north -> back to centre.
        val stops = (0..8).map { north(it.toDouble()) } + (7 downTo 0).map { north(it.toDouble()) }
        val split = RouteDirections.split(stops)
        assertEquals(RouteSplit.RoundTrip(8), split)
        assertEquals(0..8, split.range(Direction.OUTBOUND, stops.size))
        assertEquals(8 until stops.size, split.range(Direction.RETURN, stops.size))
    }

    @Test
    fun fromVillageToTownIsReturnOnly() {
        val stops = (9 downTo 0).map { north(it.toDouble()) }
        val split = RouteDirections.split(stops)
        assertEquals(RouteSplit.OneWay(Direction.RETURN), split)
        assertEquals(null, split.range(Direction.OUTBOUND, stops.size))
    }

    @Test
    fun fromTownToVillageIsOutboundOnly() {
        val stops = (0..9).map { north(it.toDouble()) }
        assertEquals(RouteSplit.OneWay(Direction.OUTBOUND), RouteDirections.split(stops))
    }

    @Test
    fun shortTownLoopIsNotSplit() {
        val stops = listOf(centre, north(0.5), east(1.0), centre)
        assertEquals(RouteSplit.Loop, RouteDirections.split(stops))
    }

    @Test
    fun aroundTheLakeTurnsAtTheTerminusNotAtTheFarthestPoint() {
        // Like line 2: go 6 km east (Κρεπένι), then north-west (Πολυκάρπη, only ~5.2 km
        // from the centre in a straight line), then back the same way.
        val outbound = (0..6).map { east(it.toDouble()) } +
            (1..4).map { LatLon(east(6.0).lat + it * 0.008993, east(6.0).lon - it * 0.008) }
        val stops = outbound + outbound.dropLast(1).reversed()
        val split = RouteDirections.split(stops) as RouteSplit.RoundTrip
        assertEquals(outbound.lastIndex, split.turnIndex)
    }

    @Test
    fun routeThatPassesTheTerminalMidwayTurnsAtItsFarPoint() {
        // Like line 15: centre -> 2.3 km east -> back through the centre -> 1 km north -> centre.
        val stops = listOf(centre, east(1.0), east(2.3), east(1.2), centre, north(0.5), north(1.0), north(0.4))
        val split = RouteDirections.split(stops) as RouteSplit.RoundTrip
        assertEquals(2, split.turnIndex)
    }
}
