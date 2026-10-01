package com.samuschat.ui.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.samuschat.SamusChatApplication
import com.samuschat.data.repository.data
import com.samuschat.ui.components.ErrorNotice
import com.samuschat.ui.components.InitialAvatar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProfileState(
    val username: String? = null,
    val photo: String? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null
)

class ProfileViewModel(private val app: SamusChatApplication, private val email: String) : ViewModel() {
    private val preferences = app.getSharedPreferences("profile_photos", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(ProfileState(photo = preferences.getString(email, null)))
    val state = mutableState.asStateFlow()

    init { refresh() }

    fun refresh() = perform {
        val user = app.api.profile(app.tokens.authorization()).data()
        mutableState.value = mutableState.value.copy(username = user.username)
    }

    fun saveName(name: String) = perform {
        val username = name.trim()
        require(username.isNotBlank() && username.length <= 50) { "Use um nome de 1 a 50 caracteres." }
        val user = app.api.updateUsername(app.tokens.authorization(), username).data()
        mutableState.value = mutableState.value.copy(username = user.username, notice = "Nome atualizado na sua conta.")
    }

    fun savePhoto(uri: Uri) = perform {
        withContext(Dispatchers.IO) {
            app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            check(preferences.edit().putString(email, uri.toString()).commit()) { "Não foi possível salvar a foto." }
        }
        mutableState.value = mutableState.value.copy(photo = uri.toString(), notice = "Foto salva neste celular.")
    }

    private fun perform(action: suspend () -> Unit) {
        if (mutableState.value.busy) return
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(busy = true, error = null, notice = null)
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                mutableState.value = mutableState.value.copy(error = error.message ?: "Não foi possível atualizar o perfil.")
            } finally { mutableState.value = mutableState.value.copy(busy = false) }
        }
    }
}

@Composable
fun ProfileScreen(app: SamusChatApplication, email: String, onLogout: () -> Unit) {
    val model: ProfileViewModel = viewModel(key = "profile-$email", factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ProfileViewModel(app, email) as T
    })
    val state by model.state.collectAsStateWithLifecycle()
    var name by rememberSaveable(state.username) { mutableStateOf(state.username.orEmpty()) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(model::savePhoto)
    }
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Seu perfil", style = MaterialTheme.typography.headlineLarge)
        Text("Deixe o SamusChat com a sua cara.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.align(Alignment.CenterHorizontally)) {
            InitialAvatar(state.username ?: email, size = 104.dp)
            state.photo?.let {
                AsyncImage(model = it, contentDescription = "Sua foto de perfil",
                    modifier = Modifier.size(104.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            }
        }
        TextButton(onClick = { photoPicker.launch(arrayOf("image/*")) }, enabled = !state.busy,
            modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Escolher foto") }
        Text("A foto fica salva neste celular.", style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.align(Alignment.CenterHorizontally), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { ErrorNotice(it) }
        if (state.username == null && !state.busy) {
            TextButton(onClick = { model.refresh() }) { Text("Carregar perfil novamente") }
        }
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nome de usuário") },
            singleLine = true, enabled = !state.busy && state.username != null, isError = name.length > 50,
            supportingText = { Text("Até 50 caracteres") })
        Text(email, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { model.saveName(name) }, modifier = Modifier.fillMaxWidth(),
            enabled = !state.busy && canSaveProfileName(name, state.username)) {
            Text("Salvar nome")
        }
        state.notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        HorizontalDivider()
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth(), enabled = !state.busy) { Text("Sair da conta") }
    }
}
