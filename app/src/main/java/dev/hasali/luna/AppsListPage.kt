package dev.hasali.luna

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.hasali.luna.ui.theme.LunaTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsListPage(onAddApp: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Luna") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddApp) {
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }
    ) { padding ->
        Surface {
            Box(modifier = Modifier.padding(padding))
        }
    }
}

@Preview
@Composable
private fun AppsListPagePreview() {
    LunaTheme(darkTheme = true) {
        AppsListPage(onAddApp = {})
    }
}
