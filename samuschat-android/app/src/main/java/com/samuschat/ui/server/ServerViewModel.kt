package com.samuschat.ui.server

import com.samuschat.SamusChatApplication
import com.samuschat.data.model.Server
import com.samuschat.ui.AppViewModel
import kotlinx.coroutines.flow.MutableStateFlow

class ServerViewModel(app: SamusChatApplication) : AppViewModel(app) {
    val servers = MutableStateFlow<List<Server>>(emptyList())
    init { refresh() }
    fun refresh() = runTask { servers.value = app.servers.list() }
    fun create(name: String) = runTask {
        require(name.isNotBlank()) { "Informe o nome do servidor" }
        app.servers.create(name); servers.value = app.servers.list()
    }
    fun join(id: String) = runTask {
        val serverId = id.toLongOrNull()
        require(serverId != null && serverId > 0) { "Informe um ID válido" }
        app.servers.join(serverId); servers.value = app.servers.list()
    }
}
