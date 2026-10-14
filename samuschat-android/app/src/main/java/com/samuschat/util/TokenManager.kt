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
data class Session(val token: String, val email: String, val refreshToken: String? = null)
class TokenManager(context: Context) {
    private val store = context.applicationContext.authStore
    private val tokenKey = stringPreferencesKey("jwt_token")
    private val refreshKey = stringPreferencesKey("refresh_token")
    private val emailKey = stringPreferencesKey("user_email")
    val session = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map { prefs ->
        prefs[tokenKey]?.takeIf { it.isNotBlank() }?.let { stored -> decode(stored)?.let { Session(it, prefs[emailKey].orEmpty(), prefs[refreshKey]?.let { value -> decode(value) }) } }
    }
    @Synchronized
    private fun key(): javax.crypto.SecretKey {
        val store = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "samuschat.session"
        return (store.getKey(alias, null) as? javax.crypto.SecretKey) ?: javax.crypto.KeyGenerator.getInstance("AES", "AndroidKeyStore").run {
            init(android.security.keystore.KeyGenParameterSpec.Builder(alias, android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes("GCM").setEncryptionPaddings("NoPadding").build())
            generateKey()
        }
    }
    private fun encode(token: String): String {
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key())
        return "v1:" + android.util.Base64.encodeToString(cipher.iv + cipher.doFinal(token.toByteArray(Charsets.UTF_8)), android.util.Base64.NO_WRAP)
    }
    private fun decode(stored: String): String? = runCatching {
        if (!stored.startsWith("v1:")) stored else {
            val bytes = android.util.Base64.decode(stored.drop(3), android.util.Base64.NO_WRAP)
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key(), javax.crypto.spec.GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
        }
    }.getOrNull()
    suspend fun getToken(): String? {
        val value = session.first() ?: return null
        if (store.data.first()[tokenKey]?.startsWith("v1:") != true) saveSession(value.token, value.email, value.refreshToken)
        return value.token
    }
    suspend fun authorization() = "Bearer " + (getToken() ?: error("Entre novamente na sua conta"))
    suspend fun saveSession(token: String, email: String, refreshToken: String? = null) {
        require(token.isNotBlank())
        store.edit {
            it[tokenKey] = encode(token); it[emailKey] = email
            if (refreshToken != null) it[refreshKey] = encode(refreshToken) else it.remove(refreshKey)
        }
    }
    suspend fun renewSession(expected: Session, token: String): Boolean {
        var saved = false
        store.edit {
            if (it[tokenKey]?.let { value -> decode(value) } == expected.token && it[emailKey] == expected.email) {
                it[tokenKey] = encode(token)
                saved = true
            }
        }
        return saved
    }
    suspend fun clearSession() { store.edit { it.clear() } }
}
