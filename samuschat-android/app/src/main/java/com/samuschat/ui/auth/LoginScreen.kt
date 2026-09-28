package com.samuschat.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun LoginScreen(viewModel: AuthViewModel, onGoToRegister: () -> Unit) = AuthForm(false, viewModel, onGoToRegister)

@Composable
internal fun AuthForm(register: Boolean, viewModel: AuthViewModel, onSwitch: () -> Unit) {
    var username by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("SamusChat", style = MaterialTheme.typography.headlineLarge)
        if (register) OutlinedTextField(username, { username = it }, label = { Text("Nome") }, singleLine = true)
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true)
        OutlinedTextField(password, { password = it }, label = { Text("Senha") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { viewModel.submit(register, username, email, password) }, enabled = !busy) {
            Text(if (busy) "Aguarde…" else if (register) "Cadastrar" else "Entrar")
        }
        TextButton(onClick = onSwitch, enabled = !busy) { Text(if (register) "Voltar para login" else "Criar conta") }
    }
}
