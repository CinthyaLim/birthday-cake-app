package com.cinthya.birthdaycake.ui.state

import androidx.compose.runtime.Immutable
import com.cinthya.birthdaycake.data.GameData
import com.cinthya.birthdaycake.model.CharacterExpressions
import com.cinthya.birthdaycake.model.GameScreen
import com.cinthya.birthdaycake.model.IngredientsCount
import com.cinthya.birthdaycake.model.dialog.ActiveDialog
import com.cinthya.birthdaycake.model.dialog.RecipeEntry

/**
 * The whole game, above the level of any one page: which page is up, what has been
 * collected, and who is talking.
 *
 * Same rule as [MiniHuntUiState] - only the irreducible facts are stored, everything else
 * is computed, so nothing can fall out of step.
 */
@Immutable
data class GameUiState(
    val screen: GameScreen = GameScreen.MIXING,
    /** What the hunt yielded. Written once, then left alone - dropping does not touch it. */
    val inventory: List<IngredientsCount> = GameData.emptyInventory,
    /** Ingredient id to how many units have gone into the bowl. */
    val bowl: Map<String, Int> = emptyMap(),
    val dialog: ActiveDialog? = null,
    /** What the current page wants the cat doing when nobody is talking. */
    val pageExpression: CharacterExpressions = CharacterExpressions.SLEEPING
) {

    /**
     * The one expression the app draws. A dialog speaks for the cat while it is up, which
     * is why there only ever needs to be a single character on screen.
     */
    val expression: CharacterExpressions
        get() = dialog?.step?.expression ?: pageExpression

    val isDialogShowing: Boolean get() = dialog != null

    /** How much the hunt turned up. What the win dialog reports, not what is in the bowl. */
    val collectedIngredients: Int get() = inventory.sumOf { it.currentAmount }

    /** What is left on the chips: collected, minus whatever has already gone in the bowl. */
    val pantry: List<IngredientsCount>
        get() = inventory.map {
            it.copy(currentAmount = it.currentAmount - (bowl[it.ingredients.id] ?: 0))
        }

    val bowlCount: Int get() = bowl.values.sum()

    /**
     * Measured against the recipe rather than against [collectedIngredients], which is also
     * zero before the hunt - that comparison would call an empty bowl ready on the opening
     * screen. Clearing the board always yields the full recipe, so this is reachable.
     */
    val isMixReady: Boolean get() = bowlCount == GameData.totalIngredients

    /** The recipe as the card wants it: what is needed, not what has been found. */
    val recipeEntries: List<RecipeEntry>
        get() = inventory.map { RecipeEntry(it.ingredients, it.ingredients.requiredAmount) }
}
