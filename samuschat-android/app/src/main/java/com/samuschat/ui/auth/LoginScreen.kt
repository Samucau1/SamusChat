package com.samuschat.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.samuschat.R
import com.samuschat.ui.components.ErrorNotice
import com.samuschat.ui.theme.*

@Composable
fun LoginScreen(viewModel: AuthViewModel, onGoToRegister: () -> Unit) = AuthForm(false, viewModel, onGoToRegister)

@Composable
internal fun AuthForm(register: Boolean, viewModel: AuthViewModel, onSwitch: () -> Unit) {
    var username by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val submit = { viewModel.submit(register, username, email, password); Unit }
    val valid = email.isNotBlank() && password.isNotBlank() && (!register || username.isNotBlank())
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF353A70), RailBackground)))) {
        Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(28.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(color = Blurple, shape = RoundedCornerShape(22.dp)) {
                Icon(painterResource(R.drawable.ic_chat), null, Modifier.padding(18.dp).size(40.dp), tint = Color.White)
            }
            Spacer(Modifier.height(20.dp))
            Text("SamusChat", style = MaterialTheme.typography.headlineLarge)
            Text("Seu lugar. Sua galera.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(32.dp))
            Surface(shape = RoundedCornerShape(20.dp), color = PanelBackground, modifier = Modifier.widthIn(max = 440.dp)) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(if (register) "Crie sua conta" else "Bom te ver de novo!", style = MaterialTheme.typography.titleLarge)
                    Text(if (register) "Entre na conversa e encontre sua comunidade." else "Entre para continuar a conversa.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (register) OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(),
                        label = { Text("Nome de usuário") }, singleLine = true, enabled = !busy,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
                    OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email") },
                        singleLine = true, enabled = !busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
                    OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Senha") },
                        visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (valid && !busy) submit() }))
                    error?.let { ErrorNotice(it) }
                    Button(onClick = submit, enabled = valid && !busy, modifier = Modifier.fillMaxWidth()) {
                        Text(if (busy) "Aguarde…" else if (register) "Criar conta" else "Entrar", Modifier.padding(8.dp))
                    }
                    TextButton(onClick = onSwitch, enabled = !busy, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(if (register) "Já tenho uma conta" else "Criar uma conta")
                    }
                }
            }
        }
    }
}
