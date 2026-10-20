package com.aamnnr.duitku.data.repository

import androidx.room.withTransaction
import com.aamnnr.duitku.data.local.AppDatabase
import com.aamnnr.duitku.data.local.CategoryEntity
import com.aamnnr.duitku.data.local.DefaultData
import com.aamnnr.duitku.data.local.ProfileEntity
import com.aamnnr.duitku.data.local.SettingEntity
import com.aamnnr.duitku.data.local.TransactionEntity
import com.aamnnr.duitku.data.local.WalletEntity
import com.aamnnr.duitku.domain.ProfileType
import com.aamnnr.duitku.domain.TransactionType
import com.aamnnr.duitku.domain.TransferType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.util.UUID

private const val KEY_ACTIVE_PROFILE = "active_profile_id"

private fun newId() = UUID.randomUUID().toString()

class ProfileRepository(private val db: AppDatabase) {
    private val profileDao = db.profileDao()
    private val settingDao = db.settingDao()

    val activeProfileId: Flow<String> = settingDao.observe(KEY_ACTIVE_PROFILE).filterNotNull()

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeProfile: Flow<ProfileEntity?> = activeProfileId.flatMapLatest { profileDao.observe(it) }

    val profiles: Flow<List<ProfileEntity>> = profileDao.observeAll()

    /** Membuat profil pertama beserta dompet & kategori bawaan saat aplikasi pertama kali dibuka. */
    suspend fun ensureInitialized() {
        db.withTransaction {
            if (profileDao.count() == 0) {
                createProfileInternal("Pribadi", ProfileType.PERSONAL)
            } else if (settingDao.get(KEY_ACTIVE_PROFILE) == null) {
                profileDao.first()?.let { settingDao.put(SettingEntity(KEY_ACTIVE_PROFILE, it.id)) }
            }
        }
    }

    suspend fun createProfile(name: String, type: ProfileType): String =
        db.withTransaction { createProfileInternal(name, type) }

    private suspend fun createProfileInternal(name: String, type: ProfileType): String {
        val now = System.currentTimeMillis()
        val id = newId()
        profileDao.upsert(ProfileEntity(id, name, type, now, updatedAt = now))
        db.walletDao().upsert(DefaultData.mainWallet(id, now))
        db.categoryDao().insertAll(DefaultData.categoriesFor(id, type, now))
        settingDao.put(SettingEntity(KEY_ACTIVE_PROFILE, id))
        return id
    }

    suspend fun switchTo(profileId: String) = settingDao.put(SettingEntity(KEY_ACTIVE_PROFILE, profileId))

    suspend fun rename(profile: ProfileEntity, name: String) =
        profileDao.upsert(profile.copy(name = name, updatedAt = System.currentTimeMillis()))
}

class WalletRepository(private val db: AppDatabase) {
    private val walletDao = db.walletDao()
    private val txDao = db.transactionDao()

    fun observeWithBalance(profileId: String) = walletDao.observeWithBalance(profileId)

    suspend fun get(id: String) = walletDao.get(id)

    suspend fun save(
        profileId: String,
        id: String?,
        name: String,
        icon: String,
        color: Long?,
        initialBalance: Long = 0,
        accountSuffix: String? = null,
        institution: String? = null,
    ) {
        val existing = id?.let { walletDao.get(it) }
        if (existing != null) {
            walletDao.upsert(
                existing.copy(
                    name = name, icon = icon, color = color, accountSuffix = accountSuffix, institution = institution,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            return
        }
        db.withTransaction {
            val now = System.currentTimeMillis()
            val wallet = WalletEntity(
                id = newId(),
                profileId = profileId,
                name = name,
                icon = icon,
                color = color,
                isDefault = false,
                sortOrder = walletDao.maxSortOrder(profileId) + 1,
                createdAt = now,
                updatedAt = now,
                accountSuffix = accountSuffix,
                institution = institution,
            )
            walletDao.upsert(wallet)
            if (initialBalance != 0L) insertAdjustment(wallet, initialBalance, "Saldo awal", now)
        }
    }

    /** Menyamakan saldo dompet dengan saldo sebenarnya lewat transaksi penyesuaian. */
    suspend fun adjustBalance(walletId: String, actualBalance: Long) = db.withTransaction {
        val wallet = walletDao.get(walletId) ?: return@withTransaction
        val diff = actualBalance - walletDao.balanceOf(walletId)
        if (diff != 0L) insertAdjustment(wallet, diff, "Penyesuaian saldo", System.currentTimeMillis())
    }

    private suspend fun insertAdjustment(wallet: WalletEntity, diff: Long, note: String, now: Long) {
        txDao.upsert(
            TransactionEntity(
                id = newId(),
                profileId = wallet.profileId,
                type = if (diff > 0) TransactionType.INCOME else TransactionType.EXPENSE,
                amount = kotlin.math.abs(diff),
                categoryId = null,
                walletId = wallet.id,
                note = note,
                transactionDate = now,
                createdAt = now,
                isAdjustment = true,
                updatedAt = now,
            )
        )
    }

    suspend fun transactionCount(walletId: String) = txDao.countByWallet(walletId)

    /** Dompet utama tidak bisa dihapus. Transaksi di dalamnya ikut dihapus. */
    suspend fun delete(walletId: String): Boolean = db.withTransaction {
        val wallet = walletDao.get(walletId) ?: return@withTransaction false
        if (wallet.isDefault) return@withTransaction false
        txDao.softDeleteByWallet(walletId)
        walletDao.softDelete(walletId)
        true
    }
}

class CategoryRepository(private val db: AppDatabase) {
    private val dao = db.categoryDao()

    fun observeAll(profileId: String) = dao.observeAll(profileId)

    suspend fun save(profileId: String, id: String?, name: String, type: TransactionType, icon: String, color: Long?) {
        val existing = id?.let { dao.get(it) }
        dao.upsert(
            existing?.copy(name = name, icon = icon, color = color, updatedAt = System.currentTimeMillis())
                ?: CategoryEntity(newId(), profileId, name, type, icon, color, isDefault = false, updatedAt = System.currentTimeMillis())
        )
    }

    suspend fun usageCount(id: String) = dao.usageCount(id)

    /** Soft delete: transaksi lama tetap menampilkan nama kategori ini. */
    suspend fun delete(id: String) = dao.softDelete(id)
}

class TransactionRepository(private val db: AppDatabase) {
    private val dao = db.transactionDao()

    fun observeItems(profileId: String, from: Long, to: Long) = dao.observeItems(profileId, from, to)
    fun observeRecent(profileId: String, limit: Int) = dao.observeRecent(profileId, limit)
    fun observeSummary(profileId: String, from: Long, to: Long) = dao.observeSummary(profileId, from, to)
    fun observeCategoryTotals(profileId: String, type: TransactionType, from: Long, to: Long) =
        dao.observeCategoryTotals(profileId, type, from, to)

    suspend fun get(id: String) = dao.get(id)
    suspend fun getTransferPair(linkId: String) = dao.getTransferPair(linkId)

    suspend fun saveTransaction(
        profileId: String,
        id: String?,
        type: TransactionType,
        amount: Long,
        categoryId: String?,
        walletId: String,
        note: String?,
        date: Long,
        receiptPath: String? = null,
        /** true = pertahankan foto lama bila [receiptPath] null; false = pakai [receiptPath] apa adanya (bisa menghapus). */
        keepReceipt: Boolean = true,
    ): String {
        val existing = id?.let { dao.get(it) }
        val txId = existing?.id ?: newId()
        dao.upsert(
            TransactionEntity(
                id = txId,
                profileId = profileId,
                type = type,
                amount = amount,
                categoryId = categoryId,
                walletId = walletId,
                note = note,
                transactionDate = date,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                receiptPath = if (keepReceipt) receiptPath ?: existing?.receiptPath else receiptPath,
            )
        )
        return txId
    }

    /** Semua transaksi aktif satu profil dalam rentang waktu (untuk ekspor dan backup). */
    suspend fun allEntities(profileId: String) = dao.allForProfile(profileId)

    /** Transfer = dua transaksi berpasangan (keluar dari asal, masuk ke tujuan) dengan link id yang sama. */
    suspend fun saveTransfer(
        profileId: String,
        linkId: String?,
        fromWalletId: String,
        toWalletId: String,
        amount: Long,
        note: String?,
        date: Long,
    ) = db.withTransaction {
        val link = linkId ?: newId()
        val pair = if (linkId != null) dao.getTransferPair(linkId) else emptyList()
        val out = pair.firstOrNull { it.transferType == TransferType.OUT }
        val inn = pair.firstOrNull { it.transferType == TransferType.IN }
        val now = System.currentTimeMillis()
        fun entity(existing: TransactionEntity?, walletId: String, dir: TransferType) = TransactionEntity(
            id = existing?.id ?: newId(),
            profileId = profileId,
            type = if (dir == TransferType.OUT) TransactionType.EXPENSE else TransactionType.INCOME,
            amount = amount,
            categoryId = null,
            walletId = walletId,
            note = note,
            transactionDate = date,
            createdAt = existing?.createdAt ?: now,
            linkedTransferId = link,
            transferType = dir,
            updatedAt = now,
        )
        dao.upsert(entity(out, fromWalletId, TransferType.OUT))
        dao.upsert(entity(inn, toWalletId, TransferType.IN))
    }

    fun observeCategoryUsage(profileId: String) = dao.observeCategoryUsage(profileId)

    suspend fun delete(id: String) {
        val tx = dao.get(id) ?: return
        if (tx.linkedTransferId != null) dao.softDeleteTransfer(tx.linkedTransferId) else dao.softDelete(id)
    }

    /** Membatalkan [delete] (dipakai tombol "Urungkan"). */
    suspend fun restore(id: String) {
        val tx = dao.get(id) ?: return
        if (tx.linkedTransferId != null) dao.restoreTransfer(tx.linkedTransferId) else dao.restore(id)
    }
}

object Flags {
    const val AUTO_IMPORT = "auto_import_enabled"
    const val DAILY_REMINDER = "daily_reminder_enabled"
    const val BUDGET_ALERTS = "budget_alerts_enabled"
    const val APP_LOCK = "app_lock_enabled"
    const val BIOMETRIC = "biometric_enabled"
    const val PIN_HASH = "pin_hash"
    const val REMINDER_TIME = "daily_reminder_time"
}

enum class ThemeMode(val label: String) { SYSTEM("Ikuti sistem"), LIGHT("Terang"), DARK("Gelap") }

private const val KEY_THEME = "theme_mode"
private const val KEY_LAST_WALLET = "last_wallet_id"
private const val KEY_AI_CONSENT = "ai_consent"
private const val KEY_DISPLAY_NAME = "display_name"
private const val KEY_HIDE_BALANCE = "hide_balance"
private fun keyMonthlyLimit(profileId: String) = "monthly_limit_$profileId"

class SettingsRepository(db: AppDatabase) {
    private val dao = db.settingDao()

    val themeMode: Flow<ThemeMode> = dao.observe(KEY_THEME).map { v ->
        ThemeMode.entries.firstOrNull { it.name == v } ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) = dao.put(SettingEntity(KEY_THEME, mode.name))

    /** Dompet terakhir yang dipakai mencatat, jadi bawaan di form berikutnya. */
    suspend fun lastWalletId(): String? = dao.get(KEY_LAST_WALLET)

    suspend fun setLastWalletId(id: String) = dao.put(SettingEntity(KEY_LAST_WALLET, id))

    /** Persetujuan mengirim data seperlunya ke layanan AI (Gemini) lewat server Duitku. */
    val aiConsent: Flow<Boolean> = dao.observe(KEY_AI_CONSENT).map { it == "true" }

    suspend fun setAiConsent(value: Boolean) = dao.put(SettingEntity(KEY_AI_CONSENT, value.toString()))

    /** Nama pengguna untuk sapaan dan "nama pemilik" di kartu dompet. */
    val displayName: Flow<String> = dao.observe(KEY_DISPLAY_NAME).map { it.orEmpty() }

    suspend fun setDisplayName(name: String) = dao.put(SettingEntity(KEY_DISPLAY_NAME, name.trim()))

    /** Sembunyikan nominal saldo di seluruh aplikasi. */
    val hideBalance: Flow<Boolean> = dao.observe(KEY_HIDE_BALANCE).map { it == "true" }

    suspend fun setHideBalance(hide: Boolean) = dao.put(SettingEntity(KEY_HIDE_BALANCE, hide.toString()))

    /** Fitur yang bisa dinyalakan di Pengaturan. */
    fun flag(key: String): Flow<Boolean> = dao.observe(key).map { it == "true" }
    suspend fun flagNow(key: String): Boolean = dao.get(key) == "true"
    suspend fun setFlag(key: String, on: Boolean) = dao.put(SettingEntity(key, on.toString()))

    /** Teks bebas (mis. hash PIN, jam pengingat). */
    suspend fun text(key: String): String? = dao.get(key)
    fun observeText(key: String): Flow<String?> = dao.observe(key)
    suspend fun setText(key: String, value: String) = dao.put(SettingEntity(key, value))

    /** Batas pengeluaran bulanan per profil (rupiah); 0 = belum diatur. */
    fun monthlyLimit(profileId: String): Flow<Long> = dao.observe(keyMonthlyLimit(profileId)).map { it?.toLongOrNull() ?: 0L }

    suspend fun setMonthlyLimit(profileId: String, amount: Long) =
        dao.put(SettingEntity(keyMonthlyLimit(profileId), amount.coerceAtLeast(0).toString()))
}
