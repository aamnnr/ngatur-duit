@file:OptIn(ExperimentalSerializationApi::class)

package com.aamnnr.duitku.data.remote

import com.aamnnr.duitku.data.local.CategoryEntity
import com.aamnnr.duitku.data.local.ProfileEntity
import com.aamnnr.duitku.data.local.TransactionEntity
import com.aamnnr.duitku.data.local.WalletEntity
import com.aamnnr.duitku.domain.ProfileType
import com.aamnnr.duitku.domain.TransactionType
import com.aamnnr.duitku.domain.TransferType
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Baris tabel Supabase (lihat supabase/migrations). user_id diisi otomatis oleh default auth.uid(),
// updated_at diisi server; keduanya hanya dibaca. updated_at ditandai EncodeDefault.NEVER agar
// tidak terkirim sebagai null (kolomnya NOT NULL).

@Serializable
data class RemoteProfile(
    val id: String,
    val name: String,
    val type: String,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class RemoteWallet(
    val id: String,
    @SerialName("profile_id") val profileId: String,
    val name: String,
    val icon: String,
    val color: Long? = null,
    @SerialName("is_default") val isDefault: Boolean,
    @SerialName("sort_order") val sortOrder: Int,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("is_deleted") val isDeleted: Boolean,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("account_suffix") val accountSuffix: String? = null,
    val institution: String? = null,
)

@Serializable
data class RemoteCategory(
    val id: String,
    @SerialName("profile_id") val profileId: String,
    val name: String,
    val type: String,
    val icon: String,
    val color: Long? = null,
    @SerialName("is_default") val isDefault: Boolean,
    @SerialName("is_deleted") val isDeleted: Boolean,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class RemoteTransaction(
    val id: String,
    @SerialName("profile_id") val profileId: String,
    val type: String,
    val amount: Long,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("wallet_id") val walletId: String? = null,
    val note: String? = null,
    @SerialName("transaction_date") val transactionDate: Long,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("is_adjustment") val isAdjustment: Boolean,
    @SerialName("linked_transfer_id") val linkedTransferId: String? = null,
    @SerialName("transfer_type") val transferType: String? = null,
    @SerialName("is_deleted") val isDeleted: Boolean,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("updated_at") val updatedAt: String? = null,
)

// --- Lokal -> cloud -------------------------------------------------------

fun ProfileEntity.toRemote() = RemoteProfile(id, name, type.name, createdAt)

fun WalletEntity.toRemote() = RemoteWallet(
    id, profileId, name, icon, color, isDefault, sortOrder, createdAt, isDeleted,
    accountSuffix = accountSuffix, institution = institution,
)

fun CategoryEntity.toRemote() = RemoteCategory(id, profileId, name, type.name, icon, color, isDefault, isDeleted)

fun TransactionEntity.toRemote() = RemoteTransaction(
    id, profileId, type.name, amount, categoryId, walletId, note, transactionDate, createdAt,
    isAdjustment, linkedTransferId, transferType?.name, isDeleted,
)

// --- Cloud -> lokal (updatedAt = 0: bukan perubahan lokal) ------------------

fun RemoteProfile.toEntity() = ProfileEntity(id, name, ProfileType.valueOf(type), createdAt, updatedAt = 0)

fun RemoteWallet.toEntity() = WalletEntity(
    id, profileId, name, icon, color, isDefault, sortOrder, createdAt, isDeleted, updatedAt = 0,
    accountSuffix = accountSuffix, institution = institution,
)

fun RemoteCategory.toEntity() = CategoryEntity(
    id, profileId, name, TransactionType.valueOf(type), icon, color, isDefault, isDeleted, updatedAt = 0,
)

fun RemoteTransaction.toEntity() = TransactionEntity(
    id = id,
    profileId = profileId,
    type = TransactionType.valueOf(type),
    amount = amount,
    categoryId = categoryId,
    walletId = walletId,
    note = note,
    transactionDate = transactionDate,
    createdAt = createdAt,
    isAdjustment = isAdjustment,
    linkedTransferId = linkedTransferId,
    transferType = transferType?.let(TransferType::valueOf),
    isDeleted = isDeleted,
    updatedAt = 0,
)

// --- Inbox draf & Telegram ------------------------------------------------

@Serializable
data class DraftPayload(
    val type: String,
    val amount: Long,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("wallet_id") val walletId: String? = null,
    val note: String? = null,
    /** "YYYY-MM-DD" */
    val date: String,
    val confidence: Double = 0.0,
    @SerialName("profile_id") val profileId: String? = null,
)

@Serializable
data class RemoteDraft(
    val id: String,
    val source: String,
    @SerialName("raw_text") val rawText: String? = null,
    val parsed: DraftPayload,
    val status: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class RemoteTelegramLink(
    @SerialName("chat_id") val chatId: Long,
    val username: String? = null,
    @SerialName("linked_at") val linkedAt: String,
)

// --- Anggaran & tabungan (Fase 2) ------------------------------------------

@Serializable
data class RemoteBudget(
    val id: String,
    @SerialName("profile_id") val profileId: String,
    val name: String,
    @SerialName("category_ids") val categoryIds: String,
    val amount: Long,
    val period: String,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("is_deleted") val isDeleted: Boolean,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class RemoteGoal(
    val id: String,
    @SerialName("profile_id") val profileId: String,
    val name: String,
    @SerialName("target_amount") val targetAmount: Long,
    @SerialName("target_date") val targetDate: Long? = null,
    val icon: String,
    val color: Long? = null,
    val status: String,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("is_deleted") val isDeleted: Boolean,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class RemoteSavingLog(
    val id: String,
    @SerialName("goal_id") val goalId: String,
    @SerialName("profile_id") val profileId: String,
    val amount: Long,
    val type: String,
    val note: String? = null,
    @SerialName("log_date") val logDate: Long,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("is_deleted") val isDeleted: Boolean,
    @EncodeDefault(EncodeDefault.Mode.NEVER) @SerialName("updated_at") val updatedAt: String? = null,
)

fun com.aamnnr.duitku.data.local.BudgetEntity.toRemote() =
    RemoteBudget(id, profileId, name, categoryIds, amount, period, createdAt, isDeleted)

fun RemoteBudget.toEntity() = com.aamnnr.duitku.data.local.BudgetEntity(
    id, profileId, name, categoryIds, amount, period, createdAt, isDeleted, updatedAt = 0,
)

fun com.aamnnr.duitku.data.local.SavingGoalEntity.toRemote() =
    RemoteGoal(id, profileId, name, targetAmount, targetDate, icon, color, status, createdAt, isDeleted)

fun RemoteGoal.toEntity() = com.aamnnr.duitku.data.local.SavingGoalEntity(
    id, profileId, name, targetAmount, targetDate, icon, color, status, createdAt, isDeleted, updatedAt = 0,
)

fun com.aamnnr.duitku.data.local.SavingLogEntity.toRemote() =
    RemoteSavingLog(id, goalId, profileId, amount, type, note, logDate, createdAt, isDeleted)

fun RemoteSavingLog.toEntity() = com.aamnnr.duitku.data.local.SavingLogEntity(
    id, goalId, profileId, amount, type, note, logDate, createdAt, isDeleted, updatedAt = 0,
)
