package com.aamnnr.duitku

import android.app.Application
import com.aamnnr.duitku.data.local.AppDatabase
import com.aamnnr.duitku.data.remote.CloudService
import com.aamnnr.duitku.data.repository.CategoryRepository
import com.aamnnr.duitku.data.repository.DraftImporter
import com.aamnnr.duitku.data.repository.ProfileRepository
import com.aamnnr.duitku.data.repository.SettingsRepository
import com.aamnnr.duitku.data.repository.TransactionRepository
import com.aamnnr.duitku.data.repository.WalletRepository
import com.aamnnr.duitku.data.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** Dependency injection manual: satu instance untuk seluruh aplikasi. */
class AppContainer(app: Application, scope: CoroutineScope) {
    private val db = AppDatabase.build(app)
    val profiles = ProfileRepository(db)
    val wallets = WalletRepository(db)
    val categories = CategoryRepository(db)
    val transactions = TransactionRepository(db)
    val settings = SettingsRepository(db)
    val plans = com.aamnnr.duitku.data.repository.PlanRepository(db)
    val notificationDrafts = com.aamnnr.duitku.data.repository.NotificationDraftRepository(db)
    val database: AppDatabase get() = db
    val cloud = CloudService()
    val sync = SyncManager(db, cloud, scope)
    val drafts = DraftImporter(profiles, wallets, transactions, categories, settings)

    /** Dompet yang dipilih di tab Dompet (dipakai saat kartu di Beranda diketuk). */
    val walletSelection = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
}

class DuitkuApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, appScope)
        com.aamnnr.duitku.notif.Notifications.createChannels(this)
        appScope.launch {
            container.profiles.ensureInitialized()
            container.sync.start()
            com.aamnnr.duitku.work.Scheduler.apply(this@DuitkuApp)
            container.notificationDrafts.purgeOld()
        }
        // Widget ikut diperbarui beberapa detik setelah data berubah.
        @OptIn(kotlinx.coroutines.FlowPreview::class)
        container.database.syncDao().observeChangeMarker()
            .debounce(3_000)
            .onEach { com.aamnnr.duitku.widget.DuitkuWidget.refresh(this) }
            .launchIn(appScope)
    }
}
