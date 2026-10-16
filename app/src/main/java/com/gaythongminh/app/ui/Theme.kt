package com.gaythongminh.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Bảng màu tươi sáng: xanh da trời, cam, xanh lá. Chỉ có giao diện sáng (không có nền đen).
val Sky = Color(0xFF1E88E5)
val SkyLight = Color(0xFFD6ECFF)
val Orange = Color(0xFFFF9800)
val OrangeLight = Color(0xFFFFE8C2)
val Green = Color(0xFF2EB872)
val GreenLight = Color(0xFFD2F5E3)
val Red = Color(0xFFE53935)
val RedLight = Color(0xFFFFDAD6)
val Bg = Color(0xFFF3FAFF)

private val LightColors = lightColorScheme(
    primary = Sky,
    onPrimary = Color.White,
    primaryContainer = SkyLight,
    onPrimaryContainer = Color(0xFF0B3D6B),
    secondary = Orange,
    onSecondary = Color.White,
    secondaryContainer = OrangeLight,
    onSecondaryContainer = Color(0xFF5A3300),
    tertiary = Green,
    onTertiary = Color.White,
    tertiaryContainer = GreenLight,
    onTertiaryContainer = Color(0xFF0B4A2C),
    error = Red,
    onError = Color.White,
    errorContainer = RedLight,
    onErrorContainer = Color(0xFF6B0F0B),
    background = Bg,
    onBackground = Color(0xFF16232F),
    surface = Color.White,
    onSurface = Color(0xFF16232F),
    surfaceVariant = Color(0xFFE8F4FF),
    onSurfaceVariant = Color(0xFF3A4B5A)
)

@Composable
fun GtmTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, typography = Typography(), content = content)
}
