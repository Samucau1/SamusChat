package com.samuschat.ui.auth

import com.samuschat.SamusChatApplication
import com.samuschat.ui.AppViewModel

class AuthViewModel(app: SamusChatApplication) : AppViewModel(app) {
    fun submit(register: Boolean, username: String, email: String, password: String) = runTask {
        require(email.isNotBlank() && password.isNotBlank() && (!register || username.isNotBlank())) { "Preencha todos os campos" }
        if (register) app.auth.register(username, email, password) else app.auth.login(email, password)
    }
}
