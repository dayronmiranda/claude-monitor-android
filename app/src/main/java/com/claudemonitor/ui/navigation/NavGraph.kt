package com.claudemonitor.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.claudemonitor.ui.screens.drivers.DriversScreen
import com.claudemonitor.ui.screens.projects.ProjectsScreen
import com.claudemonitor.ui.screens.sessions.SessionsScreen
import com.claudemonitor.ui.screens.terminals.TerminalsScreen
import com.claudemonitor.ui.screens.terminal.TerminalScreen
import java.net.URLDecoder
import java.net.URLEncoder

sealed class Screen(val route: String) {
    data object Drivers : Screen("drivers")
    data object Projects : Screen("projects/{driverId}") {
        fun createRoute(driverId: String) = "projects/$driverId"
    }
    data object Sessions : Screen("sessions/{driverId}/{projectPath}") {
        fun createRoute(driverId: String, projectPath: String): String {
            val encodedPath = URLEncoder.encode(projectPath, "UTF-8")
            return "sessions/$driverId/$encodedPath"
        }
    }
    data object Terminals : Screen("terminals/{driverId}") {
        fun createRoute(driverId: String) = "terminals/$driverId"
    }
    data object Terminal : Screen("terminal/{driverId}/{terminalId}") {
        fun createRoute(driverId: String, terminalId: String) = "terminal/$driverId/$terminalId"
    }
}

@Composable
fun NavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Drivers.route
    ) {
        composable(Screen.Drivers.route) {
            DriversScreen(
                onDriverClick = { driverId ->
                    navController.navigate(Screen.Projects.createRoute(driverId))
                }
            )
        }

        composable(
            route = Screen.Projects.route,
            arguments = listOf(navArgument("driverId") { type = NavType.StringType })
        ) { backStackEntry ->
            val driverId = backStackEntry.arguments?.getString("driverId") ?: return@composable
            ProjectsScreen(
                driverId = driverId,
                onBack = { navController.popBackStack() },
                onProjectClick = { projectPath ->
                    navController.navigate(Screen.Sessions.createRoute(driverId, projectPath))
                },
                onTerminalsClick = {
                    navController.navigate(Screen.Terminals.createRoute(driverId))
                }
            )
        }

        composable(
            route = Screen.Sessions.route,
            arguments = listOf(
                navArgument("driverId") { type = NavType.StringType },
                navArgument("projectPath") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val driverId = backStackEntry.arguments?.getString("driverId") ?: return@composable
            val projectPath = backStackEntry.arguments?.getString("projectPath")?.let {
                URLDecoder.decode(it, "UTF-8")
            } ?: return@composable

            SessionsScreen(
                driverId = driverId,
                projectPath = projectPath,
                onBack = { navController.popBackStack() },
                onOpenTerminal = { terminalId ->
                    navController.navigate(Screen.Terminal.createRoute(driverId, terminalId))
                }
            )
        }

        composable(
            route = Screen.Terminals.route,
            arguments = listOf(navArgument("driverId") { type = NavType.StringType })
        ) { backStackEntry ->
            val driverId = backStackEntry.arguments?.getString("driverId") ?: return@composable
            TerminalsScreen(
                driverId = driverId,
                onBack = { navController.popBackStack() },
                onTerminalClick = { terminalId ->
                    navController.navigate(Screen.Terminal.createRoute(driverId, terminalId))
                }
            )
        }

        composable(
            route = Screen.Terminal.route,
            arguments = listOf(
                navArgument("driverId") { type = NavType.StringType },
                navArgument("terminalId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val driverId = backStackEntry.arguments?.getString("driverId") ?: return@composable
            val terminalId = backStackEntry.arguments?.getString("terminalId") ?: return@composable
            TerminalScreen(
                driverId = driverId,
                terminalId = terminalId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
