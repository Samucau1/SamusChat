package com.samuschat.ui.server

import com.samuschat.data.model.Server
import java.text.Normalizer
import java.util.Locale

/** Search only memberships returned by the API; this is not a public directory. */
fun findServers(servers: List<Server>, query: String): List<Server> {
    val normalized = normalize(query.trim())
    if (normalized.isEmpty()) return servers
    return servers.filter { normalize(it.name).contains(normalized) || it.id.toString() == normalized }
}

private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace("\\p{M}+".toRegex(), "").lowercase(Locale.ROOT)
