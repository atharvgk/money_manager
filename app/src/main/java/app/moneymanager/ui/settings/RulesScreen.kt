package app.moneymanager.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.moneymanager.data.db.CategoryEntity
import app.moneymanager.data.db.RuleEntity
import app.moneymanager.data.db.RuleSource
import app.moneymanager.ui.common.CategoryBadge
import app.moneymanager.ui.common.CategoryPicker
import app.moneymanager.ui.common.LocalAppContainer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val repo = container.repository
    val rules by repo.rules.collectAsStateWithLifecycle(initialValue = emptyList())
    val categories by repo.categories.collectAsStateWithLifecycle(initialValue = emptyList())
    val catMap = categories.associateBy { it.key }
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<RuleEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    val filtered = rules.filter { query.isBlank() || it.keyword.contains(query.trim(), true) || (catMap[it.categoryKey]?.name?.contains(query.trim(), true) == true) }
    val (mine, builtIn) = filtered.partition { it.source == RuleSource.USER }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rules") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Restore built-in rules") }, onClick = {
                                menu = false
                                container.appScope.launch { repo.restoreDefaultRules() }
                            })
                            DropdownMenuItem(text = { Text("Re-apply rules to all transactions") }, onClick = {
                                menu = false
                                container.appScope.launch { repo.recategorizeAll() }
                            })
                        }
                    }
                },
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, "Add rule") } },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Text(
                    "If a merchant name or UPI ID contains the keyword, it gets that category. The longest matching keyword wins, " +
                        "and your rules always beat built-in ones. Categories you set by hand on a single transaction are never changed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search rules") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            item { SectionLabel("Your rules (${mine.size})") }
            if (mine.isEmpty()) {
                item {
                    Text(
                        "None yet. Change a transaction's category and tick \"Apply to all\", or tap +.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            items(mine, key = { it.id }) { RuleRow(it, catMap[it.categoryKey]) { editing = it } }
            item { SectionLabel("Built-in (${builtIn.size})") }
            items(builtIn, key = { it.id }) { RuleRow(it, catMap[it.categoryKey]) { editing = it } }
        }
    }

    if (adding || editing != null) {
        RuleDialog(
            rule = editing,
            categories = categories,
            onDismiss = { adding = false; editing = null },
            onSave = { keyword, cat ->
                val id = editing?.id
                container.appScope.launch { repo.saveRule(id, keyword, cat) }
                adding = false
                editing = null
            },
            onDelete = editing?.let { r -> { container.appScope.launch { repo.deleteRule(r.id) }; editing = null } },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
}

@Composable
private fun RuleRow(rule: RuleEntity, category: CategoryEntity?, onClick: () -> Unit) {
    ListItem(
        leadingContent = { CategoryBadge(category, 36.dp) },
        headlineContent = { Text(rule.keyword) },
        supportingContent = { Text(category?.name ?: rule.categoryKey) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun RuleDialog(
    rule: RuleEntity?,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var keyword by remember { mutableStateOf(rule?.keyword ?: "") }
    var category by remember { mutableStateOf(rule?.categoryKey ?: "food") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (rule == null) "New rule" else "Edit rule") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    label = { Text("Merchant or UPI ID contains") },
                    placeholder = { Text("e.g. swiggy, rahul.s@okaxis") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                CategoryPicker(categories, category, onSelect = { category = it })
            }
        },
        confirmButton = { TextButton(enabled = keyword.isNotBlank(), onClick = { onSave(keyword, category) }) { Text("Save") } },
        dismissButton = {
            if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            else TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
