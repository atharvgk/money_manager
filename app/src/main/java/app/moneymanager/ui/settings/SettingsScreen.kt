package app.moneymanager.ui.settings

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import app.moneymanager.BuildConfig
import app.moneymanager.core.format.Inr
import app.moneymanager.data.ThemeMode
import app.moneymanager.sms.InboxSyncWorker
import app.moneymanager.ui.common.AppCard
import app.moneymanager.ui.common.BudgetDialog
import app.moneymanager.ui.common.LocalAppContainer
import app.moneymanager.ui.common.ScreenHeader
import app.moneymanager.ui.lock.Biometrics
import app.moneymanager.ui.onboarding.RestrictedSettingsHelp
import app.moneymanager.util.Permissions
import java.time.LocalDate
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onOpenRules: () -> Unit, onOpenUnparsed: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by container.settings.prefs.collectAsStateWithLifecycle(initialValue = null)
    val unparsedCount by container.repository.unparsedCount.collectAsStateWithLifecycle(initialValue = 0)
    val scan by InboxSyncWorker.observeFullScan(context).collectAsStateWithLifecycle(initialValue = null)
    var hasSms by remember { mutableStateOf(Permissions.hasSms(context)) }
    var smsDenied by remember { mutableStateOf(false) }
    var hasNotif by remember { mutableStateOf(Permissions.hasNotifications(context)) }
    var battery by remember { mutableStateOf(Permissions.isIgnoringBatteryOptimizations(context)) }
    var confirmImport by remember { mutableStateOf<android.net.Uri?>(null) }
    var editBudget by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasSms = Permissions.hasSms(context)
        hasNotif = Permissions.hasNotifications(context)
        battery = Permissions.isIgnoringBatteryOptimizations(context)
    }
    val p = prefs ?: return

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasSms = Permissions.hasSms(context)
        smsDenied = !hasSms
        if (hasSms) InboxSyncWorker.runFullScan(context)
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasNotif = it }
    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { context.contentResolver.openOutputStream(uri)!!.use { container.backup.exportJson(it) } }
                .onSuccess { toast("Backup saved") }.onFailure { toast("Export failed: ${it.message}") }
        }
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching { context.contentResolver.openOutputStream(uri)!!.use { container.backup.exportCsv(it) } }
                .onSuccess { toast("CSV saved") }.onFailure { toast("Export failed: ${it.message}") }
        }
    }
    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> confirmImport = uri }

    LazyColumn(
        Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("Settings") }

        item {
            Group("SMS & data") {
                val running = scan?.state?.isFinished == false
                val done = scan?.progress?.getInt(InboxSyncWorker.PROGRESS_DONE, 0) ?: 0
                val total = scan?.progress?.getInt(InboxSyncWorker.PROGRESS_TOTAL, 0) ?: 0
                val lastAdded = scan?.takeIf { it.state == WorkInfo.State.SUCCEEDED }?.outputData?.getInt(InboxSyncWorker.PROGRESS_ADDED, -1) ?: -1
                Item(
                    title = "Rescan inbox",
                    subtitle = when {
                        !hasSms -> "Needs SMS access"
                        running && total > 0 -> "Scanning $done of $total messages…"
                        running -> "Starting…"
                        scan?.state == WorkInfo.State.FAILED -> "The last scan failed. Tap to try again."
                        lastAdded >= 0 -> "Last scan added $lastAdded new transaction${if (lastAdded == 1) "" else "s"}. Already-imported messages are skipped."
                        else -> "Re-reads all SMS and imports anything missing. Already-imported messages are skipped."
                    },
                    enabled = hasSms && !running,
                    onClick = { InboxSyncWorker.runFullScan(context) },
                )
                if (running) {
                    LinearProgressIndicator(
                        progress = { if (total > 0) done.toFloat() / total else 0f },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                }
                Item(
                    title = "Unparsed messages",
                    subtitle = "Bank-looking SMS the app couldn't read. Copy them to get support added.",
                    onClick = onOpenUnparsed,
                    trailing = { if (unparsedCount > 0) Badge { Text("$unparsedCount") } },
                )
                Item("Categorisation rules", "Merchant keyword → category. Your rules win over built-in ones.", onClick = onOpenRules)
            }
        }

        item {
            Group("Budget") {
                Item(
                    title = "Monthly budget",
                    subtitle = if (p.monthlyBudget > 0) "${Inr.formatRounded(p.monthlyBudget)} a month, across all accounts and cards" else "Not set. Home shows what's left and a daily allowance once it is.",
                    onClick = { editBudget = true },
                )
            }
        }

        item {
            Group("Backup") {
                Item("Export everything (JSON)", "Full backup to restore on a new phone", onClick = { exportJson.launch("money-manager-backup-${LocalDate.now()}.json") })
                Item("Import backup (JSON)", "Replaces all data on this phone with the backup", onClick = { importJson.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) })
                Item("Export transactions (CSV)", "For Excel or Google Sheets", onClick = { exportCsv.launch("money-manager-${LocalDate.now()}.csv") })
            }
        }

        item {
            Group("Security") {
                val available = Biometrics.available(context)
                Item(
                    title = "App lock",
                    subtitle = if (available) "Ask for fingerprint, face or phone PIN when opening the app" else "Set up a screen lock on your phone first",
                    enabled = available,
                    onClick = null,
                    trailing = {
                        Switch(
                            checked = p.appLock,
                            enabled = available,
                            onCheckedChange = { on ->
                                val activity = context.findActivity() ?: return@Switch
                                // Confirm the user can actually unlock before turning the lock on (or off).
                                Biometrics.authenticate(activity, if (on) "Turn on app lock" else "Turn off app lock", onSuccess = {
                                    scope.launch {
                                        container.settings.setAppLock(on)
                                        container.lock.unlock()
                                    }
                                }, onError = { toast(it) })
                            },
                        )
                    },
                )
            }
        }

        item {
            Group("Appearance") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("Theme", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ThemeMode.entries.forEachIndexed { i, mode ->
                            SegmentedButton(
                                selected = p.theme == mode,
                                onClick = { scope.launch { container.settings.setTheme(mode) } },
                                shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                            ) { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        }
                    }
                }
            }
        }

        item {
            Group("Permissions & background") {
                Item(
                    title = "SMS access",
                    subtitle = if (hasSms) "Allowed" else "Off: bank SMS aren't being recorded",
                    onClick = if (hasSms) null else ({ smsLauncher.launch(Permissions.SMS) }),
                    trailing = { if (!hasSms) TextButton(onClick = { smsLauncher.launch(Permissions.SMS) }) { Text("Allow") } },
                )
                if (!hasSms && smsDenied) {
                    Column(Modifier.padding(horizontal = 16.dp)) { RestrictedSettingsHelp(context) }
                }
                Item(
                    title = "Notify me about new transactions",
                    subtitle = if (hasNotif) "\"₹450 at Swiggy → Food & Dining\"" else "Notifications are blocked for this app",
                    onClick = null,
                    trailing = {
                        Switch(checked = p.notifyOnTransaction && hasNotif, onCheckedChange = { on ->
                            if (on && !hasNotif && Build.VERSION.SDK_INT >= 33) notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            scope.launch { container.settings.setNotify(on) }
                        })
                    },
                )
                Item(
                    title = "Battery optimisation",
                    subtitle = if (battery) "Unrestricted: background sync is reliable" else "Restricted: new SMS may be recorded late",
                    onClick = if (battery) null else ({ context.startActivity(Permissions.batteryOptimizationIntent(context)) }),
                    trailing = { if (!battery) TextButton(onClick = { context.startActivity(Permissions.batteryOptimizationIntent(context)) }) { Text("Allow") } },
                )
            }
        }

        item {
            Group("About") {
                Item(
                    "Money Manager ${BuildConfig.VERSION_NAME}",
                    "Built ${BuildConfig.BUILD_DATE}. No internet permission: your data stays on this phone.",
                    onClick = null,
                )
            }
        }
    }

    if (editBudget) BudgetDialog(currentPaise = p.monthlyBudget, onDismiss = { editBudget = false })

    confirmImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmImport = null },
            title = { Text("Replace all data?") },
            text = { Text("Everything on this phone will be replaced by the backup. Export first if you're unsure.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmImport = null
                    scope.launch {
                        runCatching { context.contentResolver.openInputStream(uri)!!.use { container.backup.importJson(it) } }
                            .onSuccess { toast("Restored ${it.transactions} transactions and ${it.rules} rules") }
                            .onFailure { toast("Import failed: ${it.message}") }
                    }
                }) { Text("Replace") }
            },
            dismissButton = { TextButton(onClick = { confirmImport = null }) { Text("Cancel") } },
        )
    }
}

/** A titled group of settings on one card. */
@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 6.dp),
        )
        AppCard(padding = PaddingValues(vertical = 4.dp), content = content)
    }
}

@Composable
private fun Item(
    title: String,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)?,
    trailing: (@Composable () -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier,
    )
}

fun Context.findActivity(): FragmentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is FragmentActivity) return c
        c = c.baseContext
    }
    return null
}
