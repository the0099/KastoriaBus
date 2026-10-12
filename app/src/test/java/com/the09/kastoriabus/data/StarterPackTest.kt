package com.the09.kastoriabus.data

import com.the09.kastoriabus.data.model.Line
import com.the09.kastoriabus.data.model.LiveResponse
import com.the09.kastoriabus.data.model.RouteShape
import com.the09.kastoriabus.data.model.RouteStop
import com.the09.kastoriabus.data.model.Stop
import com.the09.kastoriabus.data.model.Trip
import com.the09.kastoriabus.data.remote.CitybusApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Response
import java.io.File
import java.io.IOException

class StarterPackTest {

    @get:Rule val folder = TemporaryFolder()

    // The real files that ship in the app (unit tests run from the app/ folder).
    private val pack = StarterPack({ path -> File("src/main/assets", path).takeIf { it.exists() }?.inputStream() })

    /** A server like the one on 2026-10-12: /stops answers with nothing, the rest is down. */
    private class BrokenServer(private val emptyStops: Boolean) : CitybusApi {
        override suspend fun getStops(lang: String): List<Stop> =
            if (emptyStops) emptyList() else throw IOException("no internet")
        override suspend fun getLines(lang: String): List<Line> = throw IOException("no internet")
        override suspend fun getRouteSequence(lang: String, routeCode: String): List<RouteStop> = throw IOException("no internet")
        override suspend fun getLineShapes(lineCode: String): List<RouteShape> = throw IOException("no internet")
        override suspend fun getStopTimetable(lang: String, stopCode: String, day: Int): List<Trip> = throw IOException("no internet")
        override suspend fun getLiveArrivals(lang: String, stopCode: String): Response<LiveResponse> = throw IOException("no internet")
    }

    @Test
    fun builtInFilesAreCompleteAndConsistent() = runBlocking {
        val repo = BusRepository(BrokenServer(emptyStops = false), DiskCache(folder.root), pack) { "el" }
        val stops = repo.getStops()
        val lines = repo.getLines()
        assertEquals(209, stops.size)
        assertEquals(18, lines.size)
        // Every stop of every route can be found (so line screens work offline).
        lines.flatMap { it.routes }.forEach { route ->
            assertTrue("route ${route.code}", repo.getRouteStops(route.code).isNotEmpty())
        }
        assertTrue(repo.usingSavedData.value)
    }

    @Test
    fun emptyAnswerFromServerFallsBackToBuiltInStops() = runBlocking {
        val repo = BusRepository(BrokenServer(emptyStops = true), DiskCache(folder.root), pack) { "el" }
        assertEquals(209, repo.getStops().size)
    }

    @Test
    fun englishFallsBackToGreekNames() = runBlocking {
        val repo = BusRepository(BrokenServer(emptyStops = false), DiskCache(folder.root), pack) { "en" }
        assertTrue(repo.getStops().any { it.name == "ΝΤΙΝΗ" })
    }
}
