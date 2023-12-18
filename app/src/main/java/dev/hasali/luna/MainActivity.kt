package dev.hasali.luna

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.hasali.luna.data.LunaDatabase
import dev.hasali.luna.ui.theme.LunaTheme
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.observer.ResponseObserver
import io.ktor.client.statement.request
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import logcat.logcat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val client = HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                })
            }

            ResponseObserver {
                logcat { "method=${it.request.method}, url=${it.request.url}, status=${it.status}" }
            }
        }

        val db = LunaDatabase.open(this)

        setContent {
            LunaTheme {
                Surface {
                    App(client, db)
                }
            }
        }
    }
}

@Composable
private fun App(client: HttpClient, db: LunaDatabase) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "apps_list") {
        composable("apps_list") {
            AppsListPage(
                client = client,
                db = db,
                onSearchApps = { navController.navigate("add_app") },
            )
        }

        composable(
            "add_app",
            enterTransition = {
                fadeIn() + slideIntoContainer(towards = AnimatedContentTransitionScope.SlideDirection.Start)
            },
            exitTransition = {
                fadeOut() + slideOutOfContainer(towards = AnimatedContentTransitionScope.SlideDirection.End)
            }
        ) {
            AddAppPage(client = client, db = db)
        }
    }
}
