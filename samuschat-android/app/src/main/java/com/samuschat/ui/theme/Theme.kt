package com.samuschat.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val RailBackground = Color.Black
val PanelBackground = Color(0xFF101116)
val ChatBackground = Color.Black
val Blurple = Color(0xFF5562FF)
val ControlBackground = Color(0xFF242631)
val LiveGreen = Color(0xFF20C66B)
val DangerRed = Color(0xFFE02F44)

private val Colors = darkColorScheme(
    primary = Blurple, onPrimary = Color.White,
    primaryContainer = Color(0xFF252A68), onPrimaryContainer = Color(0xFFE5E7FF),
    secondary = Color(0xFFC4C8D4), onSecondary = RailBackground,
    background = Color.Black, onBackground = Color(0xFFF5F6FA),
    surface = PanelBackground, onSurface = Color(0xFFF5F6FA),
    surfaceVariant = ControlBackground, onSurfaceVariant = Color(0xFFC4C8D4),
    outline = Color(0xFF666B7E), error = Color(0xFFFF6577),
    errorContainer = Color(0xFF410E1A), onErrorContainer = Color(0xFFFFDFE4)
)

@Composable
fun SamusChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors,
        typography = Typography(
            headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp),
            headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
            titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 27.sp),
            titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
            bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
            labelSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 16.sp)
        ),
        shapes = Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(20.dp)),
        content = content)
}
