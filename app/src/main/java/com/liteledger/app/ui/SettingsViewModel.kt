package com.liteledger.app.ui

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.liteledger.app.data.AppTheme
import com.liteledger.app.data.LedgerRepository
import com.liteledger.app.data.UserPreferencesRepository
import com.liteledger.app.utils.BackupHelper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsState(
    val theme: AppTheme = AppTheme.SYSTEM,
    val hapticsEnabled: Boolean = true,
    val biometricEnabled: Boolean = false,
    val isPrivacyEnabled: Boolean = false, // <--- ADDED THIS
    val isLoading: Boolean = true
)

class SettingsViewModel(
    application: Application,
    private val userPrefs: UserPreferencesRepository,
    private val repository: LedgerRepository
) : AndroidViewModel(application) {

    private val backupHelper = BackupHelper(application, repository)

    // Combine flows to create the UI state
    val state: StateFlow<SettingsState> = combine(
        userPrefs.themeFlow,
        userPrefs.hapticsFlow,
        userPrefs.biometricFlow,
        userPrefs.privacyFlow // <--- ADDED THIS (Requires update in Repo)
    ) { theme, haptics, bio, privacy ->
        SettingsState(
            theme = theme,
            hapticsEnabled = haptics,
            biometricEnabled = bio,
            isPrivacyEnabled = privacy, // <--- Mapped here
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsState()
    )

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch { userPrefs.setTheme(theme) }
    }

    fun setHaptics(enabled: Boolean) {
        viewModelScope.launch { userPrefs.setHaptics(enabled) }
    }

    fun setBiometric(enabled: Boolean) {
        viewModelScope.launch { userPrefs.setBiometric(enabled) }
    }

    // <--- ADDED THIS FUNCTION
    fun setPrivacy(enabled: Boolean) {
        viewModelScope.launch { userPrefs.setPrivacy(enabled) }
    }

    // --- BACKUP / RESTORE ---

    fun onBackupSelected(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val result = backupHelper.performBackup(uri)
            showToast(result.getOrDefault("Backup Failed"))
        }
    }

    fun onRestoreSelected(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val result = backupHelper.performRestore(uri)
            showToast(result.getOrDefault("Restore Failed"))
        }
    }

    fun exportData(context: android.content.Context) {
        viewModelScope.launch {
            // Fetch latest data
            val peopleWithBalances = repository.personsWithBalances.first()
            val backupData = repository.getAllDataForBackup() // Reusing this to get all transactions

            // Generate CSV
            com.liteledger.app.utils.CsvExporter.exportData(context, peopleWithBalances, backupData.transactions)
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(getApplication(), message, Toast.LENGTH_SHORT).show()
    }
}

class SettingsViewModelFactory(
    private val application: Application,
    private val userPrefs: UserPreferencesRepository,
    private val repository: LedgerRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return SettingsViewModel(application, userPrefs, repository) as T
    }
}