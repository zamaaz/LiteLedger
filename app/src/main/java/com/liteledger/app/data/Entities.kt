package com.liteledger.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(tableName = "person")
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val mobile: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

enum class TransactionType {
    GAVE, GOT
}

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = Person::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("personId")]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personId: Long,
    val amount: Long,
    val type: TransactionType,
    val date: Long = System.currentTimeMillis(),
    val note: String = ""
)