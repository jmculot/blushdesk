package com.blushdesk.app.ui.theme

import androidx.compose.ui.unit.dp

/** Every spacing and size the UI uses, so layouts keep one rhythm and change in one place. */
object Dimens {
    // Spacing scale
    val spaceXs = 4.dp
    val spaceS = 8.dp
    val spaceM = 12.dp
    val spaceL = 16.dp
    val spaceXl = 20.dp
    val spaceXxl = 24.dp
    val spaceXxxl = 32.dp

    // Layout
    /** Narrower windows show one pane at a time. */
    val dualPaneMinWidth = 600.dp

    /** Below this height the search box scrolls with the buyer list instead of staying pinned. */
    val pinToolsMinHeight = 520.dp
    val masterPaneCompact = 320.dp
    val masterPaneMedium = 360.dp
    val masterPaneWide = 400.dp
    val masterPaneMediumFrom = 840.dp
    val masterPaneWideFrom = 1000.dp
    val dialogMaxWidth = 600.dp

    // Components
    val touchTarget = 48.dp
    val avatarSmall = 44.dp
    val avatarMedium = 72.dp
    val avatarLarge = 88.dp
    val avatarPicker = 96.dp
    val avatarRing = 3.dp
    val iconSmall = 14.dp
    val iconMedium = 18.dp
    val iconLarge = 22.dp
    val iconBadge = 40.dp
    val brandMark = 36.dp
    val border = 1.dp
    val borderSelected = 1.5.dp
    val progressStroke = 2.dp
    val stepNode = 32.dp
    val stepNodeInner = 20.dp
    val stepNodeDot = 7.dp
    val stepWidth = 96.dp
    val stepTrack = 4.dp
    val emptyStateIcon = 96.dp
}
