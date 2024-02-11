package dev.hasali.luna

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.hasali.luna.data.LunaDatabase
import dev.hasali.luna.ui.theme.LunaTheme
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.observer.ResponseObserver
import io.ktor.client.request.get
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.serialization.kotlinx.serialization
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import logcat.logcat

@OptIn(ExperimentalSerializationApi::class)
class InAppUpdateActivity : ComponentActivity() {

    companion object {
        private const val UnknownPackageName = 1
        private const val PermissionDenied = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val packageName = intent.getStringExtra("packageName")
        if (packageName == null) {
            setResult(UnknownPackageName)
            finish()
            return
        }

        if (callingActivity?.packageName != packageName) {
            setResult(PermissionDenied)
            finish()
            return
        }

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
        val installer = AppInstaller(this)

        var isInstalling by mutableStateOf(false)
        var progress by mutableStateOf<AppInstaller.InstallationProgress?>(null)
        var isInstalled by mutableStateOf(false)

        setContent {
            val scope = rememberCoroutineScope()

            val pkg by remember { db.packageDao().getByPackageName(packageName) }
                .collectAsState(initial = null)

            val manifest by produceState<AppManifest?>(null, pkg) {
                if (pkg != null) {
                    val res = client.get(pkg!!.manifestUrl)
                    value = res.body<AppManifest>()
                }
            }

            LaunchedEffect(isInstalling) {
                setFinishOnTouchOutside(!isInstalling)
            }

            LunaTheme {
                Surface(
                    modifier = Modifier.widthIn(min = 280.dp, max = 560.dp),
                    shape = AlertDialogDefaults.shape,
                    color = AlertDialogDefaults.containerColor,
                    tonalElevation = AlertDialogDefaults.TonalElevation,
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text("Update", style = MaterialTheme.typography.headlineSmall)

                        Column(modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)) {
                            if (manifest == null) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            } else {
                                if (isInstalled) {
                                    Text("${manifest!!.info.name} was successfully updated")
                                } else if (isInstalling) {
                                    progress.let { progress ->
                                        when (progress) {
                                            is AppInstaller.InstallationProgress.Downloading -> {
                                                Text("Downloading...")
                                                Spacer(modifier = Modifier.height(8.dp))
                                                LinearProgressIndicator(
                                                    progress.value,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }

                                            AppInstaller.InstallationProgress.Installing, null -> {
                                                Text("Installing...")
                                                Spacer(modifier = Modifier.height(8.dp))
                                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                            }
                                        }
                                    }
                                } else {
                                    Text("An update is available for ${manifest!!.info.name}")
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "Update to ${manifest!!.info.version} (${manifest!!.info.versionCode})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val isActionsEnabled =
                                manifest != null && (!isInstalling || isInstalled)

                            TextButton(enabled = isActionsEnabled, onClick = { finish() }) {
                                Text("Cancel")
                            }

                            if (isInstalled) {
                                TextButton(
                                    enabled = isActionsEnabled,
                                    onClick = {
                                        startActivity(
                                            packageManager.getLaunchIntentForPackage(packageName)
                                        )
                                        finish()
                                    },
                                ) {
                                    Text("Open")
                                }
                            } else {
                                TextButton(
                                    enabled = isActionsEnabled,
                                    onClick = {
                                        isInstalling = true
                                        scope.launch {
                                            installer.install(manifest!!) {
                                                progress = it
                                            }
                                            isInstalled = true
                                        }
                                    },
                                ) {
                                    Text("Install")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
