package app.moneymanager.sms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.moneymanager.R
import app.moneymanager.core.categorize.Categories
import app.moneymanager.core.format.Inr
import app.moneymanager.data.Repository
import app.moneymanager.ui.MainActivity
import app.moneymanager.util.Permissions
import kotlinx.coroutines.flow.first

object Notifier {
    private const val CHANNEL = "transactions"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.channel_transactions), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = context.getString(R.string.channel_transactions_desc)
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    /** "₹450 at Swiggy → Food & Dining"; tapping it opens the transaction to recategorise. */
    suspend fun notifyTransaction(context: Context, repo: Repository, id: Long) {
        if (!Permissions.hasNotifications(context)) return
        val t = repo.observeTransaction(id).first() ?: return
        val categoryName = repo.categories.first().firstOrNull { it.key == t.categoryKey }?.name ?: Categories.get(t.categoryKey).name
        val amount = Inr.format(t.amountPaise)
        val who = t.merchant ?: t.vpa
        val title = when {
            t.type == "CREDIT" && who != null -> "$amount from $who → $categoryName"
            t.type == "CREDIT" -> "$amount received → $categoryName"
            who != null -> "$amount at $who → $categoryName"
            else -> "$amount spent → $categoryName"
        }
        val tap = PendingIntent.getActivity(
            context,
            id.toInt(),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_TXN_ID, id)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_rupee)
            .setContentTitle(title)
            .setContentText(if (t.excluded) "Excluded from spending · tap to review" else "Tap to change the category")
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id.toInt(), n)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }
}
