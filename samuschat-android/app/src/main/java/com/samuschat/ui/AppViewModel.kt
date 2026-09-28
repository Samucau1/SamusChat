package com.samuschat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samuschat.SamusChatApplication
import com.samuschat.data.repository.ApiFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

open class AppViewModel(protected val app: SamusChatApplication) : ViewModel() {
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    protected fun runTask(block: suspend () -> Unit) = viewModelScope.launch {
        if (busy.value) return@launch
        busy.value = true
        error.value = null
        try { block() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            if (e is ApiFailure && e.status == 401) app.tokens.clearSession()
            error.value = e.message ?: "Sem conexão com o servidor"
        } finally { busy.value = false }
    }
}
