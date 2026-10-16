package app.oxygen.ui.theme
import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
object Ox {
    val colors: OxygenColors
        @Composable get() = LocalOxygenColors.current
}
val LocalChatFontScale = compositionLocalOf { 1f }
@Composable
fun OxygenTheme(
    themeMode: String = "system",
    oled: Boolean = false,
    accentId: String = "neutral",
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val colors = oxygenColors(dark, oled, accentId)
    val colorScheme = if (dark) {
        darkColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            primaryContainer = colors.containerHigh,
            onPrimaryContainer = colors.text,
            secondary = colors.textSecondary,
            onSecondary = colors.surface,
            secondaryContainer = colors.accent,
            onSecondaryContainer = colors.text,
            background = colors.background,
            onBackground = colors.text,
            surface = colors.surface,
            onSurface = colors.text,
            surfaceVariant = colors.container,
            onSurfaceVariant = colors.textSecondary,
            surfaceContainer = colors.container,
            surfaceContainerHigh = colors.containerHigh,
            surfaceContainerHighest = colors.containerHigh,
            surfaceContainerLow = colors.surface,
            surfaceContainerLowest = colors.surface,
            outline = colors.borderStrong,
            outlineVariant = colors.border,
            error = colors.destructive,
            onError = Color.White,
            errorContainer = colors.destructiveSoft,
            onErrorContainer = colors.destructive,
            scrim = colors.scrim,
            inverseSurface = colors.text,
            inverseOnSurface = colors.background,
        )
    } else {
        lightColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            primaryContainer = colors.containerHigh,
            onPrimaryContainer = colors.text,
            secondary = colors.textSecondary,
            onSecondary = colors.surface,
            secondaryContainer = colors.accent,
            onSecondaryContainer = colors.text,
            background = colors.background,
            onBackground = colors.text,
            surface = colors.surface,
            onSurface = colors.text,
            surfaceVariant = colors.container,
            onSurfaceVariant = colors.textSecondary,
            surfaceContainer = colors.container,
            surfaceContainerHigh = colors.containerHigh,
            surfaceContainerHighest = colors.containerHigh,
            surfaceContainerLow = colors.surface,
            surfaceContainerLowest = colors.surface,
            outline = colors.borderStrong,
            outlineVariant = colors.border,
            error = colors.destructive,
            onError = Color.White,
            errorContainer = colors.destructiveSoft,
            onErrorContainer = colors.destructive,
            scrim = colors.scrim,
            inverseSurface = colors.text,
            inverseOnSurface = colors.background,
        )
    }
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(20.dp),
    )
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
                window.isStatusBarContrastEnforced = false
            } else {
                @Suppress("DEPRECATION")
                run {
                    window.statusBarColor = colors.background.toArgb()
                    window.navigationBarColor = colors.background.toArgb()
                }
            }
        }
    }
    CompositionLocalProvider(
        LocalOxygenColors provides colors,
        LocalChatFontScale provides fontScale,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = oxygenTypography(),
            shapes = shapes,
            content = content,
        )
    }
}
