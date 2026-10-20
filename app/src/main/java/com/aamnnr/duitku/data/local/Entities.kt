package com.aamnnr.duitku.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.aamnnr.duitku.domain.ProfileType
import com.aamnnr.duitku.domain.TransactionType
import com.aamnnr.duitku.domain.TransferType

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: ProfileType,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = 0,
)

@Entity(
    tableName = "wallets",
    foreignKeys = [ForeignKey(
        entity = ProfileEntity::class,
        parentColumns = ["id"],
        childColumns = ["profile_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("profile_id")],
)
data class WalletEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "profile_id") val profileId: String,
    val name: String,
    val icon: String,
    val color: Long?,
    @ColumnInfo(name = "is_default") val isDefault: Boolean,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = 0,
    /** 4 digit terakhir nomor rekening/kartu (opsional), tampil sebagai "•••• 2038". */
    @ColumnInfo(name = "account_suffix") val accountSuffix: String? = null,
    /** Nama bank/penyedia (opsional), mis. "BCA Digital". */
    val institution: String? = null,
)

@Entity(
    tableName = "categories",
    foreignKeys = [ForeignKey(
        entity = ProfileEntity::class,
        parentColumns = ["id"],
        childColumns = ["profile_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("profile_id")],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "profile_id") val profileId: String,
    val name: String,
    val type: TransactionType,
    val icon: String,
    val color: Long?,
    @ColumnInfo(name = "is_default") val isDefault: Boolean,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = 0,
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("profile_id"),
        Index("wallet_id"),
        Index("category_id"),
        Index("transaction_date"),
        Index("linked_transfer_id"),
    ],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "profile_id") val profileId: String,
    val type: TransactionType,
    /** Nominal dalam rupiah penuh, selalu positif. */
    val amount: Long,
    /** Null untuk transfer dan penyesuaian saldo. */
    @ColumnInfo(name = "category_id") val categoryId: String?,
    @ColumnInfo(name = "wallet_id") val walletId: String?,
    val note: String?,
    @ColumnInfo(name = "transaction_date") val transactionDate: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "is_adjustment") val isAdjustment: Boolean = false,
    @ColumnInfo(name = "linked_transfer_id") val linkedTransferId: String? = null,
    @ColumnInfo(name = "transfer_type") val transferType: TransferType? = null,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean = false,
    /** Waktu perubahan lokal terakhir (epoch millis); dipakai sinkron. 0 = tidak ada perubahan lokal. */
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAt: Long = 0,
    /** Path foto struk di penyimpanan internal aplikasi (hanya lokal, tidak disinkron). */
    @ColumnInfo(name = "receipt_path") val receiptPath: String? = null,
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)
