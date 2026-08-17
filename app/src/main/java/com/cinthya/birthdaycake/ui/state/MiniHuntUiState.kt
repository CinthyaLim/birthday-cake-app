package com.cinthya.birthdaycake.ui.state

import com.cinthya.birthdaycake.data.GameData
import com.cinthya.birthdaycake.model.CharacterExpressions
import com.cinthya.birthdaycake.model.IngredientsCount
import com.cinthya.birthdaycake.model.MemoryCard

/**
 * Everything the hunt screen draws.
 *
 * Only [cards] and [moves] are stored; every other value is computed from the board.
 * Keeping it that way means the counters can never drift out of step with the cards,
 * and [isMismatched] can never be left stuck on by a coroutine that did not run.
 */
data class MiniHuntUiState(
    val cards: List<MemoryCard> = emptyList(),
    val moves: Int = 0
) {

    /** Face-up cards still waiting to be judged. At most two at a time. */
    val revealed: List<MemoryCard>
        get() = cards.filter { it.isFaceUp && !it.isMatched }

    /**
     * A missed pair is still showing, so the board is waiting to settle. A matched pair
     * never lands here: both cards are marked [MemoryCard.isMatched] in the same update
     * that turns the second one over, which drops them out of [revealed].
     */
    val isMismatched: Boolean
        get() = revealed.size >= 2

    val totalPairs: Int
        get() = cards.size / 2

    val matchedPairs: Int
        get() = cards.count { it.isMatched } / 2

    val isFinished: Boolean
        get() = cards.isNotEmpty() && matchedPairs == totalPairs

    /** Pairs found per ingredient, in recipe order, ready for the inventory chips. */
    val collected: List<IngredientsCount>
        get() {
            val foundPerIngredient = cards
                .filter { it.isMatched }
                .groupingBy { it.ingredient.id }
                .eachCount()

            return GameData.cakeIngredients.map { ingredient ->
                IngredientsCount(ingredient, (foundPerIngredient[ingredient.id] ?: 0) / 2)
            }
        }

    /** Derived rather than stored, so the cat settles back down on its own. */
    val expression: CharacterExpressions
        get() = when {
            isFinished -> CharacterExpressions.LAUGHING
            isMismatched -> CharacterExpressions.ALERT
            else -> CharacterExpressions.IDLE_SIT
        }
}
