package com.liteledger.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.liteledger.app.data.LedgerRepository
import com.liteledger.app.data.Settlement
import com.liteledger.app.data.Tag
import com.liteledger.app.data.Transaction
import com.liteledger.app.data.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface DetailListItem {
    data class Header(val title: String, val monthTotal: Long) : DetailListItem
    data class Item(val uiModel: TransactionUiModel) : DetailListItem
}

enum class SettlementStatus { OPEN, PARTIAL, SETTLED }

data class TransactionUiModel(
    val transaction: Transaction,
    val runningBalance: Long,
    val tags: List<Tag> = emptyList(),
    val settlementStatus: SettlementStatus = SettlementStatus.OPEN,
    val settledAmount: Long = 0,      // How much has been settled (for target txns)
    val settlesAmount: Long = 0       // How much this txn settles (for repayment txns)
)

data class DetailState(
    val items: List<DetailListItem> = emptyList(),
    val totalBalance: Long = 0,
    val rawTransactions: List<Transaction> = emptyList(),
    val uiModels: List<TransactionUiModel> = emptyList()  // Exposed for smart statement
)

// Smart Statement PDF generation models
enum class SmartStatementMode { UNSETTLED, SETTLED }

data class SmartStatementData(
    val mode: SmartStatementMode,
    val transactions: List<TransactionUiModel>,
    val totalBalance: Long
)

