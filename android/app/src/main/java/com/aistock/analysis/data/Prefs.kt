package com.aistock.analysis.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "aistock_prefs")

class Prefs(private val context: Context) {
    private val KEY_DEVICE_ID = stringPreferencesKey("device_id")
    private val KEY_SESSION_TOKEN = stringPreferencesKey("session_token")
    private val KEY_USER_EMAIL = stringPreferencesKey("user_email")
    private val KEY_USER_NAME = stringPreferencesKey("user_name")
    private val KEY_USER_PICTURE = stringPreferencesKey("user_picture")

    val session: Flow<Session> = context.dataStore.data.map {
        Session(
            token = it[KEY_SESSION_TOKEN],
            email = it[KEY_USER_EMAIL],
            name = it[KEY_USER_NAME],
            picture = it[KEY_USER_PICTURE],
        )
    }

    suspend fun deviceId(): String {
        val existing = context.dataStore.data.first()[KEY_DEVICE_ID]
        if (!existing.isNullOrBlank()) return existing
        val fresh = "and_" + UUID.randomUUID().toString().replace("-", "")
        context.dataStore.edit { it[KEY_DEVICE_ID] = fresh }
        return fresh
    }

    suspend fun setSession(token: String, email: String, name: String?, picture: String?) {
        context.dataStore.edit {
            it[KEY_SESSION_TOKEN] = token
            it[KEY_USER_EMAIL] = email
            if (name != null) it[KEY_USER_NAME] = name else it.remove(KEY_USER_NAME)
            if (picture != null) it[KEY_USER_PICTURE] = picture else it.remove(KEY_USER_PICTURE)
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit {
            it.remove(KEY_SESSION_TOKEN)
            it.remove(KEY_USER_EMAIL)
            it.remove(KEY_USER_NAME)
            it.remove(KEY_USER_PICTURE)
        }
    }

    suspend fun currentToken(): String? = context.dataStore.data.first()[KEY_SESSION_TOKEN]
}

data class Session(
    val token: String?,
    val email: String?,
    val name: String?,
    val picture: String?,
) {
    val isSignedIn: Boolean get() = !token.isNullOrBlank()
}
