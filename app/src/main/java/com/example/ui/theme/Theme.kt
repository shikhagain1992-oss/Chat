package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color

// White & Light Blue Mix Color Scheme (Light Mode)
private val WhiteLightBlueColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = LightBlueTint,
    onPrimaryContainer = PrimaryBlueDark,
    secondary = PrimaryBlueLight,
    onSecondary = TextDark,
    secondaryContainer = IceBlue,
    onSecondaryContainer = PrimaryBlueDark,
    background = AliceBlue,
    onBackground = TextDark,
    surface = PureWhite,
    onSurface = TextDark,
    surfaceVariant = IceBlue,
    onSurfaceVariant = TextDarkMuted,
    outline = SoftBlueBorder,
    outlineVariant = BubbleBorderColor,
    error = ErrorRed,
    onError = Color.White
)

// Navy & Electric Blue Dark Color Scheme (Dark Mode)
private val NavyDarkBlueColorScheme = darkColorScheme(
    primary = PrimaryBlueLight,
    onPrimary = NavyDark,
    primaryContainer = NavySurface,
    onPrimaryContainer = Color.White,
    secondary = PrimaryBlue,
    onSecondary = Color.White,
    secondaryContainer = NavySurface,
    onSecondaryContainer = Color.White,
    background = NavyDark,
    onBackground = Color(0xFFF1F5F9),
    surface = NavySurface,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF1E293B),
    error = ErrorRed,
    onError = Color.White
)

object ThemeState {
    val isDarkMode = mutableStateOf(false)
}

@Composable
fun NexChatTheme(
    darkTheme: Boolean = ThemeState.isDarkMode.value,
    content: @Composable () -> Unit
) {
    val scheme = if (darkTheme) NavyDarkBlueColorScheme else WhiteLightBlueColorScheme
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography,
        content = content
    )
}
