package com.cinthya.birthdaycake.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The brand palette, mirroring `res/values/colors.xml`.
 *
 * The XML stays the source of truth for the platform theme and the launcher; this exists
 * because [androidx.compose.ui.res.colorResource] is `@Composable`, so it cannot be a
 * default parameter value and cannot be read from a [androidx.compose.ui.graphics.Shape]'s
 * drawing code - both of which the dialog frame needs.
 */
object GameColors {
    val MainBlack = Color(0xFF1A1A1A)
    val PastelPink = Color(0xFFFCB8C3)
    val OffWhite = Color(0xFFFFF8F0)
    val AccentOne = Color(0xFFFFD6E8)
    val AccentTwo = Color(0xFFE8D4F0)

    /** Dims the page behind a dialog. */
    val Scrim = Color(0xFF000000).copy(alpha = 0.55f)

    /** Secondary copy - flavour labels, footnotes, "0/10 in stock". */
    val Muted = MainBlack.copy(alpha = 0.55f)

    /** The highlight behind a word like "TODAY". */
    val Highlight = Color(0xFFFFB3D1)
}
