package app.moneymanager.ui.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.moneymanager.data.db.UnparsedEntity
import app.moneymanager.ui.common.EmptyState
import app.moneymanager.ui.common.LocalAppContainer
import app.moneymanager.ui.common.fullDateTime
import app.moneymanager.ui.transactions.copy
import kotlinx.coroutines.launch

private fun format(list: List<UnparsedEntity>) = list.joinToString("\n\n") { "From: ${it.sender}\n${it.body}" }

private fun share(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, "Share unparsed SMS"))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnparsedScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val items by container.repository.unparsed.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Unparsed messages") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (items.isNotEmpty()) {
                        IconButton(onClick = { copy(context, format(items)) }) { Icon(Icons.Filled.ContentCopy, "Copy all") }
                        IconButton(onClick = { share(context, format(items)) }) { Icon(Icons.Filled.Share, "Share all") }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            item {
                Text(
                    "These look like bank messages but the app couldn't read them. Copy or share them (the format suits samples.txt), " +
                        "add support for them, then update the app and tap Settings → Rescan inbox. Dismiss anything that isn't a real transaction.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.padding(6.dp))
            }
            if (items.isEmpty()) item { EmptyState("🎉", "Nothing here", "Every bank SMS so far was understood.") }
            items(items, key = { it.id }) { u ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("${u.sender} · ${fullDateTime(u.timestamp)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.padding(3.dp))
                        SelectionContainer { Text(u.body, style = MaterialTheme.typography.bodyMedium) }
                        Text(u.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(top = 6.dp))
                        Row {
                            TextButton(onClick = { copy(context, "From: ${u.sender}\n${u.body}") }) { Text("Copy") }
                            TextButton(onClick = { share(context, "From: ${u.sender}\n${u.body}") }) { Text("Share") }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { container.appScope.launch { container.repository.dismissUnparsed(u.id) } }) { Text("Dismiss") }
                        }
                    }
                }
            }
        }
    }
}
