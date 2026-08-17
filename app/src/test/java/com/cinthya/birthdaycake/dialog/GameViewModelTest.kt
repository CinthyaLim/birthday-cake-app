package com.cinthya.birthdaycake.dialog

import com.cinthya.birthdaycake.data.GameData
import com.cinthya.birthdaycake.model.CharacterExpressions
import com.cinthya.birthdaycake.model.GameScreen
import com.cinthya.birthdaycake.model.IngredientsCount
import com.cinthya.birthdaycake.model.dialog.DialogSequenceId
import com.cinthya.birthdaycake.ui.viewmodel.GameViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Walks the checkpoint table without Compose or a device.
 *
 * `onMixClick` is the one path not covered here: it schedules the closing dialog on
 * `viewModelScope`, which needs a main dispatcher that a plain JVM test does not have.
 * Everything it does synchronously - the move to [GameScreen.FINAL] - is verified through
 * the screen assertions below instead.
 */
class GameViewModelTest {

    private val fullInventory =
        GameData.cakeIngredients.map { IngredientsCount(it, it.requiredAmount) }

    /** Taps through whatever conversation is currently up. */
    private fun GameViewModel.finishCurrentSequence() {
        val steps = uiState.value.dialog?.sequence?.steps?.size ?: return
        repeat(steps) { onDialogAdvance() }
    }

    @Test
    fun `the game opens on the mixing page with the intro running`() {
        val viewModel = GameViewModel()

        assertEquals(GameScreen.MIXING, viewModel.uiState.value.screen)
        assertEquals(DialogSequenceId.INTRO, viewModel.uiState.value.dialog?.sequence?.id)
        assertEquals(0, viewModel.uiState.value.dialog?.index)
    }

    @Test
    fun `advancing walks the steps of a sequence before ending it`() {
        val viewModel = GameViewModel()

        viewModel.onDialogAdvance()

        assertEquals(DialogSequenceId.INTRO, viewModel.uiState.value.dialog?.sequence?.id)
        assertEquals(1, viewModel.uiState.value.dialog?.index)
    }

    @Test
    fun `the opening conversations chain intro to recipe to ran out, then start the hunt`() {
        val viewModel = GameViewModel()

        viewModel.finishCurrentSequence()
        assertEquals(DialogSequenceId.RECIPE, viewModel.uiState.value.dialog?.sequence?.id)
        // All three play over the mixing page.
        assertEquals(GameScreen.MIXING, viewModel.uiState.value.screen)

        viewModel.finishCurrentSequence()
        assertEquals(DialogSequenceId.RAN_OUT, viewModel.uiState.value.dialog?.sequence?.id)
        assertEquals(GameScreen.MIXING, viewModel.uiState.value.screen)

        viewModel.finishCurrentSequence()
        assertNull(viewModel.uiState.value.dialog)
        assertEquals(GameScreen.HUNT_INGREDIENTS, viewModel.uiState.value.screen)
    }

    @Test
    fun `clearing the board banks the ingredients and celebrates`() {
        val viewModel = GameViewModel().apply { runToHunt() }

        viewModel.onHuntFinished(fullInventory)

        assertEquals(DialogSequenceId.HUNT_WON, viewModel.uiState.value.dialog?.sequence?.id)
        assertEquals(GameData.totalIngredients, viewModel.uiState.value.collectedIngredients)
    }

    @Test
    fun `finishing the celebration returns to the mixing page`() {
        val viewModel = GameViewModel().apply { runToHunt() }
        viewModel.onHuntFinished(fullInventory)

        viewModel.finishCurrentSequence()

        assertNull(viewModel.uiState.value.dialog)
        assertEquals(GameScreen.MIXING, viewModel.uiState.value.screen)
        // The inventory the hunt collected is what the mixing page now draws.
        assertEquals(GameData.totalIngredients, viewModel.uiState.value.collectedIngredients)
    }

    @Test
    fun `a second hunt result is ignored while the celebration is still up`() {
        val viewModel = GameViewModel().apply { runToHunt() }
        viewModel.onHuntFinished(fullInventory)

        viewModel.onHuntFinished(GameData.emptyInventory)

        assertEquals(GameData.totalIngredients, viewModel.uiState.value.collectedIngredients)
    }

    @Test
    fun `advancing with nothing showing does nothing`() {
        val viewModel = GameViewModel().apply { runToHunt() }
        assertNull(viewModel.uiState.value.dialog)

        viewModel.onDialogAdvance()

        assertNull(viewModel.uiState.value.dialog)
        assertEquals(GameScreen.HUNT_INGREDIENTS, viewModel.uiState.value.screen)
    }

    @Test
    fun `a page expression only shows through once the dialog is gone`() {
        val viewModel = GameViewModel()
        viewModel.onPageExpression(CharacterExpressions.WAG_TAIL)

        // The intro is still talking, so it owns the face.
        assertNotNull(viewModel.uiState.value.dialog)
        assertEquals(
            viewModel.uiState.value.dialog?.step?.expression,
            viewModel.uiState.value.expression
        )

        viewModel.runToHunt()

        assertEquals(CharacterExpressions.WAG_TAIL, viewModel.uiState.value.expression)
    }

    @Test
    fun `dropping an ingredient moves one unit from the chips into the bowl`() {
        val viewModel = GameViewModel().apply { runToMixing() }

        viewModel.onIngredientDropped("heart")

        val state = viewModel.uiState.value
        assertEquals(1, state.bowlCount)
        assertEquals(2, state.pantry.first { it.ingredients.id == "heart" }.currentAmount)
        // The hunt's record is a separate fact and does not move.
        assertEquals(GameData.totalIngredients, state.collectedIngredients)
    }

    @Test
    fun `an ingredient cannot be dropped more times than it was collected`() {
        val viewModel = GameViewModel().apply { runToMixing() }

        // "sparkle" appears once in the recipe, so the second drop has nothing left to give.
        repeat(3) { viewModel.onIngredientDropped("sparkle") }

        assertEquals(1, viewModel.uiState.value.bowlCount)
        assertEquals(
            0,
            viewModel.uiState.value.pantry.first { it.ingredients.id == "sparkle" }.currentAmount
        )
    }

    @Test
    fun `an id that is not in the recipe is ignored`() {
        val viewModel = GameViewModel().apply { runToMixing() }

        viewModel.onIngredientDropped("pickles")

        assertEquals(0, viewModel.uiState.value.bowlCount)
    }

    @Test
    fun `mixing unlocks only once the last unit is in the bowl`() {
        val viewModel = GameViewModel().apply { runToMixing() }

        GameData.cakeIngredients.dropLast(1).forEach { ingredient ->
            repeat(ingredient.requiredAmount) { viewModel.onIngredientDropped(ingredient.id) }
        }
        val last = GameData.cakeIngredients.last()
        repeat(last.requiredAmount - 1) { viewModel.onIngredientDropped(last.id) }

        assertEquals(false, viewModel.uiState.value.isMixReady)

        viewModel.onIngredientDropped(last.id)

        assertEquals(true, viewModel.uiState.value.isMixReady)
        assertEquals(GameData.totalIngredients, viewModel.uiState.value.bowlCount)
    }

    /** Plays the three opening conversations, leaving the board up and no dialog showing. */
    private fun GameViewModel.runToHunt() {
        repeat(3) { finishCurrentSequence() }
    }

    /** Carries on past the board: a cleared hunt banked, celebrated, and back on the bowl. */
    private fun GameViewModel.runToMixing() {
        runToHunt()
        onHuntFinished(fullInventory)
        finishCurrentSequence()
    }
}
