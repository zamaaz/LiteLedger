package com.liteledger.app.data

import com.google.gson.annotations.SerializedName

data class BackupData(
    @SerializedName("version") val version: Int = 3,
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis(),
    @SerializedName("persons") val persons: List<Person>,
    @SerializedName("transactions") val transactions: List<Transaction>,
    @SerializedName("tags") val tags: List<Tag> = emptyList(),
    @SerializedName("transactionTags") val transactionTags: List<TransactionTagCrossRef> = emptyList(),
    @SerializedName("settlements") val settlements: List<Settlement> = emptyList()
)

