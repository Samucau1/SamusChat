package com.samuschat.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val RailBackground = Color(0xFF1E1F22)
val PanelBackground = Color(0xFF2B2D31)
val ChatBackground = Color(0xFF313338)
val Blurple = Color(0xFF5865F2)

private val Colors = darkColorScheme(
    primary = Blurple, onPrimary = Color.White,
    primaryContainer = Color(0xFF353B70), onPrimaryContainer = Color(0xFFE0E3FF),
    secondary = Color(0xFFB5BAC1), onSecondary = RailBackground,
    background = PanelBackground, onBackground = Color(0xFFF2F3F5),
    surface = PanelBackground, onSurface = Color(0xFFF2F3F5),
    surfaceVariant = Color(0xFF383A40), onSurfaceVariant = Color(0xFFB5BAC1),
    outline = Color(0xFF4E5058), error = Color(0xFFFF8D96),
    errorContainer = Color(0xFF4A262D), onErrorContainer = Color(0xFFFFDADD)
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
