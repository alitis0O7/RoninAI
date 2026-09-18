package com.roninai.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.roninai.app.ui.screens.ChatScreen
import com.roninai.app.ui.screens.DiffViewer
import com.roninai.app.ui.screens.SettingsScreen
import com.roninai.app.ui.screens.TerminalInspector

object Routes {
    const val CHAT = "chat/{sessionId}"
    const val DIFF = "diff?old={oldContent}&new={newContent}&file={fileName}"
    const val TERMINAL = "terminal"
    const val SETTINGS = "settings"

    fun chat(sessionId: Long) = "chat/$sessionId"
    fun diff(oldContent: String, newContent: String, fileName: String = "diff") =
        "diff?old=$oldContent&new=$newContent&file=$fileName"
}

@Composable
fun RoninNavGraph(
    navController: NavHostController,
    chatScreenContent: @Composable (Long) -> Unit,
    diffScreenContent: @Composable (String, String, String) -> Unit,
    terminalScreenContent: @Composable () -> Unit,
    settingsScreenContent: @Composable () -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = Routes.chat(1L)
    ) {
        composable(
            route = Routes.CHAT,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: 1L
            chatScreenContent(sessionId)
        }

        composable(
            route = Routes.DIFF,
            arguments = listOf(
                navArgument("oldContent") { type = NavType.StringType; defaultValue = "" },
                navArgument("newContent") { type = NavType.StringType; defaultValue = "" },
                navArgument("fileName") { type = NavType.StringType; defaultValue = "diff" }
            )
        ) { backStackEntry ->
            val old = backStackEntry.arguments?.getString("oldContent") ?: ""
            val new = backStackEntry.arguments?.getString("newContent") ?: ""
            val file = backStackEntry.arguments?.getString("fileName") ?: "diff"
            diffScreenContent(old, new, file)
        }

        composable(route = Routes.TERMINAL) {
            terminalScreenContent()
        }

        composable(route = Routes.SETTINGS) {
            settingsScreenContent()
        }
    }
}
