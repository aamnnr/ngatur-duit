package com.aamnnr.duitku.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.aamnnr.duitku.domain.CategoryTotal
import com.aamnnr.duitku.domain.PeriodSummary
import com.aamnnr.duitku.domain.TransactionItem
import com.aamnnr.duitku.domain.TransactionType
import com.aamnnr.duitku.domain.WalletWithBalance
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY created_at")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id = :id")
    fun observe(id: String): Flow<ProfileEntity?>

    @Query("SELECT COUNT(*) FROM profiles")
    suspend fun count(): Int

    @Query("SELECT * FROM profiles ORDER BY created_at LIMIT 1")
    suspend fun first(): ProfileEntity?

    @Upsert
    suspend fun upsert(profile: ProfileEntity)
}

@Dao
interface WalletDao {
    @Query(
        """
        SELECT w.id, w.name, w.icon, w.color, w.is_default AS isDefault,
            w.account_suffix AS accountSuffix, w.institution AS institution,
            COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amount
                              WHEN t.type = 'EXPENSE' THEN -t.amount END), 0) AS balance
        FROM wallets w
        LEFT JOIN transactions t ON t.wallet_id = w.id AND t.is_deleted = 0
        WHERE w.profile_id = :profileId AND w.is_deleted = 0
        GROUP BY w.id
        ORDER BY w.is_default DESC, w.sort_order, w.created_at
        """
    )
    fun observeWithBalance(profileId: String): Flow<List<WalletWithBalance>>

    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE -amount END), 0)
        FROM transactions WHERE wallet_id = :walletId AND is_deleted = 0
        """
    )
    suspend fun balanceOf(walletId: String): Long

    @Query("SELECT * FROM wallets WHERE id = :id")
    suspend fun get(id: String): WalletEntity?

    @Query("SELECT * FROM wallets WHERE profile_id = :profileId AND is_default = 1 AND is_deleted = 0 LIMIT 1")
    suspend fun getDefault(profileId: String): WalletEntity?

    @Query("SELECT COALESCE(MAX(sort_order), 0) FROM wallets WHERE profile_id = :profileId")
    suspend fun maxSortOrder(profileId: String): Int

    @Upsert
    suspend fun upsert(wallet: WalletEntity)

    @Query("UPDATE wallets SET is_deleted = 1, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long = System.currentTimeMillis())
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE profile_id = :profileId AND is_deleted = 0 ORDER BY is_default DESC, name")
    fun observeAll(profileId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun get(id: String): CategoryEntity?

    @Query("SELECT COUNT(*) FROM transactions WHERE category_id = :id AND is_deleted = 0")
    suspend fun usageCount(id: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Upsert
    suspend fun upsert(category: CategoryEntity)

    @Query("UPDATE categories SET is_deleted = 1, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long = System.currentTimeMillis())
}

private const val ITEM_COLUMNS = """
    t.id, t.type, t.amount, t.note, t.transaction_date AS date,
    t.category_id AS categoryId, c.name AS categoryName, c.icon AS categoryIcon, c.color AS categoryColor,
    t.wallet_id AS walletId, w.name AS walletName,
    t.is_adjustment AS isAdjustment, t.linked_transfer_id AS linkedTransferId, t.transfer_type AS transferType
    FROM transactions t
    LEFT JOIN categories c ON c.id = t.category_id
    LEFT JOIN wallets w ON w.id = t.wallet_id
