package com.aamnnr.duitku.data.repository

import androidx.room.withTransaction
import com.aamnnr.duitku.data.local.AppDatabase
import com.aamnnr.duitku.data.local.BudgetEntity
import com.aamnnr.duitku.data.local.NotificationDraftEntity
import com.aamnnr.duitku.data.local.SavingGoalEntity
import com.aamnnr.duitku.data.local.SavingLogEntity
import java.util.UUID

class PlanRepository(private val db: AppDatabase) {
    private val dao = db.planDao()

    fun observeBudgets(profileId: String) = dao.observeBudgets(profileId)
    fun observeGoals(profileId: String) = dao.observeGoals(profileId)
    fun observeLogs(goalId: String) = dao.observeLogs(goalId)

    suspend fun saveBudget(profileId: String, id: String?, name: String, categoryIds: List<String>, amount: Long, period: String) {
        val now = System.currentTimeMillis()
        dao.upsertBudget(
            BudgetEntity(
                id = id ?: UUID.randomUUID().toString(),
                profileId = profileId,
                name = name,
                categoryIds = categoryIds.joinToString(","),
                amount = amount,
                period = period,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun deleteBudget(id: String) = dao.deleteBudget(id)

    suspend fun saveGoal(
        profileId: String,
        id: String?,
        name: String,
        target: Long,
        targetDate: Long?,
        icon: String,
        color: Long?,
    ) {
        val now = System.currentTimeMillis()
        val existing = id?.let { dao.getGoal(it) }
        dao.upsertGoal(
            existing?.copy(name = name, targetAmount = target, targetDate = targetDate, icon = icon, color = color, updatedAt = now)
                ?: SavingGoalEntity(UUID.randomUUID().toString(), profileId, name, target, targetDate, icon, color, createdAt = now, updatedAt = now),
        )
    }

    suspend fun setGoalStatus(id: String, status: String) {
        val g = dao.getGoal(id) ?: return
        dao.upsertGoal(g.copy(status = status, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteGoal(id: String) = dao.deleteGoal(id)

    /** Setor (positif) atau tarik (negatif) tabungan. */
    suspend fun addLog(goal: SavingGoalEntity, amount: Long, note: String?, date: Long) = db.withTransaction {
        val now = System.currentTimeMillis()
        dao.upsertLog(
            SavingLogEntity(
                id = UUID.randomUUID().toString(),
                goalId = goal.id,
                profileId = goal.profileId,
                amount = kotlin.math.abs(amount),
                type = if (amount >= 0) "DEPOSIT" else "WITHDRAW",
                note = note,
                logDate = date,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun deleteLog(id: String) = dao.deleteLog(id)
}

class NotificationDraftRepository(db: AppDatabase) {
    private val dao = db.notificationDraftDao()

    val pending = dao.observePending()
    val pendingCount = dao.observePendingCount()

    /** True bila draf baru (bukan duplikat). */
    suspend fun add(draft: NotificationDraftEntity): Boolean = dao.insert(draft) != -1L

    suspend fun get(fingerprint: String) = dao.get(fingerprint)
    suspend fun markImported(fingerprint: String) = dao.setStatus(fingerprint, "IMPORTED")
    suspend fun markDiscarded(fingerprint: String) = dao.setStatus(fingerprint, "DISCARDED")
    suspend fun markPending(fingerprint: String) = dao.setStatus(fingerprint, "PENDING")

    /** Bersihkan draf yang sudah diproses lebih dari 30 hari. */
    suspend fun purgeOld() = dao.purgeHandled(System.currentTimeMillis() - 30L * 24 * 3600 * 1000)
}
