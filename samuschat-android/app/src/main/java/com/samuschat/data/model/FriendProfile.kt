package com.samuschat.data.model

data class FriendProfile(
    val publicId: String,
    val username: String,
    val avatarUrl: String? = null,
    val bannerUrl: String? = null,
    val backgroundUrl: String? = null,
    val accentColor: String? = null,
    val bio: String? = null,
    val relationship: String
)
