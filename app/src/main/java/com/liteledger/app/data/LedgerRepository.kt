package com.liteledger.app.data

import kotlinx.coroutines.flow.Flow

class LedgerRepository(private val dao: LedgerDao) {
    val personsWithBalances: Flow<List<PersonWithBalance>> = dao.getPersonsWithBalances()

    suspend fun addPerson(name: String) = dao.insertPerson(Person(name = name))
    suspend fun updatePerson(person: Person) = dao.updatePerson(person)
    suspend fun deletePerson(person: Person) = dao.deletePerson(person)

    suspend fun personExists(name: String): Boolean {
        return dao.countPersonsWithName(name) > 0
    }

    suspend fun addTransaction(transaction: Transaction) = dao.insertTransaction(transaction)
    suspend fun updateTransaction(transaction: Transaction) = dao.updateTransaction(transaction)
    suspend fun deleteTransaction(transaction: Transaction) = dao.deleteTransaction(transaction)

    fun getHistoryFor(personId: Long) = dao.getTransactionsForPerson(personId)

    suspend fun getAllDataForBackup(): BackupData {
        val persons = dao.getAllPersonsSnapshot()
        val transactions = dao.getAllTransactions()

        return BackupData(persons = persons, transactions = transactions)
    }

    suspend fun restoreData(backup: BackupData) {
        dao.deleteAllTransactions()
        dao.deleteAllPersons()

        if (backup.persons.isNotEmpty()) {
            dao.insertPersons(backup.persons)
        }
        if (backup.transactions.isNotEmpty()) {
            dao.insertTransactions(backup.transactions)
        }
    }
}