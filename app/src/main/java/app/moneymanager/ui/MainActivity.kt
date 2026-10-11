package app.moneymanager.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import app.moneymanager.MoneyManagerApp
import kotlinx.coroutines.flow.MutableStateFlow

/** FragmentActivity (not plain ComponentActivity) because the biometric prompt needs it. */
class MainActivity : FragmentActivity() {
    /** Transaction to open, e.g. after tapping a "₹450 at Swiggy" notification. */
    private val openTxn = MutableStateFlow<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handle(intent)
        val container = (application as MoneyManagerApp).container
        setContent {
            AppRoot(container = container, activity = this, openTxn = openTxn)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_TXN_ID, -1L) ?: -1L
        if (id > 0) openTxn.value = id
        intent?.removeExtra(EXTRA_TXN_ID)
    }

    companion object {
        const val EXTRA_TXN_ID = "txn_id"
    }
}
