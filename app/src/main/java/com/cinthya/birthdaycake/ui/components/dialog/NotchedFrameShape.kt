package com.cinthya.birthdaycake.ui.components.dialog

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.min

/** One tread of the corner staircase. 16px on the 1368px source art reads as 6.dp here. */
val FRAME_STEP = 6.dp

/** The black outline. 12px on the source art, keeping the same 2:3 ratio to the tread. */
val FRAME_BORDER = 4.dp

/**
 * The pixel-art dialog frame, drawn rather than stretched.
 *
 * `img_dialog_box_plain.png` is a flat cream rectangle inside a flat black border, with a
 * two-tread staircase cut into each corner and no interior detail at all - so a path
 * reproduces it exactly. Drawing it is what lets the treads stay square and the border stay
 * even at any panel height, which matters here because the panel's height is animated: the
 * same frame has to hold a two-line greeting and a twelve-row recipe card.
 *
 * A data class so the background modifier's outline cache can tell two instances apart.
 */
@Immutable
data class NotchedFrameShape(
    val step: Dp = FRAME_STEP,
    val steps: Int = 2
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val n = steps.coerceAtLeast(1)
        // Cap the staircase against the shorter side, so a squat panel degrades to a
        // smaller notch instead of collapsing into a diamond.
        val unit = min(with(density) { step.toPx() }, size.minDimension / (4f * n))
        val w = size.width
        val h = size.height

        val path = Path().apply {
            moveTo(n * unit, 0f)
            lineTo(w - n * unit, 0f)
            for (i in 1..n) {                                 // top-right corner
                lineTo(w - (n - i + 1) * unit, i * unit)
                lineTo(w - (n - i) * unit, i * unit)
            }
            lineTo(w, h - n * unit)
            for (i in 1..n) {                                 // bottom-right corner
                lineTo(w - i * unit, h - (n - i + 1) * unit)
                lineTo(w - i * unit, h - (n - i) * unit)
            }
            lineTo(n * unit, h)
            for (i in 1..n) {                                 // bottom-left corner
                lineTo((n - i + 1) * unit, h - i * unit)
                lineTo((n - i) * unit, h - i * unit)
            }
            lineTo(0f, n * unit)
            for (i in 1..n) {                                 // top-left corner
                lineTo(i * unit, (n - i + 1) * unit)
                lineTo(i * unit, (n - i) * unit)
            }
            close()
        }

        return Outline.Generic(path)
    }
}
