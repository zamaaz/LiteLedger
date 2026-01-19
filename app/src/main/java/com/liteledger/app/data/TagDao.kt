package com.liteledger.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT * FROM tag ORDER BY lastUsedAt DESC")
    fun getAllTags(): Flow<List<Tag>>

    @Query("SELECT * FROM tag ORDER BY lastUsedAt DESC LIMIT 5")
    fun getRecentTags(): Flow<List<Tag>>

    @Query("SELECT COUNT(*) FROM tag")
    fun getTagCount(): Flow<Int>

    @Query("SELECT * FROM tag")
    suspend fun getAllTagsSnapshot(): List<Tag>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTag(tag: Tag): Long

    @Update
    suspend fun updateTag(tag: Tag)

    @Delete
    suspend fun deleteTag(tag: Tag)

    @Query("SELECT t.* FROM tag t INNER JOIN transaction_tag tt ON t.id = tt.tagId WHERE tt.transactionId = :transactionId")
    fun getTagsForTransaction(transactionId: Long): Flow<List<Tag>>

    @Query("SELECT t.* FROM tag t INNER JOIN transaction_tag tt ON t.id = tt.tagId WHERE tt.transactionId = :transactionId")
    suspend fun getTagsForTransactionSnapshot(transactionId: Long): List<Tag>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransactionTagCrossRef(crossRef: TransactionTagCrossRef)

    @Query("DELETE FROM transaction_tag WHERE transactionId = :transactionId")
    suspend fun deleteTagsForTransaction(transactionId: Long)

    @Query("SELECT * FROM transaction_tag")
    fun getAllTransactionTags(): Flow<List<TransactionTagCrossRef>>

    @Query("SELECT * FROM transaction_tag")
    suspend fun getAllTransactionTagsSnapshot(): List<TransactionTagCrossRef>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransactionTags(crossRefs: List<TransactionTagCrossRef>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTags(tags: List<Tag>)

    @Query("DELETE FROM tag")
    suspend fun deleteAllTags()

    @Query("DELETE FROM transaction_tag")
    suspend fun deleteAllTransactionTags()
}
