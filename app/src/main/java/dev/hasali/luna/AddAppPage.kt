package dev.hasali.luna

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.hasali.luna.ui.theme.LunaTheme
import dev.hasali.luna.ui.theme.Typography
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.observer.ResponseObserver
import io.ktor.client.request.get
import io.ktor.client.statement.request
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import logcat.logcat

@Serializable
data class GitHubRepo(
    val name: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("html_url") val htmlUrl: String,
    val owner: Owner,
) {
    @Serializable
    data class Owner(val login: String, @SerialName("avatar_url") val avatarUrl: String)
}

@Serializable
data class GitHubRepoRelease(
    val id: Int,
    @SerialName("created_at") val createdAt: String,
    val assets: List<GitHubRepoReleaseAsset>,
)

@Serializable
data class GitHubRepoReleaseAsset(
    val name: String,
    val size: Long,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAppPage() {
    val client = remember {
        HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                })
            }

            ResponseObserver {
                logcat { "method=${it.request.method}, url=${it.request.url}, status=${it.status}" }
            }

            defaultRequest {
                url("https://api.github.com")
            }
        }
    }

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
                var owner by remember { mutableStateOf("") }
                var repo by remember { mutableStateOf("") }
                var repoData: GitHubRepo? by remember { mutableStateOf(null) }

                val formattedOwner = owner.trim().ifBlank { "<user>" }
                val formattedRepo = repo.trim().ifBlank { "<repo>" }

                Column {
                    Row {
                        TextField(
                            modifier = Modifier.weight(1f),
                            value = owner,
                            placeholder = { Text("Owner") },
                            onValueChange = { owner = it },
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        TextField(
                            modifier = Modifier.weight(1f),
                            value = repo,
                            placeholder = { Text("Repo") },
                            onValueChange = { repo = it },
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "https://github.com/$formattedOwner/$formattedRepo",
                        style = Typography.bodySmall,
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    val context = LocalContext.current

                    Button(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        onClick = {
                            scope.launch {
                                val res = client.get("/repos/${owner.trim()}/${repo.trim()}")
                                if (res.status.isSuccess()) {
                                    repoData = res.body<GitHubRepo>()
                                } else {
                                    Toast.makeText(context, "Repo not found", Toast.LENGTH_SHORT)
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

                    repoData?.let { repoData ->
                        var loading by remember { mutableStateOf(false) }
                        var progress: Float? by remember {
                            mutableStateOf(
                                null
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                        RepoDetailsCard(
                            repo = repoData,
                            loading = loading,
                            progress = progress,
                            onInstall = {
                                loading = true
                                scope.launch {
                                    val res =
                                        client.get("/repos/${repoData.owner.login}/${repoData.name}/releases/latest")
                                    if (!res.status.isSuccess()) {
                                        Toast.makeText(
                                            context,
                                            "Failed to get latest release",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        loading = false
                                        return@launch
                                    }

                                    val latestRelease = res.body<GitHubRepoRelease>()
                                    val asset = latestRelease.assets
                                        .filter { it.name.endsWith(".apk") }
                                        .map { asset ->
                                            Pair(
                                                asset,
                                                Build.SUPPORTED_ABIS.indexOfFirst { abi ->
                                                    asset.name.contains(abi)
                                                }
                                            )
                                        }
                                        .filter { (_, rank) -> rank > -1 }
                                        .minByOrNull { (_, rank) -> rank }
                                        .let { it?.first }

                                    if (asset == null) {
                                        Toast.makeText(
                                            context,
                                            "No compatible release asset found",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        loading = false
                                        return@launch
                                    }

                                    AppInstaller(context).install(
                                        asset.name,
                                        asset.browserDownloadUrl,
                                        asset.size
                                    ) {
                                        progress = it
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

@Preview
@Composable
private fun AddAppPagePreview() {
    LunaTheme(darkTheme = true) {
        AddAppPage()
    }
}

@Composable
private fun RepoDetailsCard(
    repo: GitHubRepo,
    loading: Boolean,
    progress: Float?,
    onInstall: () -> Unit
) {
    val context = LocalContext.current

    val avatar = remember {
        ImageRequest.Builder(context)
            .data(repo.owner.avatarUrl)
            .crossfade(true)
            .build()
    }

    ElevatedCard {
        ListItem(
            headlineContent = { Text(repo.fullName) },
            supportingContent = { Text(repo.htmlUrl) },
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
            modifier = Modifier.clickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(repo.htmlUrl))
                context.startActivity(intent)
            },
        )
    }
}

@Preview
@Composable
private fun RepoDetailsCardPreview() {
    LunaTheme(darkTheme = true) {
        RepoDetailsCard(
            repo = GitHubRepo(
                name = "luna",
                fullName = "hasali19/luna",
                htmlUrl = "https://github.com/hasali19/luna",
                owner = GitHubRepo.Owner(
                    login = "hasali19",
                    avatarUrl = "https://avatars.githubusercontent.com/u/10169241?v=4",
                ),
            ),
            loading = true,
            progress = 0.3f,
            onInstall = {},
        )
    }
}
