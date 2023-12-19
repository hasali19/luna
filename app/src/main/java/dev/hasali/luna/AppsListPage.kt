package dev.hasali.luna

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsListPage(
    viewModel: AppsListViewModel,
    onSearchApps: () -> Unit,
) {
    val packages by viewModel.packages.collectAsState(initial = null)
    val availableUpdates by viewModel.updatedManifests.collectAsState(initial = null)

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
                                            Button(
                                                enabled = !viewModel.isCheckingForUpdates,
                                                onClick = viewModel::checkForUpdates,
                                            ) {
                                                Text("Check for updates")
                                                if (viewModel.isCheckingForUpdates) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(16.dp),
                                                        color = LocalContentColor.current,
                                                        strokeWidth = 2.dp,
                                                    )
                                                }
                                            }
                                        } else {
                                            Button(
                                                enabled = !viewModel.isUpdating,
                                                onClick = viewModel::updateAll,
                                            ) {
                                                Text("Update all (${availableUpdates!!.size})")
                                                if (viewModel.isUpdating) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(16.dp),
                                                        color = LocalContentColor.current,
                                                        strokeWidth = 2.dp,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                items(packages) {
                                    AppsListItem(
                                        model = it,
                                        onInstall = { viewModel.install(it) },
                                    )
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
private fun AppsListItem(model: PackageModel, onInstall: () -> Unit) {
    val packageInfo = model.info

    val overlineContent: (@Composable () -> Unit)? = if (packageInfo == null) ({
        Text("Not installed")
    }) else {
        null
    }

    val leadingContent: (@Composable () -> Unit)? = if (model.icon != null) ({
        AsyncImage(
            model = model.icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp)
        )
    }) else {
        null
    }

    val trailingContent: @Composable () -> Unit = if (packageInfo != null) ({
        val installedVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }

        model.manifest.let { manifest ->
            if (manifest == null || manifest.info.versionCode <= installedVersionCode) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("${packageInfo.versionName}-${installedVersionCode}")
                    Text(DateFormat.getDateInstance().format(Date(packageInfo.lastUpdateTime)))
                }
            } else {
                Text(
                    text = "${manifest.info.version}-${manifest.info.versionCode}",
                    fontStyle = FontStyle.Italic,
                    textDecoration = TextDecoration.Underline,
                )
            }
        }

    }) else ({
        TextButton(onClick = onInstall) {
            Text("Install")
        }
    })

    ListItem(
        headlineContent = { Text(model.pkg.label) },
        supportingContent = { Text(model.pkg.packageName) },
        overlineContent = overlineContent,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
    )
}
