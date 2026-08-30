package com.example.dougahenkankun.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryLightIndigo,
    onPrimary = TextDark,
    primaryContainer = PrimaryDarkIndigo,
    onPrimaryContainer = TextWhite,
    secondary = EmeraldLight,
    onSecondary = TextDark,
    secondaryContainer = EmeraldDark,
    onSecondaryContainer = TextWhite,
    tertiary = SkyBlue,
    background = DarkBackground,
    onBackground = TextWhite,
    surface = DarkSurface,
    onSurface = TextWhite,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextMutedDark,
    outline = DarkBorder,
    error = ErrorRed
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryIndigo,
    onPrimary = TextWhite,
    primaryContainer = PrimaryLightIndigo.copy(alpha = 0.2f),
    onPrimaryContainer = PrimaryDarkIndigo,
    secondary = EmeraldGreen,
    onSecondary = TextWhite,
    secondaryContainer = EmeraldLight.copy(alpha = 0.2f),
    onSecondaryContainer = EmeraldDark,
    tertiary = SkyBlue,
    background = LightBackground,
    onBackground = TextDark,
    surface = LightSurface,
    onSurface = TextDark,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextMutedLight,
    outline = LightBorder,
    error = ErrorRed
)

@Composable
fun DougaHenkanKunTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // ブランドカラーを統一して引き立たせるため基本カスタムカラーを使用
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
