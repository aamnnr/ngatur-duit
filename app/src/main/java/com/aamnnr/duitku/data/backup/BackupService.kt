package com.aamnnr.duitku.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.aamnnr.duitku.data.local.AppDatabase
import com.aamnnr.duitku.data.remote.RemoteBudget
import com.aamnnr.duitku.data.remote.RemoteCategory
import com.aamnnr.duitku.data.remote.RemoteGoal
import com.aamnnr.duitku.data.remote.RemoteProfile
import com.aamnnr.duitku.data.remote.RemoteSavingLog
import com.aamnnr.duitku.data.remote.RemoteTransaction
import com.aamnnr.duitku.data.remote.RemoteWallet
import com.aamnnr.duitku.data.remote.toEntity
import com.aamnnr.duitku.data.remote.toRemote
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Isi file backup. Memakai bentuk baris yang sama dengan sinkron cloud. */
@Serializable
data class BackupFile(
    val app: String = "duitku",
    val version: Int = 1,
    val createdAt: Long,
    val profiles: List<RemoteProfile>,
    val wallets: List<RemoteWallet>,
    val categories: List<RemoteCategory>,
    val transactions: List<RemoteTransaction>,
    val budgets: List<RemoteBudget> = emptyList(),
    val goals: List<RemoteGoal> = emptyList(),
    val logs: List<RemoteSavingLog> = emptyList(),
)

data class RestoreResult(val transactions: Int, val wallets: Int, val profiles: Int)

/**
 * Backup lengkap ke satu file JSON (tanpa akun) dan restore dengan menggabungkan berdasarkan id.
 * Baris yang dipulihkan diberi updated_at sekarang supaya ikut tersinkron bila akun aktif.
 */
class BackupService(private val context: Context, private val db: AppDatabase) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false; explicitNulls = false }

    suspend fun export(uri: Uri): Int {
        val sync = db.syncDao()
        // -1: ambil semua baris (updated_at ≥ 0), termasuk yang dihapus agar penghapusan ikut terpulihkan.
        val file = BackupFile(
            createdAt = System.currentTimeMillis(),
            profiles = sync.changedProfiles(-1).map { it.toRemote() },
            wallets = sync.changedWallets(-1).map { it.toRemote() },
            categories = sync.changedCategories(-1).map { it.toRemote() },
            transactions = sync.changedTransactions(-1).map { it.toRemote() },
            budgets = sync.changedBudgets(-1).map { it.toRemote() },
            goals = sync.changedGoals(-1).map { it.toRemote() },
            logs = sync.changedLogs(-1).map { it.toRemote() },
        )
        context.contentResolver.openOutputStream(uri)?.use { it.write(json.encodeToString(BackupFile.serializer(), file).toByteArray()) }
            ?: error("Tidak bisa menulis file")
        return file.transactions.count { !it.isDeleted }
    }

    suspend fun restore(uri: Uri): RestoreResult {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("Tidak bisa membaca file")
        val file = json.decodeFromString(BackupFile.serializer(), text)
        require(file.app == "duitku") { "Bukan file backup Duitku" }
        val now = System.currentTimeMillis()
        val sync = db.syncDao()
        db.withTransaction {
            sync.upsertProfiles(file.profiles.map { it.toEntity().copy(updatedAt = now) })
            sync.upsertWallets(file.wallets.map { it.toEntity().copy(updatedAt = now) })
            sync.upsertCategories(file.categories.map { it.toEntity().copy(updatedAt = now) })
            sync.upsertTransactions(file.transactions.map { r ->
                r.toEntity().copy(updatedAt = now, receiptPath = db.transactionDao().get(r.id)?.receiptPath)
            })
            sync.upsertBudgets(file.budgets.map { it.toEntity().copy(updatedAt = now) })
            sync.upsertGoals(file.goals.map { it.toEntity().copy(updatedAt = now) })
            sync.upsertLogs(file.logs.map { it.toEntity().copy(updatedAt = now) })
        }
        return RestoreResult(file.transactions.count { !it.isDeleted }, file.wallets.count { !it.isDeleted }, file.profiles.size)
    }
}
