package com.aamnnr.duitku.data.sync

import android.util.Log
import androidx.room.withTransaction
import com.aamnnr.duitku.data.local.AppDatabase
import com.aamnnr.duitku.data.local.SettingEntity
import com.aamnnr.duitku.data.remote.CloudService
import com.aamnnr.duitku.data.remote.toEntity
import com.aamnnr.duitku.data.remote.toRemote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SyncState(
    val running: Boolean = false,
    /** Waktu sinkron terakhir yang berhasil (epoch millis), 0 = belum pernah. */
    val lastSuccessAt: Long = 0,
    val error: String? = null,
    /** Jumlah draf di inbox cloud yang menunggu disetujui. */
    val pendingDrafts: Int = 0,
)

private const val KEY_LAST_PUSH = "sync_last_push"
private const val KEY_CURSOR = "sync_cursor"
private const val KEY_LAST_SUCCESS = "sync_last_success"
private const val EPOCH = "1970-01-01T00:00:00Z"

/**
 * Sinkron dua arah Room ↔ Supabase.
 *
 * 1. Push: kirim baris lokal dengan updated_at > waktu push terakhir (upsert by id).
 * 2. Pull: ambil baris cloud dengan updated_at (server) > cursor, tulis ke Room dengan updated_at = 0.
 *    Baris yang diubah lokal selama proses sinkron tidak ditimpa; baris itu ikut push berikutnya.
 *
 * Satu pengguna dengan satu-dua HP jarang bentrok, jadi aturan "perubahan lokal terbaru menang" cukup.
 */
