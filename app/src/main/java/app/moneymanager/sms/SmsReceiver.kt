package app.moneymanager.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import app.moneymanager.MoneyManagerApp
import app.moneymanager.core.model.SmsMessage
import kotlinx.coroutines.launch

/** Records bank SMS the moment they arrive, then posts a small notification. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return
        val now = System.currentTimeMillis()
        // Long SMS arrive in parts; join them per sender, as the inbox does.
        val messages = parts.filterNotNull()
            .groupBy { it.displayOriginatingAddress ?: it.originatingAddress ?: "" }
            .map { (sender, list) ->
                SmsMessage(
                    sender = sender,
                    body = list.joinToString("") { it.displayMessageBody ?: it.messageBody ?: "" },
                    timestamp = SmsReader.chooseTimestamp(now, list.first().timestampMillis),
                )
            }
        val app = context.applicationContext as MoneyManagerApp
        val pending = goAsync()
        app.container.appScope.launch {
            try {
                val stats = app.container.ingestor.ingest(messages)
                if (app.container.settings.current().notifyOnTransaction) {
                    for (id in stats.newTransactionIds) Notifier.notifyTransaction(context, app.container.repository, id)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
