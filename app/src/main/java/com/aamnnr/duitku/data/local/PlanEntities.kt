package com.aamnnr.duitku.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Anggaran untuk satu atau beberapa kategori pengeluaran. */
@Entity(
    tableName = "budgets",
    foreignKeys = [ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profile_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("profile_id")],
)
data class BudgetEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "profile_id") val profileId: String,
    val name: String,
    /** Id kategori dipisah koma; kosong = semua pengeluaran. */
    @ColumnInfo(name = "category_ids") val categoryIds: String,
    val amount: Long,
    /** MONTHLY atau WEEKLY. */
    val period: String = "MONTHLY",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = 0,
) {
    val categoryIdList: List<String> get() = categoryIds.split(",").filter { it.isNotBlank() }
}

/** Target tabungan, mis. dana darurat. Saldo tabungan = jumlah setor - tarik di saving_logs. */
@Entity(
    tableName = "saving_goals",
    foreignKeys = [ForeignKey(entity = ProfileEntity::class, parentColumns = ["id"], childColumns = ["profile_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("profile_id")],
)
data class SavingGoalEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "profile_id") val profileId: String,
    val name: String,
    @ColumnInfo(name = "target_amount") val targetAmount: Long,
    /** Tanggal target (epoch millis), opsional. */
    @ColumnInfo(name = "target_date") val targetDate: Long? = null,
    val icon: String,
    val color: Long?,
    /** ACTIVE, DONE, atau ARCHIVED. */
    val status: String = "ACTIVE",
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = 0,
)

@Entity(
    tableName = "saving_logs",
    foreignKeys = [ForeignKey(entity = SavingGoalEntity::class, parentColumns = ["id"], childColumns = ["goal_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("goal_id"), Index("profile_id")],
)
data class SavingLogEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "goal_id") val goalId: String,
    @ColumnInfo(name = "profile_id") val profileId: String,
    val amount: Long,
    /** DEPOSIT atau WITHDRAW. */
    val type: String,
    val note: String? = null,
    @ColumnInfo(name = "log_date") val logDate: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = 0,
)

/**
 * Draf dari notifikasi bank/e-wallet yang dibaca di HP (hanya lokal, tidak disinkron).
 * [fingerprint] mencegah notifikasi yang sama tercatat dua kali.
 */
@Entity(tableName = "notification_drafts", indices = [Index("status")])
data class NotificationDraftEntity(
    @PrimaryKey val fingerprint: String,
    @ColumnInfo(name = "source_package") val sourcePackage: String,
    @ColumnInfo(name = "source_name") val sourceName: String,
    @ColumnInfo(name = "raw_text") val rawText: String,
    /** INCOME atau EXPENSE. */
    val type: String,
    val amount: Long,
    val note: String?,
    @ColumnInfo(name = "wallet_id") val walletId: String?,
    @ColumnInfo(name = "category_id") val categoryId: String?,
    val confidence: Double,
    /** PENDING, IMPORTED, atau DISCARDED. */
    val status: String = "PENDING",
    @ColumnInfo(name = "received_at") val receivedAt: Long,
)
