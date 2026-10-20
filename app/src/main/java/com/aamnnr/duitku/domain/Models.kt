package com.aamnnr.duitku.domain

enum class ProfileType { PERSONAL, BUSINESS }

enum class TransactionType { INCOME, EXPENSE }

/** Arah transaksi transfer antar dompet; null untuk transaksi biasa. */
enum class TransferType { OUT, IN }

data class WalletWithBalance(
    val id: String,
    val name: String,
    val icon: String,
    val color: Long?,
    val isDefault: Boolean,
    val balance: Long,
    val accountSuffix: String? = null,
    val institution: String? = null,
)

/** Satu baris riwayat, sudah digabung dengan nama kategori & dompet. */
data class TransactionItem(
    val id: String,
    val type: TransactionType,
    val amount: Long,
    val note: String?,
    val date: Long,
    val categoryId: String?,
    val categoryName: String?,
    val categoryIcon: String?,
    val categoryColor: Long?,
    val walletId: String?,
    val walletName: String?,
    val isAdjustment: Boolean,
    val linkedTransferId: String?,
    val transferType: TransferType?,
) {
    val isTransfer: Boolean get() = transferType != null
}

data class PeriodSummary(val income: Long, val expense: Long) {
    val net: Long get() = income - expense
}

data class CategoryTotal(
    val categoryId: String?,
    val name: String,
    val icon: String?,
    val color: Long?,
    val total: Long,
)
