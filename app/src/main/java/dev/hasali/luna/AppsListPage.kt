package dev.hasali.luna

import android.content.pm.PackageManager.NameNotFoundException
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.hasali.luna.data.LunaDatabase
import dev.hasali.luna.data.Package
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.coroutines.launch
import logcat.logcat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsListPage(client: HttpClient, db: LunaDatabase, onSearchApps: () -> Unit) {
    val packages by remember { db.packageDao().getAll() }
        .collectAsState(initial = null)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var availableUpdates: List<AppManifest>? by remember { mutableStateOf(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("Apps") }) }, floatingActionButton = {
        FloatingActionButton(onClick = onSearchApps) {
            Icon(Icons.Default.Search, contentDescription = null)
        }
    }) { padding ->
        Surface {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            ) {
                packages.let { packages ->
                    if (packages == null) {
                        CircularProgressIndicator()
                    } else {
                        if (packages.isEmpty()) {
                            Text("No apps installed", modifier = Modifier.align(Alignment.Center))
                        } else {
                            LazyColumn {
                                item {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp),
                                    ) {
                                        Text("Installed", modifier = Modifier.weight(1f))
                                        if (availableUpdates == null || availableUpdates!!.isEmpty()) {
                                            Button(onClick = {
                                                scope.launch {
                                                    val updates = mutableListOf<AppManifest>()
                                                    for (pkg in packages) {
                                                        val packageInfo =
                                                            context.packageManager.getPackageInfo(
                                                                pkg.packageName,
                                                                0
                                                            )
                                                                ?: continue

                                                        logcat { "Retrieving manifest for ${pkg.packageName} from ${pkg.manifestUrl}" }

                                                        val res = client.get(pkg.manifestUrl)
                                                        val manifest = if (res.status.isSuccess()) {
                                                            res.body<AppManifest>()
                                                        } else {
                                                            Toast.makeText(
                                                                context,
                                                                "Failed to get manifest: ${pkg.label}",
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                            return@launch
                                                        }

                                                        val installedVersionCode =
                                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                                                packageInfo.longVersionCode
                                                            } else {
                                                                @Suppress("DEPRECATION")
                                                                packageInfo.versionCode.toLong()
                                                            }

                                                        if (manifest.info.versionCode > installedVersionCode) {
                                                            logcat { "Update available for ${pkg.packageName}, installed=$installedVersionCode, latest=${manifest.info.versionCode}" }
                                                            updates.add(manifest)
                                                        }
                                                    }
                                                    availableUpdates = updates
                                                }
                                            }) {
                                                Text("Check for updates")
                                            }
                                        } else {
                                            Button(onClick = {
                                                scope.launch {
                                                    for (manifest in availableUpdates!!) {
                                                        val result =
                                                            AppInstaller(context).install(manifest) {}

                                                        when (result) {
                                                            is AppInstaller.InstallationResult.Success -> {
                                                                Toast.makeText(
                                                                    context,
                                                                    "Package installed successfully: ${manifest.info.packageName}",
                                                                    Toast.LENGTH_SHORT
                                                                ).show()
                                                            }

                                                            is AppInstaller.InstallationResult.NoCompatiblePackage -> {
                                                                Toast.makeText(
                                                                    context,
                                                                    "No compatible package found",
                                                                    Toast.LENGTH_SHORT
                                                                ).show()
                                                            }

                                                            is AppInstaller.InstallationResult.UserCanceled -> {
                                                                Toast.makeText(
                                                                    context,
                                                                    "Installation was canceled",
                                                                    Toast.LENGTH_SHORT
                                                                ).show()
                                                            }

                                                            is AppInstaller.InstallationResult.Failure -> {
                                                                Toast.makeText(
                                                                    context,
                                                                    "Installation failed: ${result.message}",
                                                                    Toast.LENGTH_SHORT
                                                                ).show()
                                                            }
                                                        }
                                                    }
                                                }
                                            }) {
                                                Text("Update all (${availableUpdates!!.size})")
                                            }
                                        }
                                    }
                                }

                                items(packages) {
                                    AppsListItem(pkg = it)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppsListItem(pkg: Package) {
    val context = LocalContext.current
    val packageInfo = remember(pkg.packageName) {
        try {
            context.packageManager.getPackageInfo(pkg.packageName, 0)
        } catch (e: NameNotFoundException) {
            null
        }
    }

    val overlineContent: (@Composable () -> Unit)? = if (packageInfo == null) ({
        Text("Not installed")
    }) else {
        null
    }

    val leadingContent: (@Composable () -> Unit)? = if (packageInfo != null) ({
        AsyncImage(
            model = packageInfo.applicationInfo.loadIcon(context.packageManager),
            contentDescription = null,
            modifier = Modifier.size(48.dp)
        )
    }) else {
        null
    }

    ListItem(
        headlineContent = { Text(pkg.label) },
        supportingContent = { Text(pkg.packageName) },
        overlineContent = overlineContent,
        leadingContent = leadingContent,
    )
}
