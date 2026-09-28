package com.samuschat.util

import com.samuschat.BuildConfig

object Constants {
    const val BASE_URL = BuildConfig.BASE_URL
    val WS_URL = BASE_URL.replaceFirst("http", "ws").trimEnd('/') + "/ws/websocket"
    const val WS_SUBSCRIBE_PREFIX = "/topic/channel/"
    const val PAGE_SIZE = 50
}
