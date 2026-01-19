package com.liteledger.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.liteledger.app.data.PersonWithBalance

@Dao
interface LedgerDao {
    // Active people only (not archived)
    @Query("""
        SELECT 
            p.*, 
            COALESCE(SUM(CASE WHEN t.type = 'GAVE' THEN t.amount ELSE -t.amount END), 0) as balance,
            MAX(t.date) as lastActivityAt
        FROM person p
        LEFT JOIN `transactions` t ON p.id = t.personId
        WHERE p.isArchived = 0
        GROUP BY p.id
        ORDER BY MAX(t.date) DESC, p.id DESC
    """)
    fun getPersonsWithBalances(): Flow<List<PersonWithBalance>>

    // Archived people only
    @Query("""
        SELECT 
            p.*, 
            COALESCE(SUM(CASE WHEN t.type = 'GAVE' THEN t.amount ELSE -t.amount END), 0) as balance,
            MAX(t.date) as lastActivityAt
        FROM person p
        LEFT JOIN `transactions` t ON p.id = t.personId
        WHERE p.isArchived = 1
        GROUP BY p.id
        ORDER BY MAX(t.date) DESC, p.id DESC
    """)
    fun getArchivedPersonsWithBalances(): Flow<List<PersonWithBalance>>

    // Get person by ID for auto-archive check
    @Query("SELECT * FROM person WHERE id = :id")
    suspend fun getPersonById(id: Long): Person?

    // Archive/unarchive
    @Query("UPDATE person SET isArchived = :archived WHERE id = :id")
    suspend fun setPersonArchived(id: Long, archived: Boolean)

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
    suspend fun insertTransaction(transaction: Transaction): Long

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

    // Get balance for a person (for auto-archive check)
    @Query("""
        SELECT COALESCE(SUM(CASE WHEN type = 'GAVE' THEN amount ELSE -amount END), 0)
        FROM `transactions` WHERE personId = :personId
    """)
    suspend fun getPersonBalance(personId: Long): Long
}