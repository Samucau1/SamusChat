package com.samuschat.ui.auth

import com.samuschat.SamusChatApplication
import com.samuschat.ui.AppViewModel

class AuthViewModel(app: SamusChatApplication) : AppViewModel(app) {
    val recoveryStage = kotlinx.coroutines.flow.MutableStateFlow(0)
    val recoveryNotice = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    private var recoveryEmail = ""
    private var resetToken = ""
    fun closeRecovery() { recoveryStage.value = 0; resetToken = ""; recoveryEmail = ""; recoveryNotice.value = null; error.value = null }
    fun requestRecovery(email: String) = runTask {
        recoveryNotice.value = app.auth.requestRecovery(email)
        recoveryEmail = email.trim(); resetToken = ""; recoveryStage.value = 1
    }
    fun verifyRecovery(code: String) = runTask {
        resetToken = app.auth.verifyRecovery(recoveryEmail, code)
        recoveryNotice.value = "Codigo validado. Escolha sua nova senha."; recoveryStage.value = 2
    }
    fun resetPassword(password: String, confirm: String) = runTask {
        require(password == confirm) { "As senhas precisam ser iguais" }
        recoveryNotice.value = app.auth.resetPassword(recoveryEmail, resetToken, password)
        resetToken = ""; recoveryStage.value = 3
    }
    fun google(context: android.content.Context, password: String) = runTask {
        require(com.samuschat.BuildConfig.GOOGLE_CLIENT_ID.isNotBlank()) { "Login Google ainda nao configurado" }
        val option = com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption.Builder(com.samuschat.BuildConfig.GOOGLE_CLIENT_ID).build()
        val request = androidx.credentials.GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = androidx.credentials.CredentialManager.create(context).getCredential(context, request)
        val credential = result.credential as? androidx.credentials.CustomCredential ?: error("Credencial Google invalida")
        require(credential.type == com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) { "Credencial Google invalida" }
        val token = com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.createFrom(credential.data).idToken
        app.auth.googleLogin(token, password.takeIf { it.isNotBlank() })
    }
    fun submit(register: Boolean, username: String, email: String, password: String) = runTask {
        require(email.isNotBlank() && password.isNotBlank() && (!register || username.isNotBlank())) { "Preencha todos os campos" }
        if (register) app.auth.register(username, email, password) else app.auth.login(email, password)
    }
}
