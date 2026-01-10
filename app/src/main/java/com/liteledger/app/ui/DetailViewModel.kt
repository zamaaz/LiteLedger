package com.liteledger.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.liteledger.app.data.LedgerRepository
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

data class TransactionUiModel(
    val transaction: Transaction,
    val runningBalance: Long
)

data class DetailState(
    val items: List<DetailListItem> = emptyList(),
    val totalBalance: Long = 0,
    val rawTransactions: List<Transaction> = emptyList()
)

class DetailViewModel(
    private val repository: LedgerRepository,
    private val personId: Long
) : ViewModel() {

    private fun getMonthKey(date: Long): String = SimpleDateFormat("MMMM yyyy", Locale.US).format(Date(date))

    val state: StateFlow<DetailState> = repository.getHistoryFor(personId)
        .map { list ->
            val totalBalance = list.sumOf { if (it.type == TransactionType.GAVE) it.amount else -it.amount }

            val orderedList = list.asReversed()
            val runningBalanceList = ArrayList<TransactionUiModel>(list.size)
            var currentBalance = 0L

            orderedList.forEach { txn ->
                val impact = if (txn.type == TransactionType.GAVE) txn.amount else -txn.amount
                currentBalance += impact
                runningBalanceList.add(TransactionUiModel(txn, currentBalance))
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
                rawTransactions = list
            )
        }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DetailState()
        )

    fun addTransaction(amount: Long, type: TransactionType, note: String, date: Long) {
        viewModelScope.launch {
            repository.addTransaction(Transaction(personId = personId, amount = amount, type = type, note = note, date = date))
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch { repository.deleteTransaction(transaction) }
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch { repository.updateTransaction(transaction) }
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