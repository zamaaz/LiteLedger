package com.liteledger.app.data

import kotlinx.coroutines.flow.Flow

class LedgerRepository(
    private val dao: LedgerDao,
    private val tagDao: TagDao,
    private val settlementDao: SettlementDao
) {
    val personsWithBalances: Flow<List<PersonWithBalance>> = dao.getPersonsWithBalances()
    val archivedPersonsWithBalances: Flow<List<PersonWithBalance>> = dao.getArchivedPersonsWithBalances()

    suspend fun addPerson(name: String, isTemporary: Boolean = false) = 
        dao.insertPerson(Person(name = name, isTemporary = isTemporary))
    
    suspend fun updatePerson(person: Person) = dao.updatePerson(person)
    suspend fun deletePerson(person: Person) = dao.deletePerson(person)

    suspend fun personExists(name: String): Boolean {
        return dao.countPersonsWithName(name) > 0
    }

    // Archive operations
    suspend fun archivePerson(personId: Long) = dao.setPersonArchived(personId, true)
    suspend fun unarchivePerson(personId: Long) = dao.setPersonArchived(personId, false)

    // Auto-archive check: if person is temporary and balance is 0, auto-archive
    private suspend fun checkAutoArchive(personId: Long) {
        val person = dao.getPersonById(personId) ?: return
        if (person.isTemporary && !person.isArchived) {
            val balance = dao.getPersonBalance(personId)
            if (balance == 0L) {
                dao.setPersonArchived(personId, true)
            }
        }
    }

    suspend fun addTransaction(transaction: Transaction): Long {
        val txnId = dao.insertTransaction(transaction)
        checkAutoArchive(transaction.personId)
        return txnId
    }

    suspend fun updateTransaction(transaction: Transaction) {
        dao.updateTransaction(transaction)
        checkAutoArchive(transaction.personId)
    }

    suspend fun deleteTransaction(transaction: Transaction) {
        val personId = transaction.personId
        dao.deleteTransaction(transaction)
        checkAutoArchive(personId)
    }

    fun getHistoryFor(personId: Long) = dao.getTransactionsForPerson(personId)

    // --- Tag Operations ---
    val allTags: Flow<List<Tag>> = tagDao.getAllTags()
    val recentTags: Flow<List<Tag>> = tagDao.getRecentTags()
    val tagCount: Flow<Int> = tagDao.getTagCount()

    suspend fun addTag(name: String): Long = tagDao.insertTag(Tag(name = name))
    suspend fun updateTag(tag: Tag) = tagDao.updateTag(tag)
    suspend fun deleteTag(tag: Tag) = tagDao.deleteTag(tag)

    fun getTagsForTransaction(transactionId: Long): Flow<List<Tag>> = tagDao.getTagsForTransaction(transactionId)
    suspend fun getTagsForTransactionSnapshot(transactionId: Long): List<Tag> = tagDao.getTagsForTransactionSnapshot(transactionId)
    val transactionTags: Flow<List<TransactionTagCrossRef>> = tagDao.getAllTransactionTags()

    suspend fun setTagsForTransaction(transactionId: Long, tagIds: List<Long>) {
        tagDao.deleteTagsForTransaction(transactionId)
        tagIds.forEach { tagId ->
            tagDao.insertTransactionTagCrossRef(TransactionTagCrossRef(transactionId, tagId))
        }
        // Update lastUsedAt for used tags
        val currentTime = System.currentTimeMillis()
        tagIds.forEach { tagId ->
            tagDao.getAllTagsSnapshot().find { it.id == tagId }?.let { tag ->
                tagDao.updateTag(tag.copy(lastUsedAt = currentTime))
            }
        }
    }

    // --- Settlement Operations ---
    val allSettlements: Flow<List<Settlement>> = settlementDao.getAllSettlements()

    fun getSettlementsForRepayment(txnId: Long): Flow<List<Settlement>> =
        settlementDao.getSettlementsForRepayment(txnId)

    suspend fun getSettlementsForRepaymentSnapshot(txnId: Long): List<Settlement> =
        settlementDao.getSettlementsForRepaymentSnapshot(txnId)

    suspend fun getSettledAmountForTarget(txnId: Long): Long =
        settlementDao.getSettledAmountForTarget(txnId)

    suspend fun getTotalAllocatedByRepayment(txnId: Long): Long =
        settlementDao.getTotalAllocatedByRepayment(txnId)

    /**
     * Set settlements for a repayment transaction.
     * @param repaymentTxnId The ID of the repayment transaction
     * @param settlements List of (targetTxnId, allocatedAmount) pairs
     */
    suspend fun setSettlementsForTransaction(repaymentTxnId: Long, settlements: List<Pair<Long, Long>>) {
        settlementDao.deleteSettlementsForRepayment(repaymentTxnId)
        if (settlements.isNotEmpty()) {
            val settlementEntities = settlements.map { (targetId, amount) ->
                Settlement(repaymentTxnId, targetId, amount)
            }
            settlementDao.insertSettlements(settlementEntities)
        }
    }

    /**
     * Add a transaction with settlements in one operation.
     * @return The ID of the newly created transaction
     */
    suspend fun addTransactionWithSettlements(
        transaction: Transaction,
        settlements: List<Pair<Long, Long>>,
        tagIds: List<Long>
    ): Long {
        val txnId = dao.insertTransaction(transaction)
        if (tagIds.isNotEmpty()) {
            setTagsForTransaction(txnId, tagIds)
        }
        if (settlements.isNotEmpty()) {
            setSettlementsForTransaction(txnId, settlements)
        }
        checkAutoArchive(transaction.personId)
        return txnId
    }

    // --- Backup/Restore ---
    suspend fun getAllDataForBackup(): BackupData {
        val persons = dao.getAllPersonsSnapshot()
        val transactions = dao.getAllTransactions()
        val tags = tagDao.getAllTagsSnapshot()
        val transactionTags = tagDao.getAllTransactionTagsSnapshot()
        val settlements = settlementDao.getAllSettlementsSnapshot()

        return BackupData(
            persons = persons,
            transactions = transactions,
            tags = tags,
            transactionTags = transactionTags,
            settlements = settlements
        )
    }

    suspend fun restoreData(backup: BackupData) {
        // Clear all data (settlements cascade with transactions)
        settlementDao.deleteAllSettlements()
        tagDao.deleteAllTransactionTags()
        tagDao.deleteAllTags()
        dao.deleteAllTransactions()
        dao.deleteAllPersons()

        // Restore in order
        if (backup.persons.isNotEmpty()) {
            dao.insertPersons(backup.persons)
        }
        if (backup.transactions.isNotEmpty()) {
            dao.insertTransactions(backup.transactions)
        }
        if (backup.tags.isNotEmpty()) {
            tagDao.insertTags(backup.tags)
        }
        if (backup.transactionTags.isNotEmpty()) {
            tagDao.insertTransactionTags(backup.transactionTags)
        }
        if (backup.settlements.isNotEmpty()) {
            settlementDao.insertSettlementsBulk(backup.settlements)
        }
    }
}