class SyncManager(
    private val db: AppDatabase,
    private val cloud: CloudService,
    private val scope: CoroutineScope,
) {
    private val dao = db.syncDao()
    private val settings = db.settingDao()
    private val mutex = Mutex()
    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state

    /** Mulai sinkron otomatis: saat masuk akun dan beberapa detik setelah ada perubahan lokal. */
    @OptIn(FlowPreview::class)
    fun start() {
        if (!cloud.isConfigured) return
        scope.launch { _state.update { it.copy(lastSuccessAt = settings.get(KEY_LAST_SUCCESS)?.toLongOrNull() ?: 0) } }
        cloud.account.filterNotNull().distinctUntilChanged { a, b -> a.id == b.id }
            .onEach { requestSync() }
            .launchIn(scope)
        dao.observeChangeMarker().drop(1).debounce(4_000)
            .onEach { if (cloud.currentAccount() != null) requestSync() }
            .launchIn(scope)
    }

    fun requestSync() {
        scope.launch { syncNow() }
    }

    /** Menjalankan sinkron sekarang. Mengembalikan true bila berhasil. */
    suspend fun syncNow(): Boolean {
        if (!cloud.isConfigured || cloud.currentAccount() == null) return false
        if (mutex.isLocked) return true
        return mutex.withLock {
            _state.update { it.copy(running = true, error = null) }
            try {
                val startedAt = System.currentTimeMillis()
                push()
                pull(startedAt)
                val drafts = runCatching { cloud.pendingDrafts().size }.getOrDefault(_state.value.pendingDrafts)
                settings.put(SettingEntity(KEY_LAST_SUCCESS, startedAt.toString()))
                _state.update { it.copy(running = false, lastSuccessAt = startedAt, pendingDrafts = drafts) }
                true
            } catch (e: Exception) {
                Log.w("SyncManager", "Sinkron gagal", e)
                _state.update { it.copy(running = false, error = "Sinkron gagal. Periksa koneksi internet lalu coba lagi.") }
                false
            }
        }
    }

    /** Perbarui hitungan draf saja (mis. setelah draf disetujui). */
    fun setPendingDrafts(count: Int) = _state.update { it.copy(pendingDrafts = count) }

    private suspend fun push() {
        // -1 pada sinkron pertama: kirim semua baris, termasuk data lama ber-updated_at 0.
        val since = settings.get(KEY_LAST_PUSH)?.toLongOrNull() ?: -1L
        val startedAt = System.currentTimeMillis()
        // Urutan penting karena foreign key: profil → dompet/kategori → transaksi.
        dao.changedProfiles(since).takeIf { it.isNotEmpty() }?.let { rows -> cloud.upsertProfiles(rows.map { it.toRemote() }) }
        dao.changedWallets(since).takeIf { it.isNotEmpty() }?.let { rows -> cloud.upsertWallets(rows.map { it.toRemote() }) }
        dao.changedCategories(since).takeIf { it.isNotEmpty() }?.let { rows -> cloud.upsertCategories(rows.map { it.toRemote() }) }
        dao.changedTransactions(since).takeIf { it.isNotEmpty() }?.let { rows -> cloud.upsertTransactions(rows.map { it.toRemote() }) }
        dao.changedBudgets(since).takeIf { it.isNotEmpty() }?.let { rows -> cloud.upsertBudgets(rows.map { it.toRemote() }) }
        dao.changedGoals(since).takeIf { it.isNotEmpty() }?.let { rows -> cloud.upsertGoals(rows.map { it.toRemote() }) }
        dao.changedLogs(since).takeIf { it.isNotEmpty() }?.let { rows -> cloud.upsertLogs(rows.map { it.toRemote() }) }
        settings.put(SettingEntity(KEY_LAST_PUSH, startedAt.toString()))
    }

    private suspend fun pull(syncStartedAt: Long) {
        val cursor = settings.get(KEY_CURSOR) ?: EPOCH
        val profiles = cloud.profilesSince(cursor)
        val wallets = cloud.walletsSince(cursor)
        val categories = cloud.categoriesSince(cursor)
        val transactions = cloud.transactionsSince(cursor)
        val budgets = cloud.budgetsSince(cursor)
        val goals = cloud.goalsSince(cursor)
        val logs = cloud.logsSince(cursor)
        if (listOf(profiles, wallets, categories, transactions, budgets, goals, logs).all { it.isEmpty() }) return

        // Jangan timpa baris yang baru saja diubah di HP selama sinkron berjalan.
        val dirty = buildSet {
            addAll(dao.changedProfiles(syncStartedAt).map { it.id })
            addAll(dao.changedWallets(syncStartedAt).map { it.id })
            addAll(dao.changedCategories(syncStartedAt).map { it.id })
            addAll(dao.changedTransactions(syncStartedAt).map { it.id })
            addAll(dao.changedBudgets(syncStartedAt).map { it.id })
            addAll(dao.changedGoals(syncStartedAt).map { it.id })
            addAll(dao.changedLogs(syncStartedAt).map { it.id })
        }
        db.withTransaction {
            dao.upsertProfiles(profiles.filter { it.id !in dirty }.map { it.toEntity() })
            dao.upsertWallets(wallets.filter { it.id !in dirty }.map { it.toEntity() })
            dao.upsertCategories(categories.filter { it.id !in dirty }.map { it.toEntity() })
            // Foto struk hanya ada di HP: pertahankan path lokal saat baris ditimpa dari cloud.
            dao.upsertTransactions(
                transactions.filter { it.id !in dirty }.map { r ->
                    r.toEntity().copy(receiptPath = db.transactionDao().get(r.id)?.receiptPath)
                },
            )
            dao.upsertBudgets(budgets.filter { it.id !in dirty }.map { it.toEntity() })
            dao.upsertGoals(goals.filter { it.id !in dirty }.map { it.toEntity() })
            dao.upsertLogs(logs.filter { it.id !in dirty }.map { it.toEntity() })
        }
        val newest = (profiles.mapNotNull { it.updatedAt } + wallets.mapNotNull { it.updatedAt } +
            categories.mapNotNull { it.updatedAt } + transactions.mapNotNull { it.updatedAt } +
            budgets.mapNotNull { it.updatedAt } + goals.mapNotNull { it.updatedAt } + logs.mapNotNull { it.updatedAt })
            .maxByOrNull { java.time.OffsetDateTime.parse(it) }
        if (newest != null) settings.put(SettingEntity(KEY_CURSOR, newest))
    }

    /** Dipanggil saat keluar akun: lupakan posisi sinkron agar akun berikutnya mulai dari awal. */
    suspend fun reset() {
        settings.put(SettingEntity(KEY_LAST_PUSH, "-1"))
        settings.put(SettingEntity(KEY_CURSOR, EPOCH))
        settings.put(SettingEntity(KEY_LAST_SUCCESS, "0"))
        _state.value = SyncState()
    }
}
