package com.aamnnr.duitku.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class GoalWithSaved(
    @Embedded val goal: SavingGoalEntity,
    val saved: Long,
)

@Dao
interface PlanDao {
    @Query("SELECT * FROM budgets WHERE profile_id = :profileId AND is_deleted = 0 ORDER BY created_at")
    fun observeBudgets(profileId: String): Flow<List<BudgetEntity>>

    @Upsert
    suspend fun upsertBudget(budget: BudgetEntity)

    @Query("UPDATE budgets SET is_deleted = 1, updated_at = :now WHERE id = :id")
    suspend fun deleteBudget(id: String, now: Long = System.currentTimeMillis())

    @Query(
        """
        SELECT g.*, COALESCE(SUM(CASE WHEN l.type = 'DEPOSIT' THEN l.amount ELSE -l.amount END), 0) AS saved
        FROM saving_goals g
        LEFT JOIN saving_logs l ON l.goal_id = g.id AND l.is_deleted = 0
        WHERE g.profile_id = :profileId AND g.is_deleted = 0
        GROUP BY g.id
        ORDER BY g.status, g.created_at
        """
    )
    fun observeGoals(profileId: String): Flow<List<GoalWithSaved>>

    @Query("SELECT * FROM saving_goals WHERE id = :id")
    suspend fun getGoal(id: String): SavingGoalEntity?

    @Upsert
    suspend fun upsertGoal(goal: SavingGoalEntity)

    @Query("UPDATE saving_goals SET is_deleted = 1, updated_at = :now WHERE id = :id")
    suspend fun deleteGoal(id: String, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM saving_logs WHERE goal_id = :goalId AND is_deleted = 0 ORDER BY log_date DESC")
    fun observeLogs(goalId: String): Flow<List<SavingLogEntity>>

    @Upsert
    suspend fun upsertLog(log: SavingLogEntity)

    @Query("UPDATE saving_logs SET is_deleted = 1, updated_at = :now WHERE id = :id")
    suspend fun deleteLog(id: String, now: Long = System.currentTimeMillis())
}

@Dao
interface NotificationDraftDao {
    @Query("SELECT * FROM notification_drafts WHERE status = 'PENDING' ORDER BY received_at DESC")
    fun observePending(): Flow<List<NotificationDraftEntity>>

    @Query("SELECT COUNT(*) FROM notification_drafts WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    /** IGNORE: notifikasi yang sama (fingerprint sama) tidak ditambahkan lagi. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(draft: NotificationDraftEntity): Long

    @Query("UPDATE notification_drafts SET status = :status WHERE fingerprint = :fingerprint")
    suspend fun setStatus(fingerprint: String, status: String)

    @Query("SELECT * FROM notification_drafts WHERE fingerprint = :fingerprint")
    suspend fun get(fingerprint: String): NotificationDraftEntity?

    @Query("DELETE FROM notification_drafts WHERE status != 'PENDING' AND received_at < :before")
    suspend fun purgeHandled(before: Long)
}
