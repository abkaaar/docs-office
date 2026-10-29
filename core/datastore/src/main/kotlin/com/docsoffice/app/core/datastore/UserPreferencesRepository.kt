package com.docsoffice.app.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class SortMode {
    DATE,
    NAME,
    SIZE,
}

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val onboardingCompleted = booleanPreferencesKey("onboarding_completed")
        val themeMode = stringPreferencesKey("theme_mode")
        val excludedFolders = stringSetPreferencesKey("excluded_folders")
        val safTreeUris = stringSetPreferencesKey("saf_tree_uris")
        val defaultSort = stringPreferencesKey("default_sort")
        val trashRetentionDays = intPreferencesKey("trash_retention_days")
        val analyticsEnabled = booleanPreferencesKey("analytics_enabled")
        val readerTheme = stringPreferencesKey("reader_theme")
    }

    val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.onboardingCompleted] ?: false
    }

    val themeMode: Flow<AppThemeMode> = context.dataStore.data.map { prefs ->
        runCatching {
            AppThemeMode.valueOf(prefs[Keys.themeMode] ?: AppThemeMode.SYSTEM.name)
        }.getOrDefault(AppThemeMode.SYSTEM)
    }

    val excludedFolders: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[Keys.excludedFolders] ?: emptySet()
    }

    val safTreeUris: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[Keys.safTreeUris] ?: emptySet()
    }

    val defaultSort: Flow<SortMode> = context.dataStore.data.map { prefs ->
        runCatching {
            SortMode.valueOf(prefs[Keys.defaultSort] ?: SortMode.DATE.name)
        }.getOrDefault(SortMode.DATE)
    }

    val trashRetentionDays: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.trashRetentionDays] ?: 30
    }

    val analyticsEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.analyticsEnabled] ?: false
    }

    val readerTheme: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.readerTheme] ?: "light"
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[Keys.onboardingCompleted] = completed }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { it[Keys.themeMode] = mode.name }
    }

    suspend fun setExcludedFolders(folders: Set<String>) {
        context.dataStore.edit { it[Keys.excludedFolders] = folders }
    }

    suspend fun addExcludedFolder(path: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.excludedFolders] ?: emptySet()
            prefs[Keys.excludedFolders] = current + path
        }
    }

    suspend fun addSafTreeUri(uri: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.safTreeUris] ?: emptySet()
            prefs[Keys.safTreeUris] = current + uri
        }
    }

    suspend fun setDefaultSort(sort: SortMode) {
        context.dataStore.edit { it[Keys.defaultSort] = sort.name }
    }

    suspend fun setTrashRetentionDays(days: Int) {
        context.dataStore.edit { it[Keys.trashRetentionDays] = days.coerceIn(1, 90) }
    }

    suspend fun setAnalyticsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.analyticsEnabled] = enabled }
    }

    suspend fun setReaderTheme(theme: String) {
        context.dataStore.edit { it[Keys.readerTheme] = theme }
    }
}
