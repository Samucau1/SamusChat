package com.samuschat.util

import java.net.URI

fun attachmentUrl(baseUrl: String, path: String): String? = runCatching {
    val base = URI(baseUrl)
    var resolved = base.resolve(path)
    // The Android emulator reaches the development machine through 10.0.2.2.
    if (base.host == "10.0.2.2" && resolved.host in listOf("localhost", "127.0.0.1") &&
        resolved.path.startsWith("/uploads/") && resolved.port == base.port) {
        resolved = URI(base.scheme, null, base.host, base.port, resolved.path, resolved.query, null)
    }
    resolved.takeIf { it.scheme in listOf("http", "https") && it.host != null && it.userInfo == null }?.toString()
}.getOrNull()
