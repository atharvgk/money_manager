package app.moneymanager.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import app.moneymanager.MoneyManagerApp
import app.moneymanager.core.model.SmsMessage
import kotlinx.coroutines.launch

/**
 * Debug builds, emulator only: runs a message with any date through the same pipeline as an inbox scan.
 * Used by tools/send-sms.ps1 to create months of history (the emulator can only deliver SMS dated "now").
 *
 *   adb shell am broadcast -f 32 -n app.moneymanager/.debug.FakeSmsReceiver \
 *       --es from VM-HDFCBK --es body 'Rs.450.00 debited ...' --el time 1759650000000
 */
class FakeSmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!isEmulator()) return
        val from = intent.getStringExtra("from") ?: return
        val body = intent.getStringExtra("body")?.replace("\\n", "\n") ?: return
        val time = intent.getLongExtra("time", System.currentTimeMillis())
        val app = context.applicationContext as MoneyManagerApp
        val pending = goAsync()
        app.container.appScope.launch {
            try {
                app.container.ingestor.ingest(listOf(SmsMessage(from, body, time)))
            } finally {
                pending.finish()
            }
        }
    }

    private fun isEmulator() =
        Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish") || Build.FINGERPRINT.contains("generic")
}
