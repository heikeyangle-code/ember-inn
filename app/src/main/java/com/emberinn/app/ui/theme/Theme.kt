package com.emberinn.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = EmberPrimaryLight,
    onPrimary = EmberOnPrimaryLight,
    primaryContainer = EmberPrimaryContainerLight,
    onPrimaryContainer = EmberOnPrimaryContainerLight,
    secondary = EmberSecondaryLight,
    onSecondary = EmberOnSecondaryLight,
    secondaryContainer = EmberSecondaryContainerLight,
    onSecondaryContainer = EmberOnSecondaryContainerLight,
    tertiary = EmberTertiaryLight,
    onTertiary = EmberOnTertiaryLight,
    tertiaryContainer = EmberTertiaryContainerLight,
    onTertiaryContainer = EmberOnTertiaryContainerLight,
    background = EmberBackgroundLight,
    onBackground = EmberOnBackgroundLight,
    surface = EmberSurfaceLight,
    onSurface = EmberOnSurfaceLight,
    surfaceVariant = EmberSurfaceVariantLight,
    onSurfaceVariant = EmberOnSurfaceVariantLight,
    outline = EmberOutlineLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = EmberPrimaryDark,
    onPrimary = EmberOnPrimaryDark,
    primaryContainer = EmberPrimaryContainerDark,
    onPrimaryContainer = EmberOnPrimaryContainerDark,
    secondary = EmberSecondaryDark,
    onSecondary = EmberOnSecondaryDark,
    secondaryContainer = EmberSecondaryContainerDark,
    onSecondaryContainer = EmberOnSecondaryContainerDark,
    tertiary = EmberTertiaryDark,
    onTertiary = EmberOnTertiaryDark,
    tertiaryContainer = EmberTertiaryContainerDark,
    onTertiaryContainer = EmberOnTertiaryContainerDark,
    background = EmberBackgroundDark,
    onBackground = EmberOnBackgroundDark,
    surface = EmberSurfaceDark,
    onSurface = EmberOnSurfaceDark,
    surfaceVariant = EmberSurfaceVariantDark,
    onSurfaceVariant = EmberOnSurfaceVariantDark,
    outline = EmberOutlineDark,
)

@Composable
fun EmberInnTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography(),
        content = content,
    )
}
