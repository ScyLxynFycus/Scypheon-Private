package com.scypheon.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp


val ScypheonPrimary = Color(0xFF1558D6) // Deeper, more premium blue
val ScypheonBackground = Color(0xFFFDFDFD) // Elegant off-white
val ScypheonSurface = Color(0xFFFFFFFF)
val ScypheonText = Color(0xFF1F1F1F) // Deep charcoal
val ScypheonBlack = Color(0xFF000000) // Pitch Black for headers
val ScypheonTextSecondary = Color(0xFF5F6368)


private val LightColorScheme = lightColorScheme(
    primary = ScypheonPrimary,
    onPrimary = Color.White,
    background = ScypheonBackground,
    surface = ScypheonSurface,
    onSurface = ScypheonText,
    surfaceVariant = Color(0xFFF1F3F4),
    onSurfaceVariant = ScypheonTextSecondary,
    outlineVariant = Color(0xFFE0E0E0)
)

@Composable
fun ScypheonTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography(
            displaySmall = androidx.compose.ui.text.TextStyle(
                fontWeight = FontWeight.Medium,
                fontSize = 36.sp,
                letterSpacing = (-0.5).sp
            ),
            titleLarge = androidx.compose.ui.text.TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                letterSpacing = 0.sp
            )
        ),
        content = content
    )
}


