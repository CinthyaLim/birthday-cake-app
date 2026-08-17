package com.cinthya.birthdaycake.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cinthya.birthdaycake.R
import com.cinthya.birthdaycake.data.GameData.cakeIngredients
import com.cinthya.birthdaycake.model.MemoryCard
import com.cinthya.birthdaycake.ui.theme.BirthdayCakeTheme

/** How long a card takes to turn over. The hunt view model waits this out before judging a miss. */
const val CARD_FLIP_DURATION_MS = 350L

/** Card art aspect ratio, taken from img_card_back so the faces never stretch. */
const val CARD_ASPECT_RATIO = 384f / 416f

/** Halfway through the turn is where the visible face swaps. */
private const val FACE_SWAP_DEGREES = 90f

/** Pulls the vanishing point far enough back that the turn does not look fish-eyed. */
private const val CAMERA_DISTANCE = 12f

private const val MATCHED_ALPHA = 0.45f

/**
 * One tile on the memory board. Both faces are complete pieces of art of the same size,
 * so they line up exactly through the turn.
 */
@Composable
fun FlipCard(
    card: MemoryCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rotation by animateFloatAsState(
        targetValue = if (card.isFaceUp) 180f else 0f,
        animationSpec = tween(CARD_FLIP_DURATION_MS.toInt()),
        label = "cardFlip"
    )
    val showingFront = rotation >= FACE_SWAP_DEGREES

    Box(
        modifier
            // The click stays outside the rotated layer so hit testing is unaffected by the turn.
            .clickable(enabled = !card.isFaceUp && !card.isMatched, onClick = onClick)
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = CAMERA_DISTANCE * density
            }
    ) {
        if (showingFront) {
            Image(
                painterResource(card.ingredient.cardResourceId),
                // Only name the ingredient once it is visible, or a screen reader gives the board away.
                contentDescription = stringResource(card.ingredient.nameResId),
                modifier = Modifier
                    .matchParentSize()
                    // Undoes the parent's half turn on a layer of its own; folding it into the
                    // rotation above would cancel the parent and mirror the art.
                    .graphicsLayer { rotationY = 180f },
                contentScale = ContentScale.FillBounds,
                alpha = if (card.isMatched) MATCHED_ALPHA else 1f
            )
        } else {
            Image(
                painterResource(R.drawable.img_card_back),
                contentDescription = stringResource(R.string.cd_hidden_card),
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.FillBounds
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 220)
@Composable
private fun FlipCardPrev() {
    BirthdayCakeTheme {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val sparkle = cakeIngredients.first { it.id == "sparkle" }
            FlipCard(
                MemoryCard(0, sparkle),
                onClick = {},
                Modifier.weight(1f).aspectRatio(CARD_ASPECT_RATIO)
            )
            FlipCard(
                MemoryCard(1, sparkle, isFaceUp = true),
                onClick = {},
                Modifier.weight(1f).aspectRatio(CARD_ASPECT_RATIO)
            )
            FlipCard(
                MemoryCard(2, sparkle, isFaceUp = true, isMatched = true),
                onClick = {},
                Modifier.weight(1f).aspectRatio(CARD_ASPECT_RATIO)
            )
        }
    }
}
