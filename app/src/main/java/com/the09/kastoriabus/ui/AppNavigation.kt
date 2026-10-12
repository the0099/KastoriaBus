package com.the09.kastoriabus.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.the09.kastoriabus.KastoriaBusApp
import com.the09.kastoriabus.R
import com.the09.kastoriabus.ui.linedetail.LineDetailScreen
import com.the09.kastoriabus.ui.linedetail.LineDetailViewModel
import com.the09.kastoriabus.ui.lines.LinesScreen
import com.the09.kastoriabus.ui.lines.LinesViewModel
import com.the09.kastoriabus.ui.stopdetail.StopDetailScreen
import com.the09.kastoriabus.ui.stopdetail.StopDetailViewModel
import com.the09.kastoriabus.ui.stops.StopsScreen
import com.the09.kastoriabus.ui.stops.StopsViewModel

/** The "addresses" of our screens. {lineCode} and {stopCode} are filled in when navigating. */
private object Routes {
    const val STOPS = "stops"
    const val LINES = "lines"
    const val LINE = "line/{lineCode}"
    const val STOP = "stop/{stopCode}"
    fun line(lineCode: String) = "line/$lineCode"
    fun stop(stopCode: String) = "stop/$stopCode"
}

/** The whole app: the bottom tabs plus the screens they lead to. */
@Composable
fun KastoriaBusAppUi() {
    val navController = rememberNavController()
    val app = LocalContext.current.applicationContext as KastoriaBusApp
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val onTopLevel = currentRoute == Routes.STOPS || currentRoute == Routes.LINES

    Scaffold(
        // Each screen draws its own top bar, so this outer Scaffold leaves the insets to them.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // The tabs are shown only on the two main screens, like the OASTH app.
            if (onTopLevel) BottomTabs(navController, currentRoute)
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
                    onStopClick = { navController.navigate(Routes.stop(it)) }
                )
            }
            composable(Routes.STOP) { entry ->
                val stopCode = entry.arguments?.getString("stopCode").orEmpty()
                val vm = viewModel { StopDetailViewModel(stopCode, app.repository, app.favorites) }
                StopDetailScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onLineClick = { navController.navigate(Routes.line(it)) }
                )
            }
        }
    }
}

@Composable
private fun BottomTabs(navController: NavHostController, currentRoute: String?) {
    NavigationBar {
        Tab(
            selected = currentRoute == Routes.STOPS,
            label = stringResource(R.string.tab_stops),
            icon = rememberVectorPainter(Icons.Filled.Place),
            onClick = { navController.switchTab(Routes.STOPS) }
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
        label = { Text(label) }
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
