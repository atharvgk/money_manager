package app.moneymanager

import android.app.Application
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.moneymanager.data.BackupManager
import app.moneymanager.data.Repository
import app.moneymanager.data.SettingsStore
import app.moneymanager.data.db.AppDatabase
import app.moneymanager.sms.InboxSyncWorker
import app.moneymanager.sms.Notifier
import app.moneymanager.sms.SmsIngestor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MoneyManagerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifier.createChannels(this)
        InboxSyncWorker.schedulePeriodic(this)
        container.appScope.launch {
            container.repository.ensureSeeded()
            val settings = container.settings
            val from = settings.dataVersion()
            if (from < Repository.DATA_VERSION) runCatching { settings.setDataVersion(container.repository.upgradeData(from)) }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(container.lock)
    }
}

/** Hand-rolled dependency container: one database, one repository, shared by UI, receiver and workers. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = AppDatabase.build(context)
    val settings = SettingsStore(context)
    val repository = Repository(db)
    val ingestor = SmsIngestor(repository)
    val backup = BackupManager(db)
    val lock = AppLock(settings, appScope)
}

/** Locks the app on start and after 30 s in the background, when app lock is on. */
class AppLock(private val settings: SettingsStore, private val scope: CoroutineScope) : DefaultLifecycleObserver {
    val locked = MutableStateFlow(true)
    private var backgroundedAt = 0L
    private var started = false

    override fun onStart(owner: LifecycleOwner) {
        scope.launch {
            val enabled = settings.current().appLock
            val away = System.currentTimeMillis() - backgroundedAt
            if (!enabled) locked.value = false
            else if (!started || away > 30_000) locked.value = true
            started = true
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        backgroundedAt = System.currentTimeMillis()
    }

    fun unlock() {
        locked.value = false
    }
}
