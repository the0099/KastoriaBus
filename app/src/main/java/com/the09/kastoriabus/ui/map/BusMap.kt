package com.the09.kastoriabus.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.content.ContextCompat
import com.the09.kastoriabus.R
import com.the09.kastoriabus.data.OfflineMapDownloader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.the09.kastoriabus.data.model.RouteShape
import com.the09.kastoriabus.data.model.Stop
import kotlinx.coroutines.flow.Flow
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

// Free OpenStreetMap-based map style from OpenFreeMap: no account or API key needed.
// The same address as the saved offline map, so the map works without internet.
private const val STYLE_URL = OfflineMapDownloader.STYLE_URL
private val KASTORIA_CENTER = LatLng(40.5193, 21.2687)

private const val SRC_SHAPES = "shapes"
private const val SRC_STOPS = "stops"
private const val SRC_SELECTED = "selected"
private const val SRC_USER = "user"
private const val SRC_BUSES = "buses"
private const val SRC_SELECTED_BUS = "selected-bus"
private const val LAYER_STOPS = "stops-layer"
private const val LAYER_BUSES = "buses-layer"
private const val BUS_ICON = "bus-icon"

/**
 * The MapLibre map inside Compose. MapLibre is an older "View" (not Compose),
 * so we wrap it in an AndroidView and forward the screen's lifecycle to it.
 *
 * Instead of hundreds of marker objects, the stops are one GeoJSON "source" drawn by a
 * circle "layer": the map draws them all at once, which is fast and easy to restyle.
 */
@Composable
fun BusMap(
    stops: List<Stop>,
    stopColorHex: String,
    shapes: List<RouteShape>,
    shapeColorHex: String,
    selectedStop: Stop?,
    buses: List<BusMarker>,
    selectedBus: BusMarker?,
    busColor: Int,
    userLatLng: LatLng?,
    cameraCommands: Flow<CameraCommand>,
    contentPaddingTop: Dp,
    contentPaddingBottom: Dp,
    onStopClick: (String) -> Unit,
    onBusClick: (String) -> Unit,
    onMapClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mapView = rememberMapViewWithLifecycle()
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val currentOnStopClick by rememberUpdatedState(onStopClick)
    val currentOnBusClick by rememberUpdatedState(onBusClick)
    val context = LocalContext.current
    val currentOnMapClick by rememberUpdatedState(onMapClick)
    val density = LocalDensity.current
    val topPx = with(density) { contentPaddingTop.roundToPx() }
    val bottomPx = with(density) { contentPaddingBottom.roundToPx() }
    val sidePx = with(density) { Dp(32f).roundToPx() }

    // Set up the map once: style, layers and the tap listener.
    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.cameraPosition = CameraPosition.Builder().target(KASTORIA_CENTER).zoom(13.0).build()
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            m.setStyle(Style.Builder().fromUri(STYLE_URL)) { loaded ->
                addLayers(loaded)
                style = loaded
            }
            m.addOnMapClickListener { latLng ->
                // Did the tap hit a bus or a stop? Look in a small square around the finger.
                val p = m.projection.toScreenLocation(latLng)
                val r = with(density) { Dp(14f).toPx() }
                val box = RectF(p.x - r, p.y - r, p.x + r, p.y + r)
                val bus = m.queryRenderedFeatures(box, LAYER_BUSES).firstOrNull()?.getStringProperty("id")
                val stop = m.queryRenderedFeatures(box, LAYER_STOPS).firstOrNull()?.getStringProperty("code")
                when {
                    bus != null -> currentOnBusClick(bus)
                    stop != null -> currentOnStopClick(stop)
                    else -> currentOnMapClick()
                }
                true
            }
            map = m
        }
    }

    // Each time the data changes, give the map the new GeoJSON.
    LaunchedEffect(style, stops, stopColorHex) {
        style?.getSourceAs<GeoJsonSource>(SRC_STOPS)?.setGeoJson(
            FeatureCollection.fromFeatures(stops.map { stop ->
                Feature.fromGeometry(Point.fromLngLat(stop.longitude, stop.latitude)).apply {
                    addStringProperty("code", stop.code)
                    addStringProperty("color", stopColorHex)
                }
            })
        )
    }
    LaunchedEffect(style, shapes, shapeColorHex) {
        style?.getSourceAs<GeoJsonSource>(SRC_SHAPES)?.setGeoJson(
            FeatureCollection.fromFeatures(shapes.map { shape ->
                Feature.fromGeometry(
                    LineString.fromLngLats(
                        shape.routePoints.sortedBy { it.sequence }.map { Point.fromLngLat(it.longitude, it.latitude) }
                    )
                ).apply { addStringProperty("color", shapeColorHex) }
            })
        )
    }
    LaunchedEffect(style, selectedStop) {
        style?.getSourceAs<GeoJsonSource>(SRC_SELECTED)?.setGeoJson(
            FeatureCollection.fromFeatures(listOfNotNull(selectedStop?.let {
                Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude))
            }))
        )
    }
    // The bus icon is redrawn in the colour of the chosen line.
    LaunchedEffect(style, busColor) {
        style?.addImage(BUS_ICON, busIconBitmap(context, busColor))
    }
    LaunchedEffect(style, buses) {
        style?.getSourceAs<GeoJsonSource>(SRC_BUSES)?.setGeoJson(
            FeatureCollection.fromFeatures(buses.map { bus ->
                Feature.fromGeometry(Point.fromLngLat(bus.position.longitude, bus.position.latitude)).apply {
                    addStringProperty("id", bus.id)
                }
            })
        )
    }
    LaunchedEffect(style, selectedBus) {
        style?.getSourceAs<GeoJsonSource>(SRC_SELECTED_BUS)?.setGeoJson(
            FeatureCollection.fromFeatures(listOfNotNull(selectedBus?.let {
                Feature.fromGeometry(Point.fromLngLat(it.position.longitude, it.position.latitude))
            }))
        )
    }
    LaunchedEffect(style, userLatLng) {
        style?.getSourceAs<GeoJsonSource>(SRC_USER)?.setGeoJson(
            FeatureCollection.fromFeatures(listOfNotNull(userLatLng?.let {
                Feature.fromGeometry(Point.fromLngLat(it.longitude, it.latitude))
            }))
        )
    }

    // Camera moves asked by the ViewModel ("show this line", "go to my location").
    LaunchedEffect(map) {
        val m = map ?: return@LaunchedEffect
        cameraCommands.collect { command ->
            when (command) {
                is CameraCommand.Center -> m.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(LatLng(command.point.latitude, command.point.longitude), command.zoom),
                    800
                )
                is CameraCommand.Fit -> {
                    val bounds = LatLngBounds.Builder()
                        .includes(command.points.map { LatLng(it.latitude, it.longitude) })
                        .build()
                    m.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, sidePx, topPx, sidePx, bottomPx), 800)
                }
            }
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

