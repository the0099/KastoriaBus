package com.the09.kastoriabus.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.the09.kastoriabus.data.model.LiveResponse
import com.the09.kastoriabus.data.model.RouteShape
import com.the09.kastoriabus.data.model.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Checks that Gson reads the JSON samples from kastoria-api-notes.txt correctly. */
class ApiModelsParsingTest {

    private val gson = Gson()

    @Test
    fun routePoints_stringCoordinatesBecomeDoubles() {
        val json = """[{"routeCode":"001","routePoints":[
            {"id":747109452,"sequence":1,"longitude":"21.261946","latitude":"40.521436"}]}]"""
        val shapes: List<RouteShape> =
            gson.fromJson(json, object : TypeToken<List<RouteShape>>() {}.type)
        val point = shapes[0].routePoints[0]
        assertEquals(40.521436, point.latitude, 1e-9)
        assertEquals(21.261946, point.longitude, 1e-9)
    }

    @Test
    fun trip_parses() {
        val json = """[{"id":62397132,"day":1,"routeCode":"031",
            "routeName":"ΜΕΣΣΟΠΟΤΑΜΙΑ - ΜΑΝΙΑΚΟΙ - ΚΑΣΤΟΡΙΑ","lineCode":"01",
            "lineName":"ΜΕΣΟΠΟΤΑΜΙΑ","lineColor":"#787878","lineTextColor":"#ffffff",
            "stopCode":"16044","stopName":"ΝΤΙΝΗ","tripTime":"06:59",
            "tripTimeHour":6,"tripTimeMinute":59}]"""
        val trips: List<Trip> = gson.fromJson(json, object : TypeToken<List<Trip>>() {}.type)
        assertEquals("06:59", trips[0].tripTime)
        assertEquals("ΝΤΙΝΗ", trips[0].stopName)
    }

    @Test
    fun live_missingFieldsBecomeNull() {
        // Guessed shape (no real sample yet): a numeric vehicleCode and missing fields.
        val json = """{"vehicles":[{"vehicleCode":1234,"lineCode":"06","departureMins":5}]}"""
        val live = gson.fromJson(json, LiveResponse::class.java)
        val bus = live.vehicles!![0]
        assertEquals("1234", bus.vehicleCode)
        assertEquals(5, bus.departureMins)
        assertNull(bus.latitude)
    }
}
