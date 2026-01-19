package com.liteledger.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "settlement",
    primaryKeys = ["repaymentTxnId", "targetTxnId"],
    foreignKeys = [
        ForeignKey(
            entity = Transaction::class,
            parentColumns = ["id"],
            childColumns = ["repaymentTxnId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Transaction::class,
            parentColumns = ["id"],
            childColumns = ["targetTxnId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("repaymentTxnId"), Index("targetTxnId")]
)
data class Settlement(
    val repaymentTxnId: Long,  // The txn doing the settling (e.g., "GOT" settling "GAVE"s)
    val targetTxnId: Long,     // The txn being settled
    val allocatedAmount: Long  // Amount allocated from repayment to this target (in paise)
)

@Dao
interface SettlementDao {
    // Get all settlements for a repayment transaction (what this repayment settles)
    @Query("SELECT * FROM settlement WHERE repaymentTxnId = :txnId")
    fun getSettlementsForRepayment(txnId: Long): Flow<List<Settlement>>

    // Snapshot version for immediate access
    @Query("SELECT * FROM settlement WHERE repaymentTxnId = :txnId")
    suspend fun getSettlementsForRepaymentSnapshot(txnId: Long): List<Settlement>

    // Get total settled amount for a target transaction
    @Query("SELECT COALESCE(SUM(allocatedAmount), 0) FROM settlement WHERE targetTxnId = :txnId")
    suspend fun getSettledAmountForTarget(txnId: Long): Long

    // Get all settlements where this txn is settled by others
    @Query("SELECT * FROM settlement WHERE targetTxnId = :txnId")
    fun getSettlementsSettlingTarget(txnId: Long): Flow<List<Settlement>>

    // Get total amount this repayment has allocated (to prevent over-allocation)
    @Query("SELECT COALESCE(SUM(allocatedAmount), 0) FROM settlement WHERE repaymentTxnId = :txnId")
    suspend fun getTotalAllocatedByRepayment(txnId: Long): Long

    // Insert settlements
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettlements(settlements: List<Settlement>)

    // Insert single settlement
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettlement(settlement: Settlement)

    // Delete all settlements for a repayment (used when updating settlements)
    @Query("DELETE FROM settlement WHERE repaymentTxnId = :txnId")
    suspend fun deleteSettlementsForRepayment(txnId: Long)

    // Get all settlements (for Flow-based reactive updates)
    @Query("SELECT * FROM settlement")
    fun getAllSettlements(): Flow<List<Settlement>>

    // Snapshot for backup
    @Query("SELECT * FROM settlement")
    suspend fun getAllSettlementsSnapshot(): List<Settlement>

    // Delete all for restore
    @Query("DELETE FROM settlement")
    suspend fun deleteAllSettlements()

    // Bulk insert for restore
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettlementsBulk(settlements: List<Settlement>)
}