"""

@Dao
interface TransactionDao {
    @Query(
        "SELECT $ITEM_COLUMNS WHERE t.profile_id = :profileId AND t.is_deleted = 0 " +
            "AND t.transaction_date BETWEEN :from AND :to " +
            "ORDER BY t.transaction_date DESC, t.created_at DESC"
    )
    fun observeItems(profileId: String, from: Long, to: Long): Flow<List<TransactionItem>>

    @Query(
        "SELECT $ITEM_COLUMNS WHERE t.profile_id = :profileId AND t.is_deleted = 0 " +
            "ORDER BY t.transaction_date DESC, t.created_at DESC LIMIT :limit"
    )
    fun observeRecent(profileId: String, limit: Int): Flow<List<TransactionItem>>

    /** Ringkasan arus kas: transfer & penyesuaian saldo tidak dihitung. */
    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount END), 0) AS income,
               COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount END), 0) AS expense
        FROM transactions
        WHERE profile_id = :profileId AND is_deleted = 0 AND is_adjustment = 0
            AND transfer_type IS NULL AND transaction_date BETWEEN :from AND :to
        """
    )
    fun observeSummary(profileId: String, from: Long, to: Long): Flow<PeriodSummary>

    @Query(
        """
        SELECT t.category_id AS categoryId, COALESCE(c.name, 'Tanpa Kategori') AS name,
            c.icon AS icon, c.color AS color, SUM(t.amount) AS total
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.category_id
        WHERE t.profile_id = :profileId AND t.is_deleted = 0 AND t.is_adjustment = 0
            AND t.transfer_type IS NULL AND t.type = :type
            AND t.transaction_date BETWEEN :from AND :to
        GROUP BY t.category_id
        ORDER BY total DESC
        """
    )
    fun observeCategoryTotals(
        profileId: String,
        type: TransactionType,
        from: Long,
        to: Long,
    ): Flow<List<CategoryTotal>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE profile_id = :profileId AND is_deleted = 0 ORDER BY transaction_date")
    suspend fun allForProfile(profileId: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE linked_transfer_id = :linkId AND is_deleted = 0")
    suspend fun getTransferPair(linkId: String): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions WHERE wallet_id = :walletId AND is_deleted = 0")
    suspend fun countByWallet(walletId: String): Int

    @Upsert
    suspend fun upsert(tx: TransactionEntity)

    @Query("UPDATE transactions SET is_deleted = 1, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE transactions SET is_deleted = 1, updated_at = :now WHERE linked_transfer_id = :linkId")
    suspend fun softDeleteTransfer(linkId: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE transactions SET is_deleted = 1, updated_at = :now WHERE wallet_id = :walletId")
    suspend fun softDeleteByWallet(walletId: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE transactions SET is_deleted = 0, updated_at = :now WHERE id = :id")
    suspend fun restore(id: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE transactions SET is_deleted = 0, updated_at = :now WHERE linked_transfer_id = :linkId")
    suspend fun restoreTransfer(linkId: String, now: Long = System.currentTimeMillis())

    /** Jumlah pemakaian tiap kategori, untuk menaruh kategori favorit di depan. */
    @Query(
        """
        SELECT category_id AS categoryId, COUNT(*) AS uses FROM transactions
        WHERE profile_id = :profileId AND is_deleted = 0 AND category_id IS NOT NULL
        GROUP BY category_id
        """
    )
    fun observeCategoryUsage(profileId: String): Flow<List<CategoryUsage>>
}

data class CategoryUsage(val categoryId: String, val uses: Int)

@Dao
interface SettingDao {
    @Query("SELECT value FROM settings WHERE `key` = :key")
    fun observe(key: String): Flow<String?>

    @Query("SELECT value FROM settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun put(setting: SettingEntity)
}

/** Kueri khusus sinkron cloud. Baris dari cloud ditulis dengan updated_at = 0 (tidak dianggap perubahan lokal). */
@Dao
interface SyncDao {
    @Query("SELECT * FROM profiles WHERE updated_at > :since")
    suspend fun changedProfiles(since: Long): List<ProfileEntity>

    @Query("SELECT * FROM wallets WHERE updated_at > :since")
    suspend fun changedWallets(since: Long): List<WalletEntity>

    @Query("SELECT * FROM categories WHERE updated_at > :since")
    suspend fun changedCategories(since: Long): List<CategoryEntity>

    @Query("SELECT * FROM transactions WHERE updated_at > :since")
    suspend fun changedTransactions(since: Long): List<TransactionEntity>

    @Upsert suspend fun upsertProfiles(rows: List<ProfileEntity>)
    @Upsert suspend fun upsertWallets(rows: List<WalletEntity>)
    @Upsert suspend fun upsertCategories(rows: List<CategoryEntity>)
    @Upsert suspend fun upsertTransactions(rows: List<TransactionEntity>)

    @Query("SELECT * FROM budgets WHERE updated_at > :since")
    suspend fun changedBudgets(since: Long): List<BudgetEntity>

    @Query("SELECT * FROM saving_goals WHERE updated_at > :since")
    suspend fun changedGoals(since: Long): List<SavingGoalEntity>

    @Query("SELECT * FROM saving_logs WHERE updated_at > :since")
    suspend fun changedLogs(since: Long): List<SavingLogEntity>

    @Upsert suspend fun upsertBudgets(rows: List<BudgetEntity>)
    @Upsert suspend fun upsertGoals(rows: List<SavingGoalEntity>)
    @Upsert suspend fun upsertLogs(rows: List<SavingLogEntity>)

    /** Berubah setiap kali ada perubahan lokal; dipakai untuk memicu sinkron otomatis. */
    @Query(
        """
        SELECT (SELECT COALESCE(MAX(updated_at), 0) FROM profiles)
             + (SELECT COALESCE(MAX(updated_at), 0) FROM wallets)
             + (SELECT COALESCE(MAX(updated_at), 0) FROM categories)
             + (SELECT COALESCE(MAX(updated_at), 0) FROM transactions)
             + (SELECT COALESCE(MAX(updated_at), 0) FROM budgets)
             + (SELECT COALESCE(MAX(updated_at), 0) FROM saving_goals)
             + (SELECT COALESCE(MAX(updated_at), 0) FROM saving_logs)
        """
    )
    fun observeChangeMarker(): Flow<Long>
}
