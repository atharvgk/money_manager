package app.moneymanager.ui.onboarding

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import app.moneymanager.core.format.Inr
import app.moneymanager.sms.InboxSyncWorker
import app.moneymanager.ui.common.LocalAppContainer
import app.moneymanager.util.Permissions
import kotlinx.coroutines.launch

private enum class Step { WELCOME, SMS, NOTIFICATIONS, BATTERY, SCAN }

@Composable
fun OnboardingScreen() {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val steps = remember(context) {
        Step.entries.filter { it != Step.NOTIFICATIONS || Build.VERSION.SDK_INT >= 33 }
    }
    var index by rememberSaveable { mutableIntStateOf(0) }
    val step = steps[index]
    fun next() { if (index < steps.lastIndex) index++ }
    fun finish() = scope.launch { container.settings.setOnboardingDone(true) }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp).verticalScroll(rememberScrollState()),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            steps.indices.forEach { i ->
                Spacer(
                    Modifier.size(width = 28.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(
                        if (i <= index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                )
            }
        }
        Spacer(Modifier.size(32.dp))
        when (step) {
            Step.WELCOME -> Welcome(onNext = ::next)
            Step.SMS -> SmsStep(onNext = ::next)
            Step.NOTIFICATIONS -> NotificationsStep(onNext = ::next)
            Step.BATTERY -> BatteryStep(onNext = ::next)
            Step.SCAN -> ScanStep(onDone = { finish() })
        }
    }
}

@Composable
private fun Title(emoji: String, title: String, body: String) {
    Text(emoji, fontSize = 48.sp)
    Spacer(Modifier.size(16.dp))
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.size(12.dp))
    Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.size(28.dp))
}

@Composable
private fun Welcome(onNext: () -> Unit) {
    Title(
        "₹",
        "Your spending, from your bank SMS",
        "Money Manager reads the transaction messages your bank already sends you, sorts them into categories " +
            "and compares months.\n\nIt has no internet permission: nothing you see here ever leaves this phone.",
    )
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Get started") }
}

@Composable
private fun SmsStep(onNext: () -> Unit) {
    val context = LocalContext.current
    var granted by rememberSaveable { mutableStateOf(Permissions.hasSms(context)) }
    var denied by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        granted = result.values.all { it } && Permissions.hasSms(context)
        denied = !granted
    }
    // Coming back from Settings after allowing restricted settings / the permission.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = Permissions.hasSms(context) }
    LaunchedEffect(granted) { if (granted) onNext() }

    Title(
        "✉️",
        "Allow SMS access",
        "To record transactions automatically, the app needs to:\n\n" +
            "• Read SMS: to import the bank messages already in your inbox\n" +
            "• Receive SMS: to record new payments the moment they happen\n\n" +
            "Only bank and card messages are used. OTPs, promotions and personal chats are ignored and never stored.",
    )
    Button(onClick = { launcher.launch(Permissions.SMS) }, modifier = Modifier.fillMaxWidth()) { Text("Grant SMS access") }
    if (denied) {
        Spacer(Modifier.size(20.dp))
        RestrictedSettingsHelp(context)
    }
    Spacer(Modifier.size(8.dp))
    TextButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Skip for now (add expenses by hand)") }
}

/** Android 13+ blocks SMS permissions for apps installed outside the Play Store until you allow it. */
@Composable
fun RestrictedSettingsHelp(context: Context) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text("Permission blocked?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.size(6.dp))
            Text(
                "Android blocks SMS access for apps installed from a file. To allow it:\n\n" +
                    "1. Tap \"Open app settings\" below\n" +
                    "2. Tap ⋮ (top-right) → \"Allow restricted settings\" and confirm\n" +
                    "3. Open Permissions → SMS → Allow\n" +
                    "4. Come back here\n\n" +
                    "If you don't see \"Allow restricted settings\", first tap Grant once more so Android registers the request.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.size(12.dp))
            OutlinedButton(onClick = { context.startActivity(Permissions.appSettingsIntent(context)) }) {
                Text("Open app settings")
            }
        }
    }
}

@Composable
private fun NotificationsStep(onNext: () -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onNext() }
    LaunchedEffect(Unit) { if (Permissions.hasNotifications(context)) onNext() }
    Title(
        "🔔",
        "Get a note for each payment",
        "Like \"₹450 at Swiggy → Food & Dining\". Tap it to fix the category in one step.",
    )
    Button(
        onClick = { if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else onNext() },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Allow notifications") }
    TextButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Not now") }
}

@Composable
private fun BatteryStep(onNext: () -> Unit) {
    val context = LocalContext.current
    var ok by rememberSaveable { mutableStateOf(Permissions.isIgnoringBatteryOptimizations(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { ok = Permissions.isIgnoringBatteryOptimizations(context) }
    LaunchedEffect(ok) { if (ok) onNext() }
    Title(
        "🔋",
        "Let it run in the background",
        "Some phones stop background apps aggressively, which can delay new transactions. Allowing this keeps the SMS " +
            "listener and the 6-hourly sync reliable. It uses almost no battery: the app only wakes when an SMS arrives.",
    )
    Button(onClick = { context.startActivity(Permissions.batteryOptimizationIntent(context)) }, modifier = Modifier.fillMaxWidth()) {
        Text("Allow background activity")
    }
    TextButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Skip") }
}

@Composable
private fun ScanStep(onDone: () -> Unit) {
    val context = LocalContext.current
    val hasSms = Permissions.hasSms(context)
    val work by InboxSyncWorker.observeFullScan(context).collectAsStateWithLifecycle(initialValue = null)
    LaunchedEffect(hasSms) { if (hasSms) InboxSyncWorker.runFullScan(context) }

    if (!hasSms) {
        Title("📝", "You're set", "Without SMS access the app works as a manual expense tracker. You can grant access later in Settings.")
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Open the app") }
        return
    }
    val w = work
    val failed = w?.state == WorkInfo.State.FAILED || w?.state == WorkInfo.State.CANCELLED
    val finished = w?.state?.isFinished == true
    val done = w?.progress?.getInt(InboxSyncWorker.PROGRESS_DONE, 0) ?: 0
    val total = w?.progress?.getInt(InboxSyncWorker.PROGRESS_TOTAL, 0) ?: 0
    val added = if (finished) w.outputData.getInt(InboxSyncWorker.PROGRESS_ADDED, 0) else w?.progress?.getInt(InboxSyncWorker.PROGRESS_ADDED, 0) ?: 0
    if (failed) {
        Title(
            "⚠️",
            "The scan didn't finish",
            "Something went wrong while reading the inbox. Try again; if it keeps failing, continue and use Settings → Rescan inbox later.",
        )
        Button(onClick = { InboxSyncWorker.runFullScan(context) }, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Continue anyway") }
        return
    }
    val recorded by LocalAppContainer.current.repository.transactionCount.collectAsStateWithLifecycle(initialValue = 0)
    Title(
        if (finished) "✅" else "🔎",
        if (finished) "Inbox scanned" else "Reading your inbox…",
        if (finished) "$recorded transaction${if (recorded == 1) "" else "s"} recorded. New ones will appear automatically from now on."
        else "This happens once and usually takes under a minute. You can leave this screen; it keeps going in the background.",
    )
    if (!finished) {
        if (total > 0) {
            LinearProgressIndicator(progress = { done.toFloat() / total }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.size(8.dp))
            Text(
                "${Inr.groupIndian(done.toLong())} of ${Inr.groupIndian(total.toLong())} messages · $added transactions",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.size(24.dp))
        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Continue while it scans") }
    } else {
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("See my spending") }
    }
}
