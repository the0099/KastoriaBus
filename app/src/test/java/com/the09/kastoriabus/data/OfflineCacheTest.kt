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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import java.io.File
import java.io.IOException

class OfflineCacheTest {

    @get:Rule val folder = TemporaryFolder()

    /** A pretend server we can switch off, counting how often it is called. */
    private class FakeApi : CitybusApi {
        var online = true
        var calls = 0
        private val stop = Stop(1, "16044", "ΝΤΙΝΗ", 40.5, 21.2, listOf("01"), listOf("001"))

        private fun <T> answer(value: T): T {
            calls++
            if (!online) throw IOException("no internet")
            return value
        }

        override suspend fun getStops(lang: String) = answer(listOf(stop))
        override suspend fun getLines(lang: String) = answer(emptyList<Line>())
        override suspend fun getRouteSequence(lang: String, routeCode: String) = answer(listOf(RouteStop(1, "16044")))
        override suspend fun getLineShapes(lineCode: String) = answer(emptyList<RouteShape>())
        override suspend fun getStopTimetable(lang: String, stopCode: String, day: Int): Response<List<Trip>> =
            if (stopCode == "school") answer(Response.error(404, "{}".toResponseBody()))
            else answer(Response.success(emptyList()))
        override suspend fun getLiveArrivals(lang: String, stopCode: String): Response<LiveResponse> =
            throw UnsupportedOperationException()
    }

    private fun repository(api: FakeApi, dir: File) = BusRepository(api, DiskCache(dir)) { "el" }

    @Test
    fun noBusesThatDayIsAnEmptyTimetableNotAnError() = runBlocking {
        // The server answers 404 for a school stop on Sunday.
        assertEquals(emptyList<Trip>(), repository(FakeApi(), folder.root).getTimetable("school", 0))
    }

    @Test
    fun diskCacheRoundTrip() {
        val cache = DiskCache(folder.root)
        cache.write("timetable|el|16044|1", listOf("a", "b"))
        val entry = cache.read<List<String>>("timetable|el|16044|1", List::class.java)
        assertEquals(listOf("a", "b"), entry?.value)
        assertTrue(cache.sizeBytes() > 0)
    }

    @Test
    fun brokenFileIsIgnored() {
        val cache = DiskCache(folder.root)
        cache.write("stops|el", listOf(1))
        File(folder.root, "offline_v1").listFiles()!!.first().writeText("{ not json")
        assertEquals(null, cache.read<List<Int>>("stops|el", List::class.java))
    }

    @Test
    fun savedDataIsUsedWithoutInternet() = runBlocking {
        val api = FakeApi()
        repository(api, folder.root).getStops() // online: downloads and saves

        // A new app start (empty memory), no internet: the saved copy is used.
        api.online = false
        val offlineRepo = repository(api, folder.root)
        assertEquals("ΝΤΙΝΗ", offlineRepo.getStops().single().name)
    }

    @Test
    fun freshSavedDataDoesNotCallTheServer() = runBlocking {
        val api = FakeApi()
        repository(api, folder.root).getStops()
        val callsAfterFirst = api.calls
        repository(api, folder.root).getStops() // new app start, saved copy is fresh
        assertEquals(callsAfterFirst, api.calls)
    }

    @Test
    fun noInternetAndNothingSavedIsAnError() = runBlocking {
        val api = FakeApi().apply { online = false }
        try {
            repository(api, folder.root).getStops()
            fail("expected an IOException")
        } catch (e: IOException) {
            // expected
        }
    }

    @Test
    fun forcedRefreshFailsWithoutInternetInsteadOfUsingOldData() = runBlocking {
        val api = FakeApi()
        val repo = repository(api, folder.root)
        repo.getStops()
        api.online = false
        try {
            repo.getStops(forceRefresh = true)
            fail("expected an IOException")
        } catch (e: IOException) {
            // expected: the offline download must not pretend it succeeded
        }
        assertFalse(repo.usingSavedData.value)
    }
}
