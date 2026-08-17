package com.cinthya.birthdaycake.dialog

import com.cinthya.birthdaycake.data.DialogCatalog
import com.cinthya.birthdaycake.data.GameData
import com.cinthya.birthdaycake.model.CharacterExpressions
import com.cinthya.birthdaycake.model.IngredientsCount
import com.cinthya.birthdaycake.model.dialog.ActiveDialog
import com.cinthya.birthdaycake.ui.state.GameUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class GameUiStateTest {

    @Test
    fun `with no dialog the page decides the expression`() {
        val state = GameUiState(pageExpression = CharacterExpressions.SLEEPING)
        assertEquals(CharacterExpressions.SLEEPING, state.expression)
    }

    @Test
    fun `a dialog speaks for the cat while it is up`() {
        val state = GameUiState(
            pageExpression = CharacterExpressions.SLEEPING,
            dialog = ActiveDialog(DialogCatalog.intro)
        )

        assertEquals(DialogCatalog.intro.steps.first().expression, state.expression)
    }

    @Test
    fun `collected ingredients is the sum of what the hunt turned up`() {
        assertEquals(GameData.totalIngredients, stocked().collectedIngredients)
    }

    @Test
    fun `a fresh game has collected nothing`() {
        assertEquals(0, GameUiState().collectedIngredients)
    }

    @Test
    fun `the pantry is what is collected minus what is already in the bowl`() {
        val heart = GameData.cakeIngredients.first { it.id == "heart" }
        val state = stocked(bowl = mapOf("heart" to 2))

        assertEquals(
            heart.requiredAmount - 2,
            state.pantry.first { it.ingredients.id == "heart" }.currentAmount
        )
        // Everything else is untouched, and the hunt's record itself never moves.
        assertEquals(GameData.totalIngredients, state.collectedIngredients)
        assertEquals(GameData.totalIngredients - 2, state.pantry.sumOf { it.currentAmount })
    }

    @Test
    fun `the bowl counts every unit dropped into it`() {
        assertEquals(3, stocked(bowl = mapOf("heart" to 2, "smile" to 1)).bowlCount)
    }

    @Test
    fun `mixing waits until the whole recipe is in the bowl`() {
        assertEquals(false, stocked(bowl = mapOf("heart" to 3)).isMixReady)
        assertEquals(true, stocked(bowl = fullBowl).isMixReady)
    }

    /**
     * The case that rules out `bowlCount == collectedIngredients`: on the opening screen
     * both are zero, and an empty bowl must not read as ready.
     */
    @Test
    fun `a fresh game is not ready to mix`() {
        assertEquals(false, GameUiState().isMixReady)
    }

    @Test
    fun `the recipe card lists what is needed, not what is held`() {
        val state = GameUiState(inventory = GameData.emptyInventory)

        assertEquals(
            GameData.cakeIngredients.map { it.requiredAmount },
            state.recipeEntries.map { it.amount }
        )
    }

    /** A game just back from a cleared board, optionally partway through filling the bowl. */
    private fun stocked(bowl: Map<String, Int> = emptyMap()) = GameUiState(
        inventory = GameData.cakeIngredients.map { IngredientsCount(it, it.requiredAmount) },
        bowl = bowl
    )

    private val fullBowl = GameData.cakeIngredients.associate { it.id to it.requiredAmount }
}
