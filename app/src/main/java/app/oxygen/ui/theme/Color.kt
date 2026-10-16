package app.oxygen.ui.theme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
@Immutable
data class OxygenColors(
    val background: Color,
    val surface: Color,
    val container: Color,
    val containerHigh: Color,
    val border: Color,
    val borderStrong: Color,
    val text: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val primary: Color,
    val onPrimary: Color,
    val accent: Color,
    val destructive: Color,
    val destructiveSoft: Color,
    val link: Color,
    val codeBackground: Color,
    val userBubble: Color,
    val success: Color,
    val warning: Color,
    val scrim: Color,
    val skeleton: Color,
)
val LightColors = OxygenColors(
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    container = Color(0xFFF4F4F5),
    containerHigh = Color(0xFFEDEDEF),
    border = Color(0xFFE4E4E7),
    borderStrong = Color(0xFFD4D4D8),
    text = Color(0xFF09090B),
    textSecondary = Color(0xFF52525B),
    textMuted = Color(0xFFA1A1AA),
    primary = Color(0xFF18181B),
    onPrimary = Color(0xFFFAFAFA),
    accent = Color(0xFFF4F4F5),
    destructive = Color(0xFFDC2626),
    destructiveSoft = Color(0xFFFEF2F2),
    link = Color(0xFF2563EB),
    codeBackground = Color(0xFFF4F4F5),
    userBubble = Color(0xFFF4F4F5),
    success = Color(0xFF16A34A),
    warning = Color(0xFFD97706),
    scrim = Color(0x66000000),
    skeleton = Color(0xFFE4E4E7),
)
val DarkColors = OxygenColors(
    background = Color(0xFF09090B),
    surface = Color(0xFF0C0C0E),
    container = Color(0xFF18181B),
    containerHigh = Color(0xFF222225),
    border = Color(0xFF27272A),
    borderStrong = Color(0xFF3F3F46),
    text = Color(0xFFFAFAFA),
    textSecondary = Color(0xFFA1A1AA),
    textMuted = Color(0xFF71717A),
    primary = Color(0xFFFAFAFA),
    onPrimary = Color(0xFF18181B),
    accent = Color(0xFF27272A),
    destructive = Color(0xFFEF4444),
    destructiveSoft = Color(0xFF2C1414),
    link = Color(0xFF60A5FA),
    codeBackground = Color(0xFF18181B),
    userBubble = Color(0xFF27272A),
    success = Color(0xFF4ADE80),
    warning = Color(0xFFFBBF24),
    scrim = Color(0x99000000),
    skeleton = Color(0xFF27272A),
)
val OledColors = DarkColors.copy(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    container = Color(0xFF0F0F10),
    containerHigh = Color(0xFF1A1A1D),
    border = Color(0xFF232327),
    borderStrong = Color(0xFF333338),
    codeBackground = Color(0xFF0F0F10),
    userBubble = Color(0xFF1A1A1D),
    skeleton = Color(0xFF232327),
)
val LocalOxygenColors = staticCompositionLocalOf { LightColors }
enum class OxAccent(val id: String, val label: String, val vivid: Color) {
    Neutral("neutral", "Neutral", Color(0xFF18181B)),
    Indigo("indigo", "Indigo", Color(0xFF4F46E5)),
    Blue("blue", "Blue", Color(0xFF2563EB)),
    Emerald("emerald", "Emerald", Color(0xFF059669)),
    Amber("amber", "Amber", Color(0xFFB45309)),
    Rose("rose", "Rose", Color(0xFFE11D48)),
    Violet("violet", "Violet", Color(0xFF7C3AED));
    companion object {
        fun byId(id: String): OxAccent = entries.firstOrNull { it.id == id } ?: Neutral
    }
}
fun oxygenColors(dark: Boolean, oled: Boolean, accentId: String): OxygenColors {
    val base = when {
        oled && dark -> OledColors
        dark -> DarkColors
        else -> LightColors
    }
    val accent = OxAccent.byId(accentId)
    if (accent == OxAccent.Neutral) return base
    return base.copy(
        primary = accent.vivid,
        onPrimary = Color.White,
        link = if (dark) lighten(accent.vivid, 0.32f) else accent.vivid,
        userBubble = accent.vivid.copy(alpha = if (dark) 0.22f else 0.12f),
    )
}
private fun lighten(color: Color, fraction: Float): Color = Color(
    red = color.red + (1f - color.red) * fraction,
    green = color.green + (1f - color.green) * fraction,
    blue = color.blue + (1f - color.blue) * fraction,
    alpha = color.alpha,
)
