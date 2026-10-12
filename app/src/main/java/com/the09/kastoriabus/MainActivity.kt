package com.the09.kastoriabus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.the09.kastoriabus.data.BusRepository
import com.the09.kastoriabus.ui.theme.KastoriaBusTheme
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.coroutines.cancellation.CancellationException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = (application as KastoriaBusApp).repository
        setContent {
            KastoriaBusTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DataCheckScreen(
                        repository = repository,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

// --- Temporary screen for step 2: calls every endpoint once and shows the results.
// --- It will be replaced by the real screens in step 3.

private const val TEST_STOP = "16044"  // ΝΤΙΝΗ
private const val TEST_LINE = "01"
private const val TEST_ROUTE = "001"

private data class DataCheckResult(
    val stops: Int,
    val lines: Int,
    val routeStops: Int,
    val shapes: Int,
    val stopName: String,
    val todayTrips: Int,
    val nextTimes: List<String>,
    val liveBuses: Int,
)

@Composable
fun DataCheckScreen(repository: BusRepository, modifier: Modifier = Modifier) {
    var attempt by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<DataCheckResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    // Runs when the screen appears, and again each time "attempt" changes (Try again).
    LaunchedEffect(attempt) {
        result = null
        error = null
        try {
            val stops = repository.getStops()
            val trips = repository.getTimetable(TEST_STOP)
            val now = LocalTime.now(ZoneId.of("Europe/Athens"))
                .format(DateTimeFormatter.ofPattern("HH:mm"))
            result = DataCheckResult(
                stops = stops.size,
                lines = repository.getLines().size,
                routeStops = repository.getRouteStops(TEST_ROUTE).size,
                shapes = repository.getLineShapes(TEST_LINE).size,
                stopName = stops.firstOrNull { it.code == TEST_STOP }?.name ?: TEST_STOP,
                todayTrips = trips.size,
                // "HH:mm" strings sort like times, so a simple >= comparison works.
                nextTimes = trips.filter { it.tripTime >= now }.take(5)
                    .map { "${it.tripTime} (${it.lineCode})" },
                liveBuses = repository.getLiveArrivals(TEST_STOP).size,
            )
        } catch (e: CancellationException) {
            throw e // the screen was closed: not an error
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
        }
    }

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stringResource(R.string.test_title), style = MaterialTheme.typography.headlineSmall)
        val r = result
        val e = error
        when {
            e != null -> {
                Text(stringResource(R.string.test_error, e), color = MaterialTheme.colorScheme.error)
                Button(onClick = { attempt++ }) { Text(stringResource(R.string.test_retry)) }
            }
            r == null -> Text(stringResource(R.string.test_loading))
            else -> {
                Text(stringResource(R.string.test_stops, r.stops))
                Text(stringResource(R.string.test_lines, r.lines))
                Text(stringResource(R.string.test_route_stops, TEST_ROUTE, r.routeStops))
                Text(stringResource(R.string.test_shapes, TEST_LINE, r.shapes))
                Text(stringResource(R.string.test_timetable, r.stopName, r.todayTrips))
                Text(
                    if (r.nextTimes.isEmpty()) stringResource(R.string.test_next_none)
                    else stringResource(R.string.test_next, r.nextTimes.joinToString(", "))
                )
                Text(stringResource(R.string.test_live, r.liveBuses))
                Button(onClick = { attempt++ }) { Text(stringResource(R.string.test_retry)) }
            }
        }
    }
}
