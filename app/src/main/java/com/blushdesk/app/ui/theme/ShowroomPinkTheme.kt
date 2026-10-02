package com.blushdesk.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blushdesk.app.domain.BrandPalette

/**
 * Every design token of the Showroom Pink theme in one file: colors, type scale, shapes, spacing.
 * Screens read these through MaterialTheme (colorScheme / typography / shapes) and
 * [ShowroomSpacing]; they should never hard-code a color or a corner radius.
 *
 * The four brand colors come from [BrandPalette], which the PDF receipt and the Excel export
 * share, so a rebrand is a one-place change.
 */
object ShowroomColors {
    val SoftPink = rgb(BrandPalette.SOFT_PINK)
    val VibrantRose = rgb(BrandPalette.VIBRANT_ROSE)
    val DeepMagenta = rgb(BrandPalette.DEEP_MAGENTA)
    val LavenderBlush = rgb(BrandPalette.LAVENDER_BLUSH)

    val Ink = rgb(BrandPalette.INK)
    val Muted = rgb(BrandPalette.MUTED)
    val Outline = rgb(BrandPalette.OUTLINE)
    val White = rgb(BrandPalette.WHITE)

    /** A pink between SoftPink and VibrantRose, for selected rows and pressed states. */
    val RoseTint = Color(0xFFF8D7E3)
}

/** Chip colors for a status, from the shared palette so Compose, PDF and Excel agree. */
data class ToneColors(val foreground: Color, val background: Color)

fun BrandPalette.Tone.toColors() = ToneColors(rgb(foreground), rgb(background))

private fun rgb(value: Int) = Color(0xFF000000.toInt() or value)

private val ShowroomLightColors = lightColorScheme(
    primary = ShowroomColors.VibrantRose,
    onPrimary = ShowroomColors.White,
    primaryContainer = ShowroomColors.SoftPink,
    onPrimaryContainer = ShowroomColors.DeepMagenta,
    inversePrimary = Color(0xFFF48FB1),

    secondary = ShowroomColors.DeepMagenta,
    onSecondary = ShowroomColors.White,
    secondaryContainer = ShowroomColors.RoseTint,
    onSecondaryContainer = Color(0xFF4A0828),

    tertiary = Color(0xFFAD1457),
    onTertiary = ShowroomColors.White,
    tertiaryContainer = Color(0xFFFFD9E6),
    onTertiaryContainer = Color(0xFF5B0F32),

    background = ShowroomColors.LavenderBlush,
    onBackground = ShowroomColors.Ink,

    surface = ShowroomColors.White,
    onSurface = ShowroomColors.Ink,
    surfaceVariant = Color(0xFFF8E1EA),
    onSurfaceVariant = ShowroomColors.Muted,
    surfaceTint = ShowroomColors.VibrantRose,
    surfaceContainerLowest = ShowroomColors.White,
    surfaceContainerLow = Color(0xFFFFF7FA),
    surfaceContainer = ShowroomColors.LavenderBlush,
    surfaceContainerHigh = ShowroomColors.SoftPink,
    surfaceContainerHighest = Color(0xFFF9D5E3),

    outline = Color(0xFF9A6A7F),
    outlineVariant = ShowroomColors.Outline,

    error = Color(0xFFBA1A1A),
    onError = ShowroomColors.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    inverseSurface = Color(0xFF3B2530),
    inverseOnSurface = ShowroomColors.LavenderBlush,
    scrim = Color(0xFF000000),
)

private val ShowroomTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium),
        // Small uppercase "HUD" captions above values.
        labelSmall = TextStyle(
            fontSize = 11.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
        ),
    )
}

private val ShowroomShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Spacing scale. Use these instead of ad-hoc dp values so the layout keeps one rhythm. */
object ShowroomSpacing {
    val xs: Dp = 4.dp
    val s: Dp = 8.dp
    val m: Dp = 12.dp
    val l: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
}

@Composable
fun ShowroomPinkTheme(content: @Composable () -> Unit) {
    // Light only, on purpose: the pink palette is the brand, and a showroom tablet is used in a lit room.
    MaterialTheme(
        colorScheme = ShowroomLightColors,
        typography = ShowroomTypography,
        shapes = ShowroomShapes,
        content = content,
    )
}
