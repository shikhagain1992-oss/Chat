package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// White & Light Blue Mix Color Scheme
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

@Composable
fun NexChatTheme(
    darkTheme: Boolean = false, // Emphasize the requested White and Light Blue mix theme
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = WhiteLightBlueColorScheme,
        typography = Typography,
        content = content
    )
}
