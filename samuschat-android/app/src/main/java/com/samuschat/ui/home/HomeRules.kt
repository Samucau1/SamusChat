package com.samuschat.ui.home

fun matchesFriend(name: String, query: String): Boolean = name.contains(query.trim(), ignoreCase = true)

fun canSendDemoMessage(draft: String): Boolean = draft.isNotBlank() && draft.length <= 2000

fun canSaveProfileName(name: String, current: String?): Boolean =
    current != null && name.isNotBlank() && name.trim().length <= 50 && name.trim() != current
