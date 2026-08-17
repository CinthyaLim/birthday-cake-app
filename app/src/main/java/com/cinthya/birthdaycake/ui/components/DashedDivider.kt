package com.cinthya.birthdaycake.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cinthya.birthdaycake.ui.theme.BirthdayCakeTheme
import com.cinthya.birthdaycake.ui.theme.GameColors

@Composable
fun DashedDivider(modifier: Modifier = Modifier) {
    val lineColor = GameColors.MainBlack
    Canvas(
        modifier
            .fillMaxWidth()
            .height(2.dp)
    ) {
        drawLine(
            color = lineColor,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = size.height,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
        )
    }
}

@Preview(showBackground = true, widthDp = 200)
@Composable
private fun DashedDividerPrev() {
    BirthdayCakeTheme {
        DashedDivider()
    }
}
