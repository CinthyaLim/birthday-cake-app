package com.cinthya.birthdaycake.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage
import com.cinthya.birthdaycake.model.CharacterExpressions
import com.cinthya.birthdaycake.model.toImageResource

/** Long enough to read as a mood change, short enough not to lag the dialog it belongs to. */
private const val EXPRESSION_FADE_MS = 200

/**
 * The cat. Drawn through Coil so the expression GIFs actually animate;
 * previews fall back to a still frame because the IDE has no image loader.
 *
 * The crossfade runs on its own clock rather than inside the dialog's step animation, so a
 * change of mood never restarts the panel's resize - and swapping GIFs mid-conversation
 * does not cut.
 */
@Composable
fun CharacterPlaceholder(expression: CharacterExpressions, modifier: Modifier = Modifier) {

    Crossfade(
        targetState = expression,
        animationSpec = tween(EXPRESSION_FADE_MS),
        label = "expression",
        modifier = modifier.aspectRatio(1f)
    ) { current ->
        if (LocalInspectionMode.current) {
            Image(painterResource(current.toImageResource()), null, Modifier.fillMaxSize())
        } else {
            AsyncImage(
                model = current.toImageResource(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
