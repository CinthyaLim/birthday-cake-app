package com.cinthya.birthdaycake.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cinthya.birthdaycake.R
import com.cinthya.birthdaycake.data.GameData
import com.cinthya.birthdaycake.model.CharacterExpressions
import com.cinthya.birthdaycake.model.IngredientsCount
import com.cinthya.birthdaycake.ui.components.CARD_ASPECT_RATIO
import com.cinthya.birthdaycake.ui.components.DashedDivider
import com.cinthya.birthdaycake.ui.components.FlipCard
import com.cinthya.birthdaycake.ui.components.GamePageScaffold
import com.cinthya.birthdaycake.ui.components.GamePageScope
import com.cinthya.birthdaycake.ui.components.IngredientBox
import com.cinthya.birthdaycake.ui.state.MiniHuntUiState
import com.cinthya.birthdaycake.ui.theme.BirthdayCakeTheme
import com.cinthya.birthdaycake.ui.theme.GameColors
import com.cinthya.birthdaycake.ui.viewmodel.MiniHuntViewModel
import kotlin.random.Random

/** 20 cards laid out five across, which is what fits a phone without scrolling. */
private const val COLUMN_COUNT = 5

private val CARD_GAP = 8.dp

/**
 * Memory game entry point. Holds no game state of its own - it collects the view model's
 * and forwards taps back to it.
 *
 * [onFinished] fires once, when the last pair is matched, and carries the collected
 * ingredients up to the game so the mixing page can use them.
 *
 * [onExpressionChange] reports what the cat should be doing, because the cat itself is
 * drawn by the scaffold - see GamePageScaffold for why there is only one of it.
 */
@Composable
fun GamePageScope.MiniHuntPage(
    modifier: Modifier = Modifier,
    viewModel: MiniHuntViewModel = viewModel(),
    onFinished: (List<IngredientsCount>) -> Unit = {},
    onExpressionChange: (CharacterExpressions) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isFinished) {
        if (uiState.isFinished) onFinished(uiState.collected)
    }

    LaunchedEffect(uiState.expression) {
        onExpressionChange(uiState.expression)
    }

    MiniHuntPage(uiState, viewModel::onCardClick, modifier)
}

/** The drawing half, kept free of the view model so previews can render any board. */
@Composable
private fun GamePageScope.MiniHuntPage(
    uiState: MiniHuntUiState,
    onCardClick: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HuntHeader(uiState)

        DashedDivider(Modifier.padding(vertical = 8.dp))

        // The recipe inventory, filling up as pairs are found.
        val collected = uiState.collected
        collected.chunked(3).forEach { row ->
            IngredientRow(row)
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(12.dp))

        CardGrid(uiState, onCardClick, Modifier.weight(1f))
        Spacer(Modifier.height(24.dp))

    }
}

@Composable
private fun GamePageScope.HuntHeader(uiState: MiniHuntUiState, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.hunt_moves, uiState.moves),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = GameColors.MainBlack
            )
            Text(
                stringResource(
                    R.string.hunt_pairs_found,
                    uiState.matchedPairs,
                    GameData.totalIngredients
                ),
                style = MaterialTheme.typography.labelMedium,
                color = GameColors.MainBlack
            )
        }

        // The cat is the scaffold's, drawn above the dialog scrim. A Dp size rather than a
        // width fraction, which in a weighted Row would measure against the leftover space.
        Spacer(Modifier.size(characterSize))
    }
}

@Composable
private fun IngredientRow(
    counts: List<IngredientsCount>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        counts.forEach { count ->
            IngredientBox(count.ingredients, count.currentAmount, Modifier.weight(1f))
        }
    }
}

/**
 * The board. Rows and cards divide the leftover space by weight rather than by fixed sizes,
 * so all 20 cards stay on screen at any window size - a memory game you have to scroll is
 * one you cannot play.
 */
@Composable
private fun CardGrid(
    uiState: MiniHuntUiState,
    onCardClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CARD_GAP, Alignment.CenterVertically)
    ) {
        uiState.cards.chunked(COLUMN_COUNT).forEach { rowCards ->
            Row(
                Modifier
                    .weight(1f, false)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CARD_GAP, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                rowCards.forEach { card ->
                    FlipCard(
                        card = card,
                        onClick = { onCardClick(card.id) },
                        modifier = Modifier
                            // fill = false lets the card stay narrower than its slot, so the
                            // aspect ratio can be honoured off the row height instead.
                            .weight(1f, fill = false)
                            .aspectRatio(CARD_ASPECT_RATIO, matchHeightConstraintsFirst = true)
                    )
                }
            }
        }
    }
}

/** Builds a reproducible board so the previews below show a specific moment in a game. */
private fun previewBoard(
    matchedPairs: Int = 0,
    revealed: Int = 0,
    moves: Int = 0
): MiniHuntUiState {
    val deck = GameData.buildDeck(random = Random(7))

    val matchedIds = deck
        .groupBy { it.ingredient.id }
        .values
        .flatMap { cards -> cards.chunked(2) }
        .take(matchedPairs)
        .flatten()
        .map { it.id }
        .toSet()

    val revealedIds = deck
        .filterNot { it.id in matchedIds }
        .take(revealed)
        .map { it.id }
        .toSet()

    return MiniHuntUiState(
        cards = deck.map { card ->
            card.copy(
                isFaceUp = card.id in matchedIds || card.id in revealedIds,
                isMatched = card.id in matchedIds
            )
        },
        moves = moves
    )
}

/** The board inside the page frame it really runs in, so previews include the cat. */
@Composable
private fun MiniHuntPreview(uiState: MiniHuntUiState) {
    BirthdayCakeTheme {
        GamePageScaffold(uiState.expression) {
            MiniHuntPage(uiState)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MiniHuntPagePrev() = MiniHuntPreview(previewBoard())

@Preview(showBackground = true)
@Composable
private fun MiniHuntPageMidGamePrev() =
    MiniHuntPreview(previewBoard(matchedPairs = 4, revealed = 2, moves = 11))

@Preview(showBackground = true)
@Composable
private fun MiniHuntPageFinishedPrev() =
    MiniHuntPreview(previewBoard(matchedPairs = 10, moves = 23))

@Preview(showBackground = true, widthDp = 320, heightDp = 640, name = "Small phone")
@Composable
private fun MiniHuntPageSmallPrev() = MiniHuntPreview(previewBoard(matchedPairs = 2))

@Preview(showBackground = true, widthDp = 411, heightDp = 891, name = "Large phone")
@Composable
private fun MiniHuntPageLargePrev() = MiniHuntPreview(previewBoard(matchedPairs = 2))
