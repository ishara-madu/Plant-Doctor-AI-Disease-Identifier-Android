package com.pixeleye.plantdoctor.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

data class UserPreferences(
    val country: String = "",
    val language: String = "",
    val selectedAiLanguage: String = "English",
    val onboardingCompleted: Boolean = false,
    val isPremium: Boolean = false
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val USER_COUNTRY = stringPreferencesKey("user_country")
        val USER_LANGUAGE = stringPreferencesKey("user_language")
        val SELECTED_AI_LANGUAGE = stringPreferencesKey("selected_ai_language")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val IS_PREMIUM = booleanPreferencesKey("is_premium")
    }

    val userPreferences: Flow<UserPreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            UserPreferences(
                country = preferences[Keys.USER_COUNTRY] ?: "",
                language = preferences[Keys.USER_LANGUAGE] ?: "",
                selectedAiLanguage = preferences[Keys.SELECTED_AI_LANGUAGE] ?: "English",
                onboardingCompleted = preferences[Keys.ONBOARDING_COMPLETED] ?: false,
                isPremium = preferences[Keys.IS_PREMIUM] ?: false
            )
        }

    suspend fun saveUserPreferences(
        country: String, 
        language: String,
        selectedAiLanguage: String,
        onboardingCompleted: Boolean
    ) {
        context.dataStore.edit { preferences ->
            preferences[Keys.USER_COUNTRY] = country
            preferences[Keys.USER_LANGUAGE] = language
            preferences[Keys.SELECTED_AI_LANGUAGE] = selectedAiLanguage
            preferences[Keys.ONBOARDING_COMPLETED] = onboardingCompleted
        }
    }

    suspend fun clearPreferences() {
        context.dataStore.edit { it.clear() }
    }
}
