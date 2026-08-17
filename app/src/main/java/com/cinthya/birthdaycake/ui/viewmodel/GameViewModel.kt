package com.cinthya.birthdaycake.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinthya.birthdaycake.data.DialogCatalog
import com.cinthya.birthdaycake.model.CharacterExpressions
import com.cinthya.birthdaycake.model.GameScreen
import com.cinthya.birthdaycake.model.IngredientsCount
import com.cinthya.birthdaycake.model.dialog.ActiveDialog
import com.cinthya.birthdaycake.model.dialog.DialogSequence
import com.cinthya.birthdaycake.model.dialog.DialogSequenceId
import com.cinthya.birthdaycake.ui.state.GameUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * How long the final page is left alone before the closing dialog dims it. The cake and
 * "Yay! You Made It!" should land on their own first.
 */
private const val FINAL_PAGE_BEAT_MS = 900L

/**
 * The game above the level of any one page: which screen is up, what has been collected,
 * and which conversation is running.
 *
 * [MiniHuntViewModel] is untouched by this - it still owns the memory board. The two meet
 * once, when the board is cleared and [onHuntFinished] carries the result up here.
 *
 * State lives in a view model rather than in a `remember` because finishing a conversation
 * *changes the game*: the pantry dialog ends and the hunt begins. Screen and dialog have to
 * move in one update or the scrim flickers between them, and a composable holding one half
 * of that cannot promise it.
 *
 * Like [MiniHuntViewModel], this deliberately survives configuration change but not process
 * death. If that changes, the only thing that needs saving is the sequence id and the step
 * index - which is exactly why sequences are named by [DialogSequenceId] rather than passed
 * around as lists.
 */
class GameViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    init {
        // The intro plays over the mixing page, which is why the game opens there.
        show(DialogSequenceId.INTRO)
    }

    /** The only input the overlay has. A tap and a button press both mean "next". */
    fun onDialogAdvance() {
        val current = _uiState.value.dialog ?: return
        val next = current.next()

        if (next != null) {
            _uiState.update { it.copy(dialog = next) }
        } else {
            _uiState.update { it.copy(dialog = null) }
            onSequenceComplete(current.sequence.id)
        }
    }

    /** The board has been cleared. Bank what was found, then celebrate. */
    fun onHuntFinished(collected: List<IngredientsCount>) {
        if (_uiState.value.dialog != null) return // already celebrating

        _uiState.update { it.copy(inventory = collected) }
        show(DialogSequenceId.HUNT_WON)
    }

    /**
     * One unit of [id] moves from the chip row into the bowl.
     *
     * The guard sits inside the update so the read and the write cannot be split - the same
     * reason [MiniHuntViewModel] resolves its board inside one update.
     */
    fun onIngredientDropped(id: String) {
        _uiState.update { state ->
            val held = state.pantry.firstOrNull { it.ingredients.id == id }
            if (held == null || held.currentAmount <= 0) return@update state
            state.copy(bowl = state.bowl + (id to (state.bowl[id] ?: 0) + 1))
        }
    }

    fun onMixClick() {
        // The button is disabled until the bowl is full; the rule still belongs here.
        if (!_uiState.value.isMixReady) return

        goTo(GameScreen.FINAL)
        viewModelScope.launch {
            delay(FINAL_PAGE_BEAT_MS)
            show(DialogSequenceId.FINALE)
        }
    }

    /** Lets a page say what the cat should be doing while nobody is talking. */
    fun onPageExpression(expression: CharacterExpressions) {
        _uiState.update { it.copy(pageExpression = expression) }
    }

    private fun show(id: DialogSequenceId) {
        _uiState.update { it.copy(dialog = ActiveDialog(sequenceFor(id, it))) }
    }

    /** Static copy comes straight from the catalog; anything that reads the run is built here. */
    private fun sequenceFor(id: DialogSequenceId, state: GameUiState): DialogSequence = when (id) {
        DialogSequenceId.INTRO -> DialogCatalog.intro
        DialogSequenceId.RECIPE -> DialogCatalog.recipe(state.recipeEntries)
        DialogSequenceId.RAN_OUT -> DialogCatalog.ranOut(state.inventory)
        DialogSequenceId.HUNT_WON -> DialogCatalog.huntWon(state.collectedIngredients)
        DialogSequenceId.FINALE -> DialogCatalog.finale
    }

    /**
     * What a finished conversation *does* - the whole game's checkpoints on one screen.
     *
     * Keeping the consequences here is what lets a [com.cinthya.birthdaycake.model.dialog.DialogStep]
     * stay a lambda-free value type. Adding a checkpoint later is one enum constant, one
     * catalog entry, and one line in this table.
     */
    private fun onSequenceComplete(id: DialogSequenceId) = when (id) {
        DialogSequenceId.INTRO -> show(DialogSequenceId.RECIPE)
        DialogSequenceId.RECIPE -> show(DialogSequenceId.RAN_OUT)
        DialogSequenceId.RAN_OUT -> goTo(GameScreen.HUNT_INGREDIENTS)
        DialogSequenceId.HUNT_WON -> goTo(GameScreen.MIXING)
        // Terminal. The overlay fades out and the finished cake is where the game rests.
        DialogSequenceId.FINALE -> Unit
    }

    private fun goTo(screen: GameScreen) {
        _uiState.update { it.copy(screen = screen) }
    }
}
