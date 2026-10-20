package com.aamnnr.duitku.data.local

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ProfileEntity::class,
        WalletEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        SettingEntity::class,
        BudgetEntity::class,
        SavingGoalEntity::class,
        SavingLogEntity::class,
        NotificationDraftEntity::class,
    ],
    version = 4,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4)],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun walletDao(): WalletDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
    abstract fun settingDao(): SettingDao
    abstract fun syncDao(): SyncDao
    abstract fun planDao(): PlanDao
    abstract fun notificationDraftDao(): NotificationDraftDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "duitku.db").build()
    }
}
