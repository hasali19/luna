package dev.hasali.luna

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.hasali.luna.data.LunaDatabase
import dev.hasali.luna.ui.theme.LunaTheme
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.observer.ResponseObserver
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.serialization.kotlinx.serialization
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import logcat.logcat
import java.time.Duration

@OptIn(ExperimentalSerializationApi::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissions()

        WorkManager.getInstance(applicationContext)
            .enqueueUniquePeriodicWork(
                "background_updates_check",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<BackgroundUpdatesWorker>(Duration.ofHours(1))
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.UNMETERED)
                            .setRequiresBatteryNotLow(true)
                            .build()
                    )
                    .build()
            )

        val client = HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
                })

                serialization(ContentType.Application.OctetStream, Json {
                    ignoreUnknownKeys = true
                    explicitNulls = false
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

    private fun requestNotificationPermissions() {
        val requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (!isGranted) {
                    Toast.makeText(
                        this,
                        "Notifications will not be received when apps are updated",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @Composable
    private fun App(client: HttpClient, db: LunaDatabase) {
        val navController = rememberNavController()

        NavHost(navController = navController, startDestination = "apps_list") {
            composable("apps_list") {
                AppsListPage(
                    viewModel = viewModel {
                        AppsListViewModel(
                            application = application,
                            client = client,
                            db = db,
                        )
                    },
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
}
