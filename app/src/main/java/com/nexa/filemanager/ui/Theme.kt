package com.nexa.filemanager.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

val Violet = Color(0xFF9E7BFF)
val Cyan = Color(0xFF00E5FF)
val Pink = Color(0xFFFF6AA7)
val Mint = Color(0xFF66E0B5)
val Amber = Color(0xFFFFCA7B)
private val Night = darkColorScheme(
    primary = Violet, onPrimary = Color(0xFF1C103C), secondary = Cyan, tertiary = Pink,
    background = Color(0xFF06070B), surface = Color(0xFF10121B), surfaceVariant = Color(0xFF1A1D2A),
    onBackground = Color(0xFFF5F3FC), onSurface = Color(0xFFF5F3FC), onSurfaceVariant = Color(0xFF9A9EB4),
    outline = Color(0xFF303345)
)
private val Day = lightColorScheme(
    primary = Color(0xFF6738D2), secondary = Color(0xFF007E90), tertiary = Color(0xFFBE2666),
    background = Color(0xFFF5F5FC), surface = Color.White, surfaceVariant = Color(0xFFECEBF5),
    onBackground = Color(0xFF171328), onSurface = Color(0xFF171328), onSurfaceVariant = Color(0xFF656175),
    outline = Color(0xFFD7D4E3)
)
@Composable
fun FileManagerTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Night else Day, shapes = Shapes(
        small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp)
    ), content = content)
}
