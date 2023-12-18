package dev.hasali.luna

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.hasali.luna.data.LunaDatabase
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAppPage(client: HttpClient, db: LunaDatabase) {
    val context = LocalContext.current

    val scope = rememberCoroutineScope()
    val onBackPressedDispatcherOwner = LocalOnBackPressedDispatcherOwner.current

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Install from repo") }, navigationIcon = {
                IconButton(onClick = { onBackPressedDispatcherOwner?.onBackPressedDispatcher?.onBackPressed() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null)
                }
            })
        },
    ) { padding ->
        Surface {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp),
            ) {
                var manifestUrl by remember { mutableStateOf("https://drive.google.com/uc?export=download&id=1JWg1vM9D0KZbILv3-YDUeHSs_-3kWxyH") }
                var manifest: AppManifest? by remember { mutableStateOf(null) }

                Column {
                    TextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = manifestUrl,
                        placeholder = { Text("Url") },
                        onValueChange = { manifestUrl = it },
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        onClick = {
                            scope.launch {
                                val res = client.get(manifestUrl)
                                if (res.status.isSuccess()) {
                                    manifest = res.body<AppManifest>()
                                } else {
                                    Toast.makeText(context, "App not found", Toast.LENGTH_SHORT)
                                        .show()
                                }
                            }
                        },
                    ) {
                        Row {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text("Search")
                        }
                    }

                    manifest?.let { manifest ->
                        var loading by remember { mutableStateOf(false) }
                        var progress: Float? by remember {
                            mutableStateOf(
                                null
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                        AppDetailsCard(
                            manifest = manifest,
                            loading = loading,
                            progress = progress,
                            onInstall = {
                                loading = true
                                scope.launch {
                                    db.packageDao().insert(
                                        dev.hasali.luna.data.Package(
                                            label = manifest.info.name,
                                            packageName = manifest.info.packageName,
                                            manifestUrl = manifestUrl,
                                        )
                                    )

                                    val result = AppInstaller(context).install(manifest) {
                                        progress = it
                                    }

                                    if (result is AppInstaller.InstallationResult.NoCompatiblePackage) {
                                        Toast.makeText(
                                            context,
                                            "No compatible package found",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }

                                    loading = false
                                    progress = null
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppDetailsCard(
    manifest: AppManifest,
    loading: Boolean,
    progress: Float?,
    onInstall: () -> Unit
) {
    val context = LocalContext.current

    val avatar = remember {
        ImageRequest.Builder(context)
            .data(manifest.info.icon)
            .crossfade(true)
            .build()
    }

    ElevatedCard {
        ListItem(
            headlineContent = { Text(manifest.info.name) },
            supportingContent = { Text(manifest.info.author) },
            leadingContent = {
                AsyncImage(
                    model = avatar,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                )
            },
            trailingContent = {
                OutlinedIconButton(enabled = !loading, onClick = onInstall) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_download),
                        contentDescription = null,
                    )

                    if (loading) {
                        if (progress == null) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                        } else {
                            CircularProgressIndicator(progress, strokeWidth = 2.dp)
                        }
                    }
                }
            },
            modifier = Modifier.clickable(enabled = manifest.info.url != null) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(manifest.info.url))
                context.startActivity(intent)
            },
        )
    }
}
