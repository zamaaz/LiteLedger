package com.liteledger.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.liteledger.app.data.PersonWithBalance

@Dao
interface LedgerDao {
    @Query("""
        SELECT 
            p.*, 
            COALESCE(SUM(CASE WHEN t.type = 'GAVE' THEN t.amount ELSE -t.amount END), 0) as balance
        FROM person p
        LEFT JOIN `transactions` t ON p.id = t.personId
        GROUP BY p.id
        ORDER BY MAX(t.date) DESC, p.id DESC
    """)
    fun getPersonsWithBalances(): Flow<List<PersonWithBalance>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPerson(person: Person): Long

    @Update
    suspend fun updatePerson(person: Person)

    @Delete
    suspend fun deletePerson(person: Person)

    @Query("SELECT * FROM person ORDER BY id DESC")
    fun getAllPersons(): Flow<List<Person>>

    @Query("SELECT * FROM `transactions` WHERE personId = :personId ORDER BY date DESC")
    fun getTransactionsForPerson(personId: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM `transactions`") suspend fun getAllTransactions(): List<Transaction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction)

    @Delete
    suspend fun deleteTransaction(transaction: Transaction)

    @Update
    suspend fun updateTransaction(transaction: Transaction)

    @Query("SELECT * FROM person")
    suspend fun getAllPersonsSnapshot(): List<Person>

    @Query("DELETE FROM person")
    suspend fun deleteAllPersons()

    @Query("DELETE FROM `transactions`")
    suspend fun deleteAllTransactions()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPersons(persons: List<Person>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<Transaction>)

    @Query("SELECT COUNT(*) FROM person WHERE name = :name COLLATE NOCASE")
    suspend fun countPersonsWithName(name: String): Int
}