package io.heckel.ntfy.ui.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.heckel.ntfy.R

/**
 * Compose theme for the 2026-11 redesign screens.
 *
 * Colors are read from res/values(-night)/colors.xml, which `verifyColorTokens` checks against
 * ntfy-web tokens.css, so there is one source of truth. Screens use `colorResource(R.color.<token>)`
 * for tokens without a Material role (accent_text, meter_*, topic_chip_*, priority_*).
 * Dark/light follows AppCompat night mode (applied to the configuration, read by isSystemInDarkTheme).
 */
@Composable
fun NotifyTheme(content: @Composable () -> Unit) {
    val bg = colorResource(R.color.bg)
    val surface = colorResource(R.color.surface)
    val text = colorResource(R.color.text)
    val primary = colorResource(R.color.accent_ui)
    val onPrimary = colorResource(R.color.accent_on_surface)
    val surface2 = colorResource(R.color.surface_2)
    val muted = colorResource(R.color.muted)
    val outline = colorResource(R.color.control_border)
    val border = colorResource(R.color.border)
    val error = colorResource(R.color.priority_urgent)
    val scheme = if (isSystemInDarkTheme()) {
        darkColorScheme(
            primary = primary, onPrimary = onPrimary, background = bg, onBackground = text,
            surface = surface, onSurface = text, surfaceVariant = surface2, onSurfaceVariant = muted,
            surfaceContainer = surface, surfaceContainerHigh = surface2,
            outline = outline, outlineVariant = border, error = error,
        )
    } else {
        lightColorScheme(
            primary = primary, onPrimary = onPrimary, background = bg, onBackground = text,
            surface = surface, onSurface = text, surfaceVariant = surface2, onSurfaceVariant = muted,
            surfaceContainer = surface, surfaceContainerHigh = surface2,
            outline = outline, outlineVariant = border, error = error,
        )
    }
    MaterialTheme(colorScheme = scheme, typography = NotifyTypography, shapes = NotifyShapes, content = content)
}

val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
)

val JetBrainsMono = FontFamily(Font(R.font.jetbrains_mono_regular, FontWeight.Normal))

private fun style(size: Int, leading: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = Pretendard, fontSize = size.sp, lineHeight = leading.sp, fontWeight = weight)

// Sizes/leadings = tokens.css --text-* / --leading-*.
val NotifyTypography = Typography(
    displaySmall = style(28, 34, FontWeight.Bold),     // text-display
    titleLarge = style(22, 28, FontWeight.Bold),       // text-title
    titleMedium = style(18, 24, FontWeight.SemiBold),  // text-subtitle
    bodyLarge = style(16, 24),                         // text-body
    bodyMedium = style(14, 20),                        // text-body-sm
    bodySmall = style(12, 16),                         // text-caption
    labelLarge = style(14, 20, FontWeight.SemiBold),
    labelMedium = style(12, 16, FontWeight.Medium),
    labelSmall = style(12, 16, FontWeight.Medium),
)

// radius-badge 8, radius-sm 14, radius-md 20.
val NotifyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(20.dp),
)
