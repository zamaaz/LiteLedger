package com.liteledger.app

import android.app.Application
import com.liteledger.app.data.AppDatabase

class LiteLedgerApp : Application() {
    // we instantiate the db here so it lives as long as the app does
    val database by lazy { AppDatabase.getDatabase(this) }
}