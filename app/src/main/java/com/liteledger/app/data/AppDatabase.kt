package com.liteledger.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

// type converter because room doesn't know how to save "enums" by default
class Converters {
    @TypeConverter
    fun fromType(value: TransactionType) = value.name

    @TypeConverter
    fun toType(value: String) = TransactionType.valueOf(value)
}

@Database(entities = [Person::class, Transaction::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): LedgerDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "liteledger_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}