class DetailViewModel(
    private val repository: LedgerRepository,
    private val personId: Long
) : ViewModel() {

    private fun getMonthKey(date: Long): String = SimpleDateFormat("MMMM yyyy", Locale.US).format(Date(date))

    // Expose tags for the picker
    val allTags: StateFlow<List<Tag>> = repository.allTags
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentTags: StateFlow<List<Tag>> = repository.recentTags
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val state: StateFlow<DetailState> = kotlinx.coroutines.flow.combine(
        repository.getHistoryFor(personId),
        repository.transactionTags,
        repository.allSettlements
    ) { list, _, settlements ->
        val totalBalance = list.sumOf { if (it.type == TransactionType.GAVE) it.amount else -it.amount }

        // Pre-compute settlement totals for efficiency
        val settledByTarget = settlements.groupBy { it.targetTxnId }
            .mapValues { (_, setts) -> setts.sumOf { it.allocatedAmount } }
        val settlesByRepayment = settlements.groupBy { it.repaymentTxnId }
            .mapValues { (_, setts) -> setts.sumOf { it.allocatedAmount } }

        val orderedList = list.asReversed()
        val runningBalanceList = ArrayList<TransactionUiModel>(list.size)
        var currentBalance = 0L

        orderedList.forEach { txn ->
            val impact = if (txn.type == TransactionType.GAVE) txn.amount else -txn.amount
            currentBalance += impact
            // Fetch tags synchronously for each transaction
            val tags = repository.getTagsForTransactionSnapshot(txn.id)
            
            // Calculate settlement status
            val settledAmount = settledByTarget[txn.id] ?: 0L
            val settlesAmount = settlesByRepayment[txn.id] ?: 0L
            
            val settlementStatus = when {
                settledAmount >= txn.amount -> SettlementStatus.SETTLED
                settledAmount > 0 -> SettlementStatus.PARTIAL
                else -> SettlementStatus.OPEN
            }
            
            runningBalanceList.add(TransactionUiModel(
                transaction = txn, 
                runningBalance = currentBalance, 
                tags = tags,
                settlementStatus = settlementStatus,
                settledAmount = settledAmount,
                settlesAmount = settlesAmount
            ))
        }

        val uiModels = runningBalanceList.asReversed()

        val displayItems = ArrayList<DetailListItem>()

        val groupedMap = uiModels.groupBy { getMonthKey(it.transaction.date) }

        groupedMap.forEach { (month, transactions) ->
            val monthlyNet = transactions.sumOf {
                val t = it.transaction
                if (t.type == TransactionType.GAVE) t.amount else -t.amount
            }

            displayItems.add(DetailListItem.Header(month, monthlyNet))

            transactions.forEach { displayItems.add(DetailListItem.Item(it)) }
        }

        DetailState(
            items = displayItems,
            totalBalance = totalBalance,
            rawTransactions = list,
            uiModels = uiModels
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DetailState()
        )

    /**
     * Get eligible transactions for settlement.
     * Returns opposite-type transactions that are either OPEN or PARTIAL.
     */
    fun getEligibleSettlementTargets(txnType: TransactionType): List<TransactionUiModel> {
        val oppositeType = if (txnType == TransactionType.GAVE) TransactionType.GOT else TransactionType.GAVE
        return state.value.items
            .filterIsInstance<DetailListItem.Item>()
            .map { it.uiModel }
            .filter { it.transaction.type == oppositeType && it.settlementStatus != SettlementStatus.SETTLED }
            .sortedByDescending { it.transaction.date } // Latest first
    }
    
    /**
     * Get existing settlements for a repayment transaction (for editing)
     */
    suspend fun getSettlementsForRepayment(txnId: Long): List<Settlement> {
        return repository.getSettlementsForRepaymentSnapshot(txnId)
    }

    /**
     * Generate smart statement data for PDF export.
     * - UNSETTLED: Shows transactions representing outstanding debts:
     *   - GAVE that is OPEN or PARTIAL (someone owes me)
     *   - GOT that is NOT linked to settle any GAVE (I owe someone)
     * - SETTLED: No outstanding debts → show last 10 of any type
     */
    fun getSmartStatementData(): SmartStatementData {
        val currentState = state.value
        val uiModels = currentState.uiModels
        val totalBalance = currentState.totalBalance

        // Filter for unsettled transactions:
        // 1. GAVE transactions that are OPEN or PARTIAL (money owed TO me)
        // 2. GOT transactions NOT linked as repayment (money I OWE - settlesAmount = 0)
        val unsettledTransactions = uiModels.filter { model ->
            val txn = model.transaction
            when (txn.type) {
                TransactionType.GAVE -> {
                    // GAVE is unsettled if OPEN or PARTIAL
                    model.settlementStatus == SettlementStatus.OPEN || 
                    model.settlementStatus == SettlementStatus.PARTIAL
                }
                TransactionType.GOT -> {
                    // GOT is unsettled if NOT linked to settle any GAVE (not a repayment)
                    model.settlesAmount == 0L
                }
            }
        }

        return if (unsettledTransactions.isNotEmpty()) {
            // UNSETTLED: Show outstanding debts (both directions)
            SmartStatementData(
                mode = SmartStatementMode.UNSETTLED,
                transactions = unsettledTransactions,
                totalBalance = totalBalance
            )
        } else {
            // SETTLED: All debts are cleared, show last 10 as reference
            SmartStatementData(
                mode = SmartStatementMode.SETTLED,
                transactions = uiModels.take(10),
                totalBalance = totalBalance
            )
        }
    }

    fun addTransaction(amount: Long, type: TransactionType, note: String, date: Long, dueDate: Long? = null, tagIds: List<Long> = emptyList()) {
        viewModelScope.launch {
            val txnId = repository.addTransaction(Transaction(personId = personId, amount = amount, type = type, note = note, date = date, dueDate = dueDate))
            if (tagIds.isNotEmpty()) {
                repository.setTagsForTransaction(txnId, tagIds)
            }
        }
    }
    
    /**
     * Add transaction with settlements
     * @param settlements List of (targetTxnId, allocatedAmount) pairs
     */
    fun addTransactionWithSettlements(
        amount: Long, 
        type: TransactionType, 
        note: String, 
        date: Long, 
        dueDate: Long? = null, 
        tagIds: List<Long> = emptyList(),
        settlements: List<Pair<Long, Long>> = emptyList()
    ) {
        viewModelScope.launch {
            val txn = Transaction(personId = personId, amount = amount, type = type, note = note, date = date, dueDate = dueDate)
            repository.addTransactionWithSettlements(txn, settlements, tagIds)
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch { repository.deleteTransaction(transaction) }
    }

    fun updateTransaction(transaction: Transaction, tagIds: List<Long>? = null) {
        viewModelScope.launch {
            repository.updateTransaction(transaction)
            if (tagIds != null) {
                repository.setTagsForTransaction(transaction.id, tagIds)
            }
        }
    }
    
    /**
     * Update settlements for an existing repayment transaction
     */
    fun updateSettlements(txnId: Long, settlements: List<Pair<Long, Long>>) {
        viewModelScope.launch {
            repository.setSettlementsForTransaction(txnId, settlements)
        }
    }

    fun createTag(name: String) {
        viewModelScope.launch {
            repository.addTag(name)
        }
    }
}

class DetailViewModelFactory(
    private val repository: LedgerRepository,
    private val personId: Long
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return DetailViewModel(repository, personId) as T
    }
}

