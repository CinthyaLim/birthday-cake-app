package com.cinthya.birthdaycake.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cinthya.birthdaycake.model.CharacterExpressions
import com.cinthya.birthdaycake.ui.theme.GameColors

/** The cat's share of the page width. Was copy-pasted into all three pages; lives here now. */
const val CHARACTER_WIDTH_FRACTION = 0.24f

val PAGE_PADDING = 16.dp

/** What the scaffold hands a page: a size, so the page can reserve the cat's corner in flow. */
@Immutable
data class GamePageScope(val characterSize: Dp)

/**
 * The frame every page sits in: the pink background, the page padding, the dialog overlay,
 * and - exactly once - the cat.
 *
 * The character is drawn here, last, so it sits above both the page and the overlay's
 * scrim. That is why it moved out of the pages: a dialog speaks with its own expression, so
 * with a copy in the page and a copy in the overlay there were two cats, and the page's one
 * was dimmed while the dialog's was not.
 *
 * [systemInsets] is applied to the content and the cat but not to the root, so the pink
 * background and the scrim run full-bleed behind the status and navigation bars.
 */
@Composable
fun GamePageScaffold(
    expression: CharacterExpressions,
    modifier: Modifier = Modifier,
    systemInsets: PaddingValues = PaddingValues(0.dp),
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable GamePageScope.() -> Unit
) {
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(GameColors.PastelPink)
    ) {
        val scope = remember(maxWidth) { GamePageScope(maxWidth * CHARACTER_WIDTH_FRACTION) }

        Box(
            Modifier
                .fillMaxSize()
                .padding(systemInsets)
                .padding(PAGE_PADDING)
        ) {
            scope.content()
        }

        overlay()

        CharacterPlaceholder(
            expression,
            Modifier
                .align(Alignment.TopEnd)
                .padding(systemInsets)
                .padding(PAGE_PADDING)
                .size(scope.characterSize)
        )
    }
}
