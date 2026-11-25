package com.samuschat.ui.server

import com.samuschat.SamusChatApplication
import com.samuschat.data.model.Server
import com.samuschat.data.model.ServerAppearance
import com.samuschat.data.repository.data
import com.samuschat.data.repository.withAppearancePart
import android.net.Uri
import com.samuschat.ui.AppViewModel
import kotlinx.coroutines.flow.MutableStateFlow

class ServerViewModel(app: SamusChatApplication) : AppViewModel(app) {
    val servers = MutableStateFlow<List<Server>>(emptyList())
    init { refresh() }
    fun refresh() = runTask { servers.value = app.servers.list() }
    fun saveAppearance(id: Long, color: String) = runTask {
        require(color.matches(Regex("#[0-9a-fA-F]{6}"))) { "Use uma cor no formato #RRGGBB." }
        app.api.updateServerAppearance(app.tokens.authorization(), id, ServerAppearance(color)).data()
        servers.value = app.servers.list()
    }
    fun media(id: Long, slot: String, uri: Uri?) = runTask {
        if (uri == null) app.api.deleteServerMedia(app.tokens.authorization(), id, slot).data()
        else withAppearancePart(app, uri) { file -> app.api.uploadServerMedia(app.tokens.authorization(), id, slot, file).data() }
        servers.value = app.servers.list()
    }
    fun createChannel(serverId: Long, name: String, type: String, onSuccess: () -> Unit) = runTask {
        require(name.isNotBlank() && name.trim().length <= 255) { "Informe um nome de até 255 caracteres" }
        require(type in listOf("TEXT", "VOICE"))
        app.servers.createChannel(serverId, name, type)
        servers.value = app.servers.list()
        onSuccess()
    }
    fun create(name: String, onSuccess: (Long) -> Unit) = runTask {
        require(name.isNotBlank()) { "Informe o nome do servidor" }
        val created = app.servers.create(name)
        servers.value = app.servers.list()
        onSuccess(created.id)
    }
    fun join(id: String, onSuccess: (Long) -> Unit) = runTask {
        val code = id.trim()
        require(code.matches(Regex("[A-Za-z0-9!@#$%*+_-]{3}"))) { "Informe o código de 3 caracteres" }
        val previous = servers.value.map { it.id }.toSet()
        app.servers.join(code); servers.value = app.servers.list()
        val joined = servers.value.firstOrNull { it.id !in previous }
        require(joined != null) { "Atualize a lista de servidores para continuar" }
        onSuccess(joined.id)
    }
}
