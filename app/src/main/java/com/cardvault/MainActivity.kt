package com.cardvault

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cardvault.ui.CardDetailScreen
import com.cardvault.ui.CardVaultTheme
import com.cardvault.ui.EditCardScreen
import com.cardvault.ui.InventoryScreen
import com.cardvault.ui.ScanScreen
import com.cardvault.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CardVaultTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val nav = rememberNavController()
                    NavHost(navController = nav, startDestination = "inventory") {
                        composable("inventory") {
                            InventoryScreen(
                                onScan = { nav.navigate("scan") },
                                onOpen = { id -> nav.navigate("card/$id") },
                                onSettings = { nav.navigate("settings") },
                                onAddManually = { nav.navigate("edit/0") },
                            )
                        }
                        composable("scan") {
                            ScanScreen(
                                onBack = { nav.popBackStack() },
                                onScanned = {
                                    nav.navigate("edit/0") { popUpTo("scan") { inclusive = true } }
                                },
                            )
                        }
                        composable(
                            "edit/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.LongType }),
                        ) { entry ->
                            val id = entry.arguments?.getLong("id") ?: 0L
                            EditCardScreen(
                                cardId = id,
                                onBack = { nav.popBackStack() },
                                onSaved = { savedId ->
                                    if (id == 0L) {
                                        nav.navigate("card/$savedId") { popUpTo("inventory") }
                                    } else {
                                        nav.popBackStack()
                                    }
                                },
                            )
                        }
                        composable(
                            "card/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.LongType }),
                        ) { entry ->
                            val id = entry.arguments?.getLong("id") ?: 0L
                            CardDetailScreen(
                                cardId = id,
                                onBack = { nav.popBackStack() },
                                onEdit = { nav.navigate("edit/$id") },
                            )
                        }
                        composable("settings") {
                            SettingsScreen(onBack = { nav.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}
