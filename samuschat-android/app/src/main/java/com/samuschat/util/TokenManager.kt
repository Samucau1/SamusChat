package com.samuschat.util

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.catch
import java.io.IOException

private val Context.authStore by preferencesDataStore("auth_prefs")
data class Session(val token: String, val email: String)
class TokenManager(context: Context) {
    private val store = context.applicationContext.authStore
    private val tokenKey = stringPreferencesKey("jwt_token")
    private val emailKey = stringPreferencesKey("user_email")
    val session = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { prefs ->
        prefs[tokenKey]?.takeIf { it.isNotBlank() }?.let { Session(it, prefs[emailKey].orEmpty()) }
    }
    suspend fun getToken() = session.first()?.token
    suspend fun authorization() = "Bearer " + (getToken() ?: error("Entre novamente na sua conta"))
    suspend fun saveSession(token: String, email: String) {
        require(token.isNotBlank())
        store.edit { it[tokenKey] = token; it[emailKey] = email }
    }
    suspend fun clearSession() { store.edit { it.clear() } }
}
