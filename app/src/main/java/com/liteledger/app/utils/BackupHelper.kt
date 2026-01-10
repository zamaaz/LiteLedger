package com.liteledger.app.utils

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.liteledger.app.data.BackupData
import com.liteledger.app.data.LedgerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class BackupHelper(
    private val context: Context,
    private val repository: LedgerRepository
) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun performBackup(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val backupData = repository.getAllDataForBackup()
            val jsonString = gson.toJson(backupData)

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(jsonString)
                }
            }
            Result.success("Backup saved successfully")
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun performRestore(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val jsonString = StringBuilder()
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    var line = reader.readLine()
                    while (line != null) {
                        jsonString.append(line)
                        line = reader.readLine()
                    }
                }
            }

            val backupData = gson.fromJson(jsonString.toString(), BackupData::class.java)

            // Validation
            if (backupData.persons == null || backupData.transactions == null) {
                return@withContext Result.failure(Exception("Invalid backup file"))
            }

            repository.restoreData(backupData)
            Result.success("Data restored successfully")
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}