package app.moneymanager.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.moneymanager.core.format.Amounts
import kotlinx.coroutines.launch

/** Set, change or remove the monthly budget (one budget for all accounts and cards). */
@Composable
fun BudgetDialog(currentPaise: Long, onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    var text by remember { mutableStateOf(if (currentPaise > 0) (currentPaise / 100).toString() else "") }
    val paise = Amounts.parseToPaise(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Monthly budget") },
        text = {
            Column {
                Text(
                    "How much do you want to spend in a month, across all accounts and cards? " +
                        "Home then shows what's left and how much you can spend per day.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() || it == '.' || it == ',' } },
                    prefix = { Text("₹ ") },
                    singleLine = true,
                    isError = text.isNotEmpty() && paise == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = paise != null && paise > 0, onClick = {
                container.appScope.launch { container.settings.setMonthlyBudget(paise ?: 0) }
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            if (currentPaise > 0) {
                TextButton(onClick = {
                    container.appScope.launch { container.settings.setMonthlyBudget(0) }
                    onDismiss()
                }) { Text("Remove budget") }
            } else {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
