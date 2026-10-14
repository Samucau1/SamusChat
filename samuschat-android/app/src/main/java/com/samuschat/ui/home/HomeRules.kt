package com.samuschat.ui.home

fun matchesFriend(name: String, query: String): Boolean = name.contains(query.trim(), ignoreCase = true)

fun canSendDemoMessage(draft: String): Boolean = draft.isNotBlank() && draft.length <= 2000

fun canSaveProfileName(name: String, current: String?): Boolean =
    current != null && validProfileName(name) && name.trim() != current

fun validProfileName(name: String): Boolean = name.trim().let { value ->
    value.isNotEmpty() && value.length <= 50 && value.none { it.isISOControl() || it == '<' || it == '>' }
}
