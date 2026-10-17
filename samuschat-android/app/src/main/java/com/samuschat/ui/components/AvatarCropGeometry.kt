package com.samuschat.ui.components

data class AvatarRect(val left: Float, val top: Float, val side: Float)
fun avatarRect(width: Int, height: Int, zoom: Float, x: Float, y: Float): AvatarRect {
    require(width > 0 && height > 0 && zoom.isFinite() && x.isFinite() && y.isFinite())
    val side = minOf(width, height) / zoom.coerceIn(1f, 4f)
    return AvatarRect((width - side) * x.coerceIn(0f, 1f), (height - side) * y.coerceIn(0f, 1f), side)
}
