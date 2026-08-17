package com.cinthya.birthdaycake.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cinthya.birthdaycake.ui.theme.GameColors

/** Matches the dash rhythm of [DashedDivider], so the pantry frame and the dividers agree. */
private val DASH_INTERVALS = floatArrayOf(10f, 10f)

/**
 * A dashed rectangle drawn behind whatever it is applied to - the pantry panel's frame.
 *
 * Drawn rather than composed so it costs no layout node and can wrap content of any size,
 * which the pantry needs: the grid inside it grows as the slots fill.
 */
fun Modifier.dashedBorder(
    color: Color = GameColors.MainBlack,
    width: Dp = 2.dp,
    cornerRadius: Dp = 4.dp
): Modifier = drawBehind {
    val strokeWidth = width.toPx()
    val inset = strokeWidth / 2f
    drawRoundRect(
        color = color,
        topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
        size = androidx.compose.ui.geometry.Size(
            size.width - strokeWidth,
            size.height - strokeWidth
        ),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius.toPx()),
        style = Stroke(
            width = strokeWidth,
            pathEffect = PathEffect.dashPathEffect(DASH_INTERVALS)
        )
    )
}
