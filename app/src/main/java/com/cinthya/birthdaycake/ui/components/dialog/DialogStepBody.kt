package com.cinthya.birthdaycake.ui.components.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import com.cinthya.birthdaycake.model.dialog.DialogExtra
import com.cinthya.birthdaycake.model.dialog.DialogStep
import com.cinthya.birthdaycake.model.dialog.resolve
import com.cinthya.birthdaycake.ui.components.PixelButton
import com.cinthya.birthdaycake.ui.theme.GameColors

/**
 * Wraps the part of a header that should sit on a pink highlight, the way "TODAY" does in
 * the mockup. Marking it up in the string keeps it a translator's decision rather than a
 * guess made from the casing.
 */
private const val HIGHLIGHT_OPEN = "[["
private const val HIGHLIGHT_CLOSE = "]]"

/**
 * Everything inside the panel, for one step.
 *
 * Each piece is drawn only when the step carries it, so the same composable renders a bare
 * line of description, a header with a button, or the whole recipe card. This is the only
 * thing the overlay animates - the frame around it is drawn by an ancestor and stays put.
 */
@Composable
fun DialogStepBody(
    step: DialogStep,
    onAdvance: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        step.header?.let {
            Text(
                highlighted(it.resolve()),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = GameColors.MainBlack,
                textAlign = if(step.extra is DialogExtra.Recipe) TextAlign.Center else TextAlign.Start
            )
        }

        step.desc?.let {
            Text(
                highlighted(it.resolve()),
                style = MaterialTheme.typography.bodyMedium,
                color = GameColors.MainBlack
            )
        }

        // Exhaustive: a new variant will not compile until it is handled here.
        when (val extra = step.extra) {
            is DialogExtra.Recipe -> RecipeExtraBody(extra)
            is DialogExtra.Pantry -> PantryExtraBody(extra)
            null -> Unit
        }

        step.button?.let {
            PixelButton(it.label.resolve(), onAdvance, Modifier.fillMaxWidth())
        }
    }
}

/** Turns `is [[TODAY]]!` into `is TODAY!` with the marked run on a pink field. */
private fun highlighted(raw: String): AnnotatedString {
    val open = raw.indexOf(HIGHLIGHT_OPEN)
    val close = raw.indexOf(HIGHLIGHT_CLOSE, startIndex = open + HIGHLIGHT_OPEN.length)
    if (open < 0 || close < 0) return AnnotatedString(raw)

    return buildAnnotatedString {
        append(raw.substring(0, open))
        withStyle(SpanStyle(background = GameColors.Highlight)) {
            append(raw.substring(open + HIGHLIGHT_OPEN.length, close))
        }
        append(raw.substring(close + HIGHLIGHT_CLOSE.length))
    }
}
