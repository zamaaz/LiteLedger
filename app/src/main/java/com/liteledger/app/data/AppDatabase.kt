package com.liteledger.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// type converter because room doesn't know how to save "enums" by default
class Converters {
    @TypeConverter
    fun fromType(value: TransactionType) = value.name

    @TypeConverter
    fun toType(value: String) = TransactionType.valueOf(value)
}

@Database(
    entities = [Person::class, Transaction::class, Tag::class, TransactionTagCrossRef::class, Settlement::class],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): LedgerDao
    abstract fun tagDao(): TagDao
    abstract fun settlementDao(): SettlementDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `tag` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `lastUsedAt` INTEGER NOT NULL
                    )
                """)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `transaction_tag` (
                        `transactionId` INTEGER NOT NULL,
                        `tagId` INTEGER NOT NULL,
                        PRIMARY KEY(`transactionId`, `tagId`),
                        FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON DELETE CASCADE,
                        FOREIGN KEY(`tagId`) REFERENCES `tag`(`id`) ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_tag_transactionId` ON `transaction_tag` (`transactionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_tag_tagId` ON `transaction_tag` (`tagId`)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add dueDate column to transactions table
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `dueDate` INTEGER DEFAULT NULL")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `settlement` (
                        `repaymentTxnId` INTEGER NOT NULL,
                        `targetTxnId` INTEGER NOT NULL,
                        `allocatedAmount` INTEGER NOT NULL,
                        PRIMARY KEY(`repaymentTxnId`, `targetTxnId`),
                        FOREIGN KEY(`repaymentTxnId`) REFERENCES `transactions`(`id`) ON DELETE CASCADE,
                        FOREIGN KEY(`targetTxnId`) REFERENCES `transactions`(`id`) ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_settlement_repaymentTxnId` ON `settlement` (`repaymentTxnId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_settlement_targetTxnId` ON `settlement` (`targetTxnId`)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `person` ADD COLUMN `isTemporary` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `person` ADD COLUMN `isArchived` INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "liteledger_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
