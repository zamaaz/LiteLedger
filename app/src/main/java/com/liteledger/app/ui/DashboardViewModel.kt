package com.liteledger.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.liteledger.app.data.LedgerRepository
import com.liteledger.app.data.PersonWithBalance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn

data class DashboardState(
    val people: List<PersonWithBalance> = emptyList(),
    val totalReceive: Long = 0,
    val totalPay: Long = 0
)

class DashboardViewModel(private val repository: LedgerRepository) : ViewModel() {

    // 1. New Search State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // 2. Combine the DB data with the Search Query
    val state: StateFlow<DashboardState> = combine(
        repository.personsWithBalances,
        _searchQuery
    ) { list, query ->
        // this calculation now runs on a background thread
        val totalReceive = list.filter { it.balance > 0 }.sumOf { it.balance }
        val totalPay = list.filter { it.balance < 0 }.sumOf { it.balance }

        val filteredList = if (query.isBlank()) {
            list
        } else {
            list.filter { it.person.name.contains(query, ignoreCase = true) }
        }

        DashboardState(
            people = filteredList,
            totalReceive = totalReceive,
            totalPay = totalPay
        )
    }
    .flowOn(Dispatchers.Default)
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardState()
    )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    suspend fun validatePersonName(name: String): Boolean {
        return repository.personExists(name.trim())
    }

    fun addPerson(name: String) {
        viewModelScope.launch { repository.addPerson(name.trim()) }
    }

    fun renamePerson(personId: Long, newName: String) {
        viewModelScope.launch {
            // Logic remains the same: find in the current filtered list is fine,
            // as you can only rename someone you can see.
            val currentPerson = state.value.people.find { it.person.id == personId }?.person
            if (currentPerson != null) {
                repository.updatePerson(currentPerson.copy(name = newName))
            }
        }
    }

    fun deletePerson(personId: Long) {
        viewModelScope.launch {
            val personToDelete = state.value.people.find { it.person.id == personId }?.person
            if (personToDelete != null) {
                repository.deletePerson(personToDelete)
            }
        }
    }
}

class DashboardViewModelFactory(private val repository: LedgerRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return DashboardViewModel(repository) as T
    }
}