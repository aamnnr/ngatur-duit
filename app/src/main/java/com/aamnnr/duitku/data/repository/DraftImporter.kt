package com.aamnnr.duitku.data.repository

import com.aamnnr.duitku.data.remote.DraftPayload
import com.aamnnr.duitku.domain.TransactionType
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Mengubah draf (dari bot Telegram, asisten AI, atau scan struk) menjadi transaksi lokal.
 * Draf tanpa kategori tidak bisa disimpan langsung; pengguna perlu melengkapinya di form.
 */
class DraftImporter(
    private val profiles: ProfileRepository,
    private val wallets: WalletRepository,
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val settings: SettingsRepository,
) {
    /** True bila draf cukup lengkap untuk disimpan tanpa ditinjau di form. */
    fun isComplete(d: DraftPayload) = d.categoryId != null && d.amount > 0

    /** Menyimpan draf sebagai transaksi. Mengembalikan false bila datanya belum lengkap atau tidak cocok. */
    suspend fun import(d: DraftPayload): Boolean {
        if (!isComplete(d)) return false
        val activeId = profiles.activeProfileId.first()
        val profileId = d.profileId?.takeIf { pid -> profiles.profiles.first().any { it.id == pid } } ?: activeId
        val cats = categories.observeAll(profileId).first()
        val type = if (d.type == "INCOME") TransactionType.INCOME else TransactionType.EXPENSE
        if (cats.none { it.id == d.categoryId && it.type == type }) return false
        val walletList = wallets.observeWithBalance(profileId).first()
        val last = settings.lastWalletId()
        val walletId = listOfNotNull(d.walletId, last).firstOrNull { id -> walletList.any { it.id == id } }
            ?: walletList.firstOrNull()?.id
            ?: return false
        transactions.saveTransaction(
            profileId = profileId,
            id = null,
            type = type,
            amount = d.amount,
            categoryId = d.categoryId,
            walletId = walletId,
            note = d.note,
            date = draftDateMillis(d.date),
        )
        return true
    }

    companion object {
        /** "YYYY-MM-DD" → epoch millis (jam sekarang, agar urutan di riwayat wajar). */
        fun draftDateMillis(date: String): Long {
            val day = runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now())
            return day.atTime(LocalTime.now()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
    }
}
