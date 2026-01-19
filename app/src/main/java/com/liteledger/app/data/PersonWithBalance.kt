package com.liteledger.app.data

import androidx.room.Embedded

data class PersonWithBalance(
    @Embedded val person: Person,
    val balance: Long,
    val lastActivityAt: Long? = null
)