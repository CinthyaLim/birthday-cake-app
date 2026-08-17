package com.cinthya.birthdaycake.ui.components.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.cinthya.birthdaycake.ui.theme.BirthdayCakeTheme
import com.cinthya.birthdaycake.ui.theme.GameColors

/**
 * The notched cream panel: a dialog box, a recipe card, an ingredient row, a button.
 *
 * It owns the background and the border and nothing else, so it can sit *outside* the
 * content animation and stay on screen while what is inside it changes. That is the whole
 * reason it is a separate composable - see DialogOverlayHost for why moving these
 * backgrounds inside the animation makes the dialog blink.
 */
@Composable
fun DialogFrame(
    modifier: Modifier = Modifier,
    fillColor: Color = GameColors.OffWhite,
    borderColor: Color = GameColors.MainBlack,
    borderWidth: Dp = FRAME_BORDER,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val shape = remember { NotchedFrameShape() }

    // Two stacked fills rather than background(fill) + border(colour).
    //
    // For a generic (non-rounded-rect) outline, Modifier.border cannot simply stroke the
    // path - half the stroke would fall outside the bounds - so foundation strokes at twice
    // the width into an offscreen layer and clears the middle with BlendMode.Clear. That is
    // a saveLayer every time the draw cache is invalidated, and this panel's size changes
    // on every frame for the length of a step transition. Two path fills, no layers.
    Box(
        modifier
            .background(borderColor, shape)
            .padding(borderWidth)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(fillColor, shape)
                .padding(contentPadding),
            content = content
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFCB8C3, widthDp = 300)
@Composable
private fun DialogFramePrev() {
    BirthdayCakeTheme {
        DialogFrame(Modifier.padding(16.dp)) {
            Text("The corners stay square at any height.")
        }
    }
}
