package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.screens.LanguageSelectionScreen
import com.example.ui.screens.LanguageWorkspaceScreen
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.ToolchainScreen
import com.example.ui.theme.MyApplicationTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.EditorViewModel
import com.example.ui.screens.HomeViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val editorViewModel: EditorViewModel = viewModel()
                    val homeViewModel: HomeViewModel = viewModel()

                    NavHost(navController = navController, startDestination = "splash") {
                        composable("splash") {
                            com.example.ui.screens.SplashScreen(
                                onNavigateToHome = {
                                    navController.navigate("home") {
                                        popUpTo("splash") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("home") {
                            LanguageSelectionScreen(navController = navController)
                        }
                        composable(
                            route = "workspace/{languageId}",
                            arguments = listOf(navArgument("languageId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val languageId = backStackEntry.arguments?.getString("languageId") ?: ""
                            LanguageWorkspaceScreen(
                                navController = navController,
                                languageId = languageId,
                                viewModel = homeViewModel
                            )
                        }
                        composable(
                            route = "new_project/{languageId}",
                            arguments = listOf(navArgument("languageId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val languageId = backStackEntry.arguments?.getString("languageId") ?: ""
                            com.example.ui.screens.NewProjectScreen(
                                navController = navController,
                                languageId = languageId,
                                viewModel = homeViewModel
                            )
                        }
                        composable("toolchains") {
                            ToolchainScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable(
                            route = "editor/{projectId}",
                            arguments = listOf(navArgument("projectId") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val projectId = backStackEntry.arguments?.getLong("projectId") ?: 0L
                            EditorScreen(
                                projectId = projectId,
                                onNavigateBack = { navController.popBackStack() },
                                viewModel = editorViewModel
                            )
                        }
                    }
                }
            }
        }
    }
}
