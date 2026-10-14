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
import com.samuschat.data.repository.withAppearancePart
import com.samuschat.data.model.ProfileAppearance
import com.samuschat.ui.components.*
import androidx.compose.foundation.background
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
    val banner: String? = null,
    val background: String? = null,
    val bio: String = "",
    val accentColor: String = "#5865F2",
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
        mutableState.value = mutableState.value.copy(username = user.username, photo = user.avatarUrl ?: mutableState.value.photo, banner = user.bannerUrl, background = user.backgroundUrl, bio = user.bio.orEmpty(), accentColor = user.accentColor ?: "#5865F2")
    }

    fun saveName(name: String) = perform {
        val username = name.trim()
        require(validProfileName(username)) { "Use um nome de 1 a 50 caracteres, sem tags ou caracteres de controle." }
        val user = app.api.updateUsername(app.tokens.authorization(), username).data()
        mutableState.value = mutableState.value.copy(username = user.username, notice = "Nome atualizado na sua conta.")
    }

    fun saveMedia(slot: String, uri: Uri) = perform {
        val user = withAppearancePart(app, uri) { file -> app.api.uploadProfileMedia(app.tokens.authorization(), slot, file).data() }
        mutableState.value = mutableState.value.copy(photo = user.avatarUrl, banner = user.bannerUrl, background = user.backgroundUrl, notice = "Imagem atualizada na sua conta.")
        if (slot == "avatar") preferences.edit().remove(email).apply()
    }
    fun removeMedia(slot: String) = perform {
        val user = app.api.deleteProfileMedia(app.tokens.authorization(), slot).data()
        if (slot == "avatar") preferences.edit().remove(email).apply()
        mutableState.value = mutableState.value.copy(photo = user.avatarUrl, banner = user.bannerUrl, background = user.backgroundUrl, notice = "Imagem removida da sua conta.")
    }
    fun saveAppearance(bio: String, color: String) = perform {
        require(color.matches(Regex("#[0-9a-fA-F]{6}"))) { "Use uma cor no formato #RRGGBB." }
        require(bio.trim().length <= 190) { "Sobre mim deve ter até 190 caracteres." }
        val user = app.api.updateAppearance(app.tokens.authorization(), ProfileAppearance(bio.trim(), color)).data()
        mutableState.value = mutableState.value.copy(bio = user.bio.orEmpty(), accentColor = user.accentColor ?: "#5865F2", notice = "Aparência salva na sua conta.")
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
    var bio by rememberSaveable(state.bio) { mutableStateOf(state.bio) }
    var color by rememberSaveable(state.accentColor) { mutableStateOf(state.accentColor) }
    var mediaSlot by rememberSaveable { mutableStateOf("avatar") }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { model.saveMedia(mediaSlot, it) }
    }
    Box(Modifier.fillMaxSize()) {
    AppearanceImage(state.background, null, Modifier.matchParentSize())
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)).imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AppearanceImage(state.banner, "Capa do perfil", Modifier.fillMaxWidth().height(140.dp))
        Text("Seu perfil", style = MaterialTheme.typography.headlineLarge)
        Text("Deixe o SamusChat com a sua cara.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        AnimationPreference { animated ->
            Box(Modifier.align(Alignment.CenterHorizontally).size(104.dp).clip(CircleShape).background(appearanceColor(state.accentColor, MaterialTheme.colorScheme.primary))) {
                InitialAvatar(state.username ?: email, size = 104.dp)
                if (state.photo?.startsWith("content:") == true) AsyncImage(state.photo, "Sua foto local anterior", Modifier.size(104.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                else AppearanceImage(state.photo, "Sua foto de perfil", Modifier.size(104.dp).clip(CircleShape), animated)
            }
        }
        TextButton(onClick = { mediaSlot = "avatar"; photoPicker.launch(arrayOf("image/png", "image/jpeg", "image/gif")) }, enabled = !state.busy,
            modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Escolher foto ou GIF") }
        if (state.photo != null) TextButton(onClick = { model.removeMedia("avatar") }, enabled = !state.busy) { Text("Remover foto") }
        TextButton(onClick = { mediaSlot = "banner"; photoPicker.launch(arrayOf("image/png", "image/jpeg", "image/gif")) }, enabled = !state.busy) { Text("Escolher capa do perfil") }
        if (state.banner != null) TextButton(onClick = { model.removeMedia("banner") }, enabled = !state.busy) { Text("Remover capa") }
        TextButton(onClick = { mediaSlot = "background"; photoPicker.launch(arrayOf("image/png", "image/jpeg", "image/gif")) }, enabled = !state.busy) { Text("Escolher fundo do perfil") }
        if (state.background != null) TextButton(onClick = { model.removeMedia("background") }, enabled = !state.busy) { Text("Remover fundo") }
        Text("PNG/JPG até 4 MB; GIF até 2 MB e 60 quadros. Avatar GIF até 512 px; capa até 960×540. As imagens novas aparecem também no PC.", style = MaterialTheme.typography.bodySmall)
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { ErrorNotice(it) }
        if (state.username == null && !state.busy) {
            TextButton(onClick = { model.refresh() }) { Text("Carregar perfil novamente") }
        }
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nome de usuário") },
            singleLine = true, enabled = !state.busy && state.username != null, isError = name.isNotBlank() && !validProfileName(name),
            supportingText = { Text("${name.trim().length}/50 caracteres") })
        Text(email, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { model.saveName(name) }, modifier = Modifier.fillMaxWidth(),
            enabled = !state.busy && canSaveProfileName(name, state.username)) {
            Text("Salvar nome")
        }
        if (state.username != null && name != state.username) TextButton(onClick = { name = state.username.orEmpty() },
            enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Descartar alteração do nome") }
        OutlinedTextField(bio, { bio = it }, Modifier.fillMaxWidth(), label = { Text("Sobre mim") }, enabled = !state.busy, supportingText = { Text("${bio.length}/190 caracteres") }, isError = bio.length > 190)
        OutlinedTextField(color, { color = it }, Modifier.fillMaxWidth(), label = { Text("Cor do perfil (#RRGGBB)") }, singleLine = true, enabled = !state.busy)
        Button(onClick = { model.saveAppearance(bio, color) }, enabled = !state.busy && state.username != null && bio.length <= 190 && color.matches(Regex("#[0-9a-fA-F]{6}"))) { Text("Salvar aparência") }
        state.notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        HorizontalDivider()
        Text("Sair encerra as sessões desta conta em todos os dispositivos.")
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth(), enabled = !state.busy) { Text("Sair da conta") }
    }
    }
}
