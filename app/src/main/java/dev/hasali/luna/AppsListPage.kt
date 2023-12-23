package dev.hasali.luna

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsListPage(
    viewModel: AppsListViewModel,
    onAddApp: () -> Unit,
) {
    val packages by viewModel.packages.collectAsState(initial = null)
    val updatablePackages by viewModel.updatablePackages.collectAsState(initial = null)

    Scaffold(topBar = { TopAppBar(title = { Text("Apps") }) }, floatingActionButton = {
        FloatingActionButton(onClick = onAddApp) {
            Icon(Icons.Default.Add, contentDescription = null)
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
                        LazyColumn {
                            item {
                                LunaStatusCard(
                                    lunaPackage = viewModel.lunaPackage,
                                    onUpdate = viewModel::updateLuna,
                                )
                            }

                            if (packages.isEmpty()) {
                                item {
                                    Text(
                                        text = "No apps installed",
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            } else {
                                item {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp),
                                    ) {
                                        Text("Installed", modifier = Modifier.weight(1f))
                                        if (updatablePackages == null || updatablePackages!!.isEmpty()) {
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
                                                Text("Update all (${updatablePackages!!.size})")
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
private fun LunaStatusCard(lunaPackage: LunaPackageState, onUpdate: () -> Unit) {
    ElevatedCard(modifier = Modifier.padding(16.dp)) {
        val trailingContent: @Composable () -> Unit = if (lunaPackage.isUpdating) ({
            val updateProgress = lunaPackage.updateProgress.value
            if (updateProgress == null) {
                CircularProgressIndicator()
            } else {
                CircularProgressIndicator(
                    updateProgress
                )
            }
        }) else if (lunaPackage.isUpdateAvailable) ({
            TextButton(onClick = onUpdate) {
                Text("Update")
            }
        }) else ({
            Text("${lunaPackage.installedVersionName}-${lunaPackage.installedVersionCode}")
        })

        Column {
            ListItem(
                headlineContent = { Text(lunaPackage.name) },
                supportingContent = { Text(lunaPackage.packageName) },
                leadingContent = {
                    AsyncImage(
                        model = lunaPackage.icon,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp)
                    )
                },
                trailingContent = trailingContent,
            )
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
        val installedVersionCode = packageInfo.longVersionCodeCompat
        val latestVersionName = model.pkg.latestVersionName ?: "0.0.0"
        val latestVersionCode = model.pkg.latestVersionCode ?: -1
        if (latestVersionCode <= installedVersionCode) {
            Column(horizontalAlignment = Alignment.End) {
                Text("${packageInfo.versionName}-$installedVersionCode")
                Text(DateFormat.getDateInstance().format(Date(packageInfo.lastUpdateTime)))
            }
        } else {
            Text(
                text = "${packageInfo.versionName}-$installedVersionCode ~ $latestVersionName-$latestVersionCode",
                fontStyle = FontStyle.Italic,
                textDecoration = TextDecoration.Underline,
            )
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
