package com.asciilive.wallpaper.ui.theme

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Material 3 Expressive Spring Motion tokens.
 * Spatial springs carry expressive overshoot; effects springs are critically damped.
 */
object ExpressiveMotion {
    fun <T> spatialDefault(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    fun <T> spatialFast(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium
    )

    fun <T> spatialSlow(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow
    )

    fun <T> effectsDefault(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )
}

private val ExpressiveDarkScheme = darkColorScheme(
    primary = Color(0xFFC8F582),
    onPrimary = Color(0xFF152400),
    primaryContainer = Color(0xFF283D09),
    onPrimaryContainer = Color(0xFFE2FFAE),
    secondary = Color(0xFFB5CC9F),
    onSecondary = Color(0xFF1F3014),
    secondaryContainer = Color(0xFF2B3821),
    onSecondaryContainer = Color(0xFFDCEFD0),
    tertiary = Color(0xFF8BE5E1),
    onTertiary = Color(0xFF003735),
    background = Color(0xFF090B0A),
    onBackground = Color(0xFFF0F2EE),
    surface = Color(0xFF111412),
    onSurface = Color(0xFFF0F2EE),
    surfaceVariant = Color(0xFF1C211D),
    onSurfaceVariant = Color(0xFFB4BEB3),
    surfaceContainerLowest = Color(0xFF070908),
    surfaceContainerLow = Color(0xFF131714),
    surfaceContainer = Color(0xFF181D19),
    surfaceContainerHigh = Color(0xFF212722),
    surfaceContainerHighest = Color(0xFF2A312B),
    outline = Color(0xFF3A443B),
    outlineVariant = Color(0xFF272E28)
)

private val ExpressiveLightScheme = lightColorScheme(
    primary = Color(0xFF395B00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC8F582),
    onPrimaryContainer = Color(0xFF122000),
    secondary = Color(0xFF4B5E3A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD5E9C0),
    onSecondaryContainer = Color(0xFF132208),
    tertiary = Color(0xFF006965),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF7FAF2),
    onBackground = Color(0xFF151914),
    surface = Color(0xFFF7FAF2),
    onSurface = Color(0xFF151914),
    surfaceVariant = Color(0xFFE3E9DC),
    onSurfaceVariant = Color(0xFF424A3E),
    outline = Color(0xFF727C6D),
    outlineVariant = Color(0xFFC5CEC0)
)

@Composable
fun AsciiExpressiveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> ExpressiveDarkScheme
        else -> ExpressiveLightScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ExpressiveTypography,
        content = content
    )
}
