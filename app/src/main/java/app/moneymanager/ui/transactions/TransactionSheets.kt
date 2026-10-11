package app.moneymanager.ui.transactions

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.moneymanager.core.categorize.Categories
import app.moneymanager.core.format.Amounts
import app.moneymanager.core.format.Inr
import app.moneymanager.core.model.TxnType
import app.moneymanager.ui.common.CategoryPicker
import app.moneymanager.ui.common.LocalAppContainer
import app.moneymanager.ui.common.fullDateTime
import app.moneymanager.ui.common.isCredit
import app.moneymanager.ui.common.zone
import app.moneymanager.ui.theme.LocalMoneyColors
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionSheet(id: Long, onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    val repo = container.repository
    val context = LocalContext.current
    val txn by remember(id) { repo.observeTransaction(id) }.collectAsStateWithLifecycle(initialValue = null)
    val categories by repo.categories.collectAsStateWithLifecycle(initialValue = emptyList())
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val t = txn
    var category by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var note by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var excluded by rememberSaveable(id) { mutableStateOf<Boolean?>(null) }
    var applyToAll by rememberSaveable(id) { mutableStateOf(true) }
    var showSms by rememberSaveable(id) { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(t?.id) {
        if (t != null) {
            if (category == null) category = t.categoryKey
            if (note == null) note = t.note.orEmpty()
            if (excluded == null) excluded = t.excluded
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        if (t == null) {
            Spacer(Modifier.height(200.dp))
            return@ModalBottomSheet
        }
        val merchantName = t.merchant ?: t.vpa
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
        ) {
            Text(
                (if (t.isCredit) "+" else "") + Inr.format(t.amountPaise),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = if (t.isCredit) LocalMoneyColors.current.income else MaterialTheme.colorScheme.onSurface,
            )
            Text(merchantName ?: if (t.isCredit) "Money received" else "Payment", style = MaterialTheme.typography.titleMedium)
            val details = listOfNotNull(
                fullDateTime(t.timestamp),
                t.bank,
                t.account?.let { "••$it" },
                t.channel?.let(::channelLabel),
                t.reference?.let { "Ref $it" },
            )
            Text(details.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (t.vpa != null && t.merchant != null) {
                Text(t.vpa, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(Modifier.height(20.dp))
            Text("Category", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            CategoryPicker(categories, category, onSelect = { category = it })

            val changed = category != null && category != t.categoryKey
            val key = t.merchantKey ?: t.vpa
            if (changed && key != null) {
                Row(
                    Modifier.fillMaxWidth().clickable { applyToAll = !applyToAll }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = applyToAll, onCheckedChange = { applyToAll = it })
                    Column {
                        Text("Apply to all from ${merchantName ?: key}", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Saves a rule and updates past transactions from this merchant",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = note.orEmpty(),
                onValueChange = { note = it },
                label = { Text("Note") },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Exclude from totals", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "For transfers between your own accounts, card bill payments, etc.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = excluded == true, onCheckedChange = { excluded = it })
            }

            if (t.rawText != null) {
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { showSms = !showSms }) { Text(if (showSms) "Hide original SMS" else "Show original SMS") }
                if (showSms) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(t.sender.orEmpty(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            SelectionContainer { Text(t.rawText, style = MaterialTheme.typography.bodySmall) }
                            TextButton(onClick = { copy(context, "${t.sender}\n${t.rawText}") }) { Text("Copy") }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Row {
                OutlinedButton(onClick = { confirmDelete = true }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.weight(1f))
                Button(onClick = {
                    val cat = category ?: t.categoryKey
                    val ex = excluded ?: t.excluded
                    val n = note
                    val apply = applyToAll && changed
                    container.appScope.launch { repo.updateTransaction(t.id, cat, n, ex, apply) }
                    onDismiss()
                }) { Text("Save") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this transaction?") },
            text = { Text("It won't come back on a rescan. If it's a real payment you just don't want counted, use \"Exclude from totals\" instead.") },
            confirmButton = {
                TextButton(onClick = {
                    container.appScope.launch { repo.delete(id) }
                    confirmDelete = false
                    onDismiss()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

fun channelLabel(channel: String): String? = when (channel) {
    "UPI", "NEFT", "IMPS", "RTGS", "ACH", "ATM" -> channel
    "CARD" -> "Card"
    "NETBANKING" -> "Net banking"
    "WALLET" -> "Wallet"
    "CHEQUE" -> "Cheque"
    else -> null
}

fun copy(context: Context, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java)
    cm.setPrimaryClip(ClipData.newPlainText("SMS", text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionSheet(onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    val categories by container.repository.categories.collectAsStateWithLifecycle(initialValue = emptyList())
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var income by rememberSaveable { mutableStateOf(false) }
    var amount by rememberSaveable { mutableStateOf("") }
    var merchant by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(Categories.FOOD) }
    var date by rememberSaveable { mutableStateOf(LocalDate.now(zone).toEpochDay()) }
    var pickDate by remember { mutableStateOf(false) }
    val paise = Amounts.parseToPaise(amount)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text("Add cash transaction", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = !income, onClick = { income = false; category = Categories.FOOD }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Expense") }
                SegmentedButton(selected = income, onClick = { income = true; category = Categories.INCOME }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Income") }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = amount,
                onValueChange = { v -> amount = v.filter { it.isDigit() || it == '.' || it == ',' } },
                label = { Text("Amount") },
                prefix = { Text("₹ ") },
                singleLine = true,
                isError = amount.isNotEmpty() && paise == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it },
                label = { Text(if (income) "From" else "Paid to / what for") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { pickDate = true }) {
                Text("Date: " + LocalDate.ofEpochDay(date).let { if (it == LocalDate.now(zone)) "Today" else it.toString() })
            }
            Spacer(Modifier.height(12.dp))
            Text("Category", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            CategoryPicker(categories, category, onSelect = { category = it })
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Row {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.weight(1f))
                Button(
                    enabled = paise != null && paise > 0,
                    onClick = {
                        val day = LocalDate.ofEpochDay(date)
                        val time = if (day == LocalDate.now(zone)) LocalTime.now(zone) else LocalTime.NOON
                        val ts = day.atTime(time).atZone(zone).toInstant().toEpochMilli()
                        val type = if (income) TxnType.CREDIT else TxnType.DEBIT
                        container.appScope.launch { container.repository.addManual(type, paise!!, ts, merchant, category, note) }
                        onDismiss()
                    },
                ) { Text("Save") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date * 24 * 3600 * 1000L)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = it / (24 * 3600 * 1000L) }
                    pickDate = false
                }) { Text("OK") }
            },
        ) { DatePicker(state) }
    }
}