/** Adds the empty sources and the layers that draw them, bottom to top. */
private fun addLayers(style: Style) {
    style.addSource(GeoJsonSource(SRC_SHAPES))
    style.addSource(GeoJsonSource(SRC_STOPS))
    style.addSource(GeoJsonSource(SRC_SELECTED))
    style.addSource(GeoJsonSource(SRC_USER))
    style.addSource(GeoJsonSource(SRC_SELECTED_BUS))
    style.addSource(GeoJsonSource(SRC_BUSES))

    // Route lines: thick and rounded, in the line's colour.
    style.addLayer(
        LineLayer("shapes-layer", SRC_SHAPES).withProperties(
            PropertyFactory.lineColor(Expression.toColor(Expression.get("color"))),
            PropertyFactory.lineWidth(5f),
            PropertyFactory.lineOpacity(0.85f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
    )
    // Stops: white dots with a coloured ring that grow when you zoom in.
    style.addLayer(
        CircleLayer(LAYER_STOPS, SRC_STOPS).withProperties(
            PropertyFactory.circleRadius(
                Expression.interpolate(
                    Expression.linear(), Expression.zoom(),
                    Expression.stop(11, 3f), Expression.stop(14, 6f), Expression.stop(17, 10f)
                )
            ),
            PropertyFactory.circleColor("#FFFFFF"),
            PropertyFactory.circleStrokeColor(Expression.toColor(Expression.get("color"))),
            PropertyFactory.circleStrokeWidth(
                Expression.interpolate(
                    Expression.linear(), Expression.zoom(),
                    Expression.stop(11, 1.5f), Expression.stop(15, 3.5f)
                )
            ),
        )
    )
    // The tapped stop: a big orange dot with a white ring.
    style.addLayer(
        CircleLayer("selected-layer", SRC_SELECTED).withProperties(
            PropertyFactory.circleRadius(12f),
            PropertyFactory.circleColor("#FF7A1A"),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(4f),
        )
    )
    // Estimated buses: an orange glow behind the tapped one, then the bus icons on top.
    style.addLayer(
        CircleLayer("selected-bus-layer", SRC_SELECTED_BUS).withProperties(
            PropertyFactory.circleRadius(26f),
            PropertyFactory.circleColor("#FF7A1A"),
            PropertyFactory.circleOpacity(0.35f),
        )
    )
    style.addLayer(
        SymbolLayer(LAYER_BUSES, SRC_BUSES).withProperties(
            PropertyFactory.iconImage(BUS_ICON),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
        )
    )
    // "You are here": a soft blue halo and a blue dot.
    style.addLayer(
        CircleLayer("user-halo-layer", SRC_USER).withProperties(
            PropertyFactory.circleRadius(22f),
            PropertyFactory.circleColor("#1A9BE6"),
            PropertyFactory.circleOpacity(0.2f),
        )
    )
    style.addLayer(
        CircleLayer("user-layer", SRC_USER).withProperties(
            PropertyFactory.circleRadius(8f),
            PropertyFactory.circleColor("#1A9BE6"),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(3f),
        )
    )
}

/**
 * Creates the MapView and passes it the screen's lifecycle (start, pause, stop...),
 * which MapLibre needs to start/stop drawing and free memory.
 */
@Composable
private fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val mapView = remember { MapView(context).apply { onCreate(null) } }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(lifecycle, mapView) {
        var started = false
        var resumed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> { mapView.onStart(); started = true }
                Lifecycle.Event.ON_RESUME -> { mapView.onResume(); resumed = true }
                Lifecycle.Event.ON_PAUSE -> { mapView.onPause(); resumed = false }
                Lifecycle.Event.ON_STOP -> { mapView.onStop(); started = false }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            // Leaving the Map tab: shut the map down in the right order.
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            mapView.onDestroy()
        }
    }
    return mapView
}

/** Draws the bus marker: a circle in the line's colour, white ring, white bus inside. */
private fun busIconBitmap(context: Context, color: Int): Bitmap {
    val size = (40 * context.resources.displayMetrics.density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val radius = size / 2f
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(radius, radius, radius, paint)
    paint.color = color
    canvas.drawCircle(radius, radius, radius * 0.84f, paint)
    ContextCompat.getDrawable(context, R.drawable.ic_bus)?.mutate()?.let { bus ->
        bus.setTint(android.graphics.Color.WHITE)
        val inset = (size * 0.24f).toInt()
        bus.setBounds(inset, inset, size - inset, size - inset)
        bus.draw(canvas)
    }
    return bitmap
}
