package com.the09.kastoriabus.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.the09.kastoriabus.KastoriaBusApp
import com.the09.kastoriabus.R
import com.the09.kastoriabus.ui.linedetail.LineDetailScreen
import com.the09.kastoriabus.ui.linedetail.LineDetailViewModel
import com.the09.kastoriabus.ui.lines.LinesScreen
import com.the09.kastoriabus.ui.lines.LinesViewModel
import com.the09.kastoriabus.ui.map.MapScreen
import com.the09.kastoriabus.ui.map.MapViewModel
import com.the09.kastoriabus.ui.stopdetail.StopDetailScreen
import com.the09.kastoriabus.ui.stopdetail.StopDetailViewModel
import com.the09.kastoriabus.ui.stops.StopsScreen
import com.the09.kastoriabus.ui.stops.StopsViewModel

/** The "addresses" of our screens. {lineCode} and {stopCode} are filled in when navigating. */
private object Routes {
    const val STOPS = "stops"
    const val MAP = "map?line={line}&stop={stop}" // both optional: what to show first
    const val LINES = "lines"
    const val LINE = "line/{lineCode}"
    const val STOP = "stop/{stopCode}"
    fun line(lineCode: String) = "line/$lineCode"
    fun stop(stopCode: String) = "stop/$stopCode"
    fun map(line: String? = null, stop: String? = null) =
        "map" + listOfNotNull(line?.let { "line=$it" }, stop?.let { "stop=$it" })
            .joinToString("&").let { if (it.isEmpty()) "" else "?$it" }
}

private val TOP_LEVEL = setOf(Routes.STOPS, Routes.MAP, Routes.LINES)

/** The whole app: the bottom tabs plus the screens they lead to. */
@Composable
fun KastoriaBusAppUi() {
    val navController = rememberNavController()
    val app = LocalContext.current.applicationContext as KastoriaBusApp
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    Scaffold(
        // Each screen draws its own header, so this outer Scaffold leaves the insets to them.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // The tabs are shown only on the three main screens, like the OASTH app.
            if (currentRoute in TOP_LEVEL) BottomTabs(navController, currentRoute)
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.STOPS,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding)
        ) {
            composable(Routes.STOPS) {
                val vm = viewModel { StopsViewModel(app.repository, app.favorites, app.location) }
                StopsScreen(vm, onStopClick = { navController.navigate(Routes.stop(it)) })
            }
            composable(
                Routes.MAP,
                arguments = listOf(
                    navArgument("line") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("stop") { type = NavType.StringType; nullable = true; defaultValue = null },
                )
            ) { entry ->
                val line = entry.arguments?.getString("line")
                val stop = entry.arguments?.getString("stop")
                val vm = viewModel { MapViewModel(app.repository, app.favorites, app.location, line, stop) }
                MapScreen(
                    viewModel = vm,
                    onStopDetailsClick = { navController.navigate(Routes.stop(it)) },
                    onLineClick = { navController.navigate(Routes.line(it)) }
                )
            }
            composable(Routes.LINES) {
                val vm = viewModel { LinesViewModel(app.repository, app.favorites) }
                LinesScreen(vm, onLineClick = { navController.navigate(Routes.line(it)) })
            }
            composable(Routes.LINE) { entry ->
                val lineCode = entry.arguments?.getString("lineCode").orEmpty()
                val vm = viewModel { LineDetailViewModel(lineCode, app.repository, app.favorites) }
                LineDetailScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onStopClick = { navController.navigate(Routes.stop(it)) },
                    onShowOnMap = { navController.openMap(Routes.map(line = it)) }
                )
            }
            composable(Routes.STOP) { entry ->
                val stopCode = entry.arguments?.getString("stopCode").orEmpty()
                val vm = viewModel { StopDetailViewModel(stopCode, app.repository, app.favorites) }
                StopDetailScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onLineClick = { navController.navigate(Routes.line(it)) },
                    onShowOnMap = { navController.openMap(Routes.map(stop = stopCode)) }
                )
            }
        }
    }
}

@Composable
private fun BottomTabs(navController: NavHostController, currentRoute: String?) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
        Tab(
            selected = currentRoute == Routes.STOPS,
            label = stringResource(R.string.tab_stops),
            icon = rememberVectorPainter(Icons.Filled.Place),
            onClick = { navController.switchTab(Routes.STOPS) }
        )
        Tab(
            selected = currentRoute == Routes.MAP,
            label = stringResource(R.string.tab_map),
            icon = painterResource(R.drawable.ic_map),
            onClick = { navController.switchTab(Routes.map()) }
        )
        Tab(
            selected = currentRoute == Routes.LINES,
            label = stringResource(R.string.tab_lines),
            icon = painterResource(R.drawable.ic_bus),
            onClick = { navController.switchTab(Routes.LINES) }
        )
    }
}

@Composable
private fun RowScope.Tab(
    selected: Boolean,
    label: String,
    icon: Painter,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
            indicatorColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
        )
    )
}

/** Switches tab the standard way: each tab keeps its scroll position, and Back doesn't cycle through tabs. */
private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Opens the Map tab showing a specific line or stop (a fresh map, not the saved one). */
private fun NavHostController.openMap(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
    }
}
