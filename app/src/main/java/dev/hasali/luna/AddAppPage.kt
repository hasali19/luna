package dev.hasali.luna

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.hasali.luna.ui.theme.LunaTheme
import dev.hasali.luna.ui.theme.Typography

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAppPage() {
    Scaffold(topBar = { TopAppBar(title = { Text("Add App") }) }) { padding ->
        Surface {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp),
            ) {
                var username by remember { mutableStateOf("") }
                var repository by remember { mutableStateOf("") }

                Column {
                    Row {
                        TextField(
                            modifier = Modifier.weight(1f),
                            value = username,
                            placeholder = { Text("Username") },
                            onValueChange = { username = it },
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        TextField(
                            modifier = Modifier.weight(1f),
                            value = repository,
                            placeholder = { Text("Repository") },
                            onValueChange = { repository = it },
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "https://github.com/${username.ifBlank { "<user>" }}/${repository.ifBlank { "<repo>" }}",
                        style = Typography.bodySmall,
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        onClick = { /*TODO*/ },
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
