package com.samuschat.ui.auth
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.samuschat.ui.components.ErrorNotice
@Composable
fun RecoveryScreen(viewModel: AuthViewModel, onBack: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val stage by viewModel.recoveryStage.collectAsStateWithLifecycle()
    val notice by viewModel.recoveryNotice.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    androidx.activity.compose.BackHandler(enabled = !busy, onBack = onBack)
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Recuperar senha", style = MaterialTheme.typography.headlineMedium)
        notice?.let { Text(it) }
        when(stage) {
            0 -> {
                OutlinedTextField(email, { email=it }, label={ Text("Email da conta") }, singleLine=true, enabled=!busy, keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email))
                Button(onClick={ viewModel.requestRecovery(email) }, enabled=!busy && email.isNotBlank()) { Text("Enviar codigo") }
            }
            1 -> {
                OutlinedTextField(code, { code=it.filter { c -> c in '0'..'9' }.take(4) }, label={ Text("Codigo de 4 digitos") }, singleLine=true, enabled=!busy, keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
                Button(onClick={ viewModel.verifyRecovery(code); code="" }, enabled=!busy && code.length==4) { Text("Validar codigo") }
                TextButton(onClick={ viewModel.requestRecovery(email) }, enabled=!busy) { Text("Reenviar apos 10 minutos") }
            }
            2 -> {
                OutlinedTextField(password, { password=it }, label={ Text("Nova senha") }, singleLine=true, enabled=!busy, visualTransformation=PasswordVisualTransformation(), keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password))
                OutlinedTextField(confirm, { confirm=it }, label={ Text("Confirmar senha") }, singleLine=true, enabled=!busy, visualTransformation=PasswordVisualTransformation(), keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password))
                Button(onClick={ viewModel.resetPassword(password,confirm); password=""; confirm="" }, enabled=!busy && password.length>=6 && password==confirm) { Text("Salvar nova senha") }
            }
        }
        error?.let { ErrorNotice(it) }
        if(busy) CircularProgressIndicator()
        TextButton(onClick=onBack, enabled=!busy) { Text("Voltar ao login") }
    }
}
