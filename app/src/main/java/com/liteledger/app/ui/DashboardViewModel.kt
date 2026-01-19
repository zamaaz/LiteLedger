package com.liteledger.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.liteledger.app.data.LedgerRepository
import com.liteledger.app.data.PersonWithBalance
import com.liteledger.app.data.SortOption
import com.liteledger.app.data.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlin.math.abs

data class DashboardState(
    val people: List<PersonWithBalance> = emptyList(),
    val totalReceive: Long = 0,
    val totalPay: Long = 0
)

class DashboardViewModel(
    private val repository: LedgerRepository,
    private val userPrefs: UserPreferencesRepository
) : ViewModel() {

    // Search State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    // Sort State
    private val _sortOption = MutableStateFlow(SortOption.RECENT_ACTIVITY)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    init {
        viewModelScope.launch {
            userPrefs.sortOptionFlow.collect { _sortOption.value = it }
        }
    }

    // Combine the DB data with Search Query and Sort Option
    val state: StateFlow<DashboardState> = combine(
        repository.personsWithBalances,
        _searchQuery,
        _sortOption
    ) { list, query, sort ->
        val totalReceive = list.filter { it.balance > 0 }.sumOf { it.balance }
        val totalPay = list.filter { it.balance < 0 }.sumOf { it.balance }

        val filteredList = if (query.isBlank()) {
            list
        } else {
            list.filter { it.person.name.contains(query, ignoreCase = true) }
        }

        val sortedList = sortPeople(filteredList, sort)

        DashboardState(
            people = sortedList,
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

    private fun sortPeople(list: List<PersonWithBalance>, option: SortOption): List<PersonWithBalance> {
        return when (option) {
            SortOption.RECENT_ACTIVITY -> list.sortedWith(
                compareByDescending<PersonWithBalance> { it.lastActivityAt ?: Long.MIN_VALUE }
                    .thenBy { it.person.name.lowercase() }
            )
            SortOption.OLDEST_ACTIVITY -> list.sortedWith(
                compareBy<PersonWithBalance> { it.lastActivityAt ?: Long.MAX_VALUE }
                    .thenBy { it.person.name.lowercase() }
            )
            SortOption.HIGHEST_AMOUNT -> list.sortedWith(
                compareByDescending<PersonWithBalance> { abs(it.balance) }
                    .thenByDescending { it.lastActivityAt ?: Long.MIN_VALUE }
            )
            SortOption.LOWEST_AMOUNT -> list.sortedWith(
                compareBy<PersonWithBalance> { abs(it.balance) }
                    .thenByDescending { it.lastActivityAt ?: Long.MIN_VALUE }
            )
            SortOption.NAME_AZ -> list.sortedWith(
                compareBy { it.person.name.lowercase() }
            )
            SortOption.UNSETTLED_FIRST -> list.sortedWith(
                compareByDescending<PersonWithBalance> { it.balance != 0L }
                    .thenByDescending { it.lastActivityAt ?: Long.MIN_VALUE }
                    .thenBy { it.person.name.lowercase() }
            )
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun setSortOption(option: SortOption) {
        viewModelScope.launch {
            _sortOption.value = option
            userPrefs.setSortOption(option)
        }
    }

    suspend fun validatePersonName(name: String): Boolean {
        return repository.personExists(name.trim())
    }

    fun addPerson(name: String, isTemporary: Boolean = false) {
        viewModelScope.launch { repository.addPerson(name.trim(), isTemporary) }
    }

    fun renamePerson(personId: Long, newName: String) {
        viewModelScope.launch {
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

class DashboardViewModelFactory(
    private val repository: LedgerRepository,
    private val userPrefs: UserPreferencesRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return DashboardViewModel(repository, userPrefs) as T
    }
}
