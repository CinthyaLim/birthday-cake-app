package com.cinthya.birthdaycake.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cinthya.birthdaycake.ui.components.dialog.DialogFrame
import com.cinthya.birthdaycake.ui.theme.BirthdayCakeTheme
import com.cinthya.birthdaycake.ui.theme.GameColors

/** Matches the dimming IngredientBox uses, so a dead button and a dead chip read alike. */
private const val DISABLED_ALPHA = 0.45f

/**
 * The pink action button - the same notched frame as the dialog panel, filled instead of
 * cream. Used by the mixing page and by any dialog step that carries a button.
 *
 * [enabled] dims the whole frame and hands `false` to `clickable`, which drops the press
 * feedback and marks the node disabled for TalkBack rather than just swallowing the tap.
 */
@Composable
fun PixelButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    DialogFrame(
        modifier = modifier
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clickable(enabled = enabled, onClick = onClick),
        fillColor = GameColors.AccentOne,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            label,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = GameColors.MainBlack
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFCB8C3, widthDp = 300)
@Composable
private fun PixelButtonPrev() {
    BirthdayCakeTheme {
        PixelButton("Help Me! 💗", {}, Modifier.padding(16.dp))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFCB8C3, widthDp = 300, name = "Disabled")
@Composable
private fun PixelButtonDisabledPrev() {
    BirthdayCakeTheme {
        PixelButton("Mix It!", {}, Modifier.padding(16.dp), enabled = false)
    }
}
