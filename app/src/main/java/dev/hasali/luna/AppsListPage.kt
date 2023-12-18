package dev.hasali.luna

import android.content.pm.PackageManager.NameNotFoundException
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.hasali.luna.data.LunaDatabase
import dev.hasali.luna.data.Package

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsListPage(db: LunaDatabase, onSearchApps: () -> Unit) {
    val packages by remember { db.packageDao().getAll() }
        .collectAsState(initial = null)

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
