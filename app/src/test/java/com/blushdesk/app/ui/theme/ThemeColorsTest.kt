package com.blushdesk.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression test for a class-initialization cycle: touching [ShowroomColors] first used to build
 * the extended colors while the palette was half initialized, leaving them all transparent (the
 * operator card lost its gradient and its white text on device).
 */
class ThemeColorsTest {

    @Test
    fun `extended colors are complete even when the palette is touched first`() {
        // Same order as the app: the color scheme reads the palette before anything else.
        assertEquals(Color(0xFFE91E63), ShowroomColors.VibrantRose)

        val extended = DefaultExtendedColors
        assertEquals(Color(0xFF666666), extended.mutedOnTint)
        assertEquals(Color(0xFF880E4F), extended.brandGradientStart)
        assertEquals(Color(0xFFC2185B), extended.brandGradientEnd)
        assertEquals(Color.White, extended.onBrand)
        assertEquals(Color.White.copy(alpha = 0.9f), extended.onBrandMuted)
        assertEquals(Color(0xFFFCE4EC), extended.neutralBadge.background)
    }

    @Test
    fun `text colors follow the specification`() {
        assertEquals(Color(0xFF212121), ShowroomColors.DarkText)
        assertEquals(Color(0xFF757575), ShowroomColors.MutedText)
        assertEquals(Color(0xFFFFF0F5), ShowroomColors.LavenderBlush)
    }
}
