package com.liteledger.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Enum for Theme Options
enum class AppTheme { LIGHT, DARK, SYSTEM }

// Extension property for DataStore
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("app_theme")
        val HAPTICS = booleanPreferencesKey("haptics_enabled")
        val BIOMETRIC = booleanPreferencesKey("biometric_enabled")
        val PRIVACY = booleanPreferencesKey("privacy_mode")
    }

    // --- READ STREAMS ---
    val themeFlow: Flow<AppTheme> = context.dataStore.data.map { prefs ->
        AppTheme.valueOf(prefs[Keys.THEME] ?: AppTheme.SYSTEM.name)
    }

    val hapticsFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.HAPTICS] ?: true // Default True
    }

    val biometricFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.BIOMETRIC] ?: false // Default False
    }

    val privacyFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.PRIVACY] ?: false
    }

    suspend fun setPrivacy(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PRIVACY] = enabled }
    }

    // --- WRITE ACTIONS ---
    suspend fun setTheme(theme: AppTheme) {
        context.dataStore.edit { it[Keys.THEME] = theme.name }
    }

    suspend fun setHaptics(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HAPTICS] = enabled }
    }

    suspend fun setBiometric(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BIOMETRIC] = enabled }
    }
}