package com.cinthya.birthdaycake.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinthya.birthdaycake.data.GameData
import com.cinthya.birthdaycake.model.Ingredients
import com.cinthya.birthdaycake.ui.components.CARD_FLIP_DURATION_MS
import com.cinthya.birthdaycake.ui.state.MiniHuntUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/** How long a mismatched pair stays readable once it has finished turning over. */
private const val REVEAL_HOLD_MS = 700L

/**
 * Owns the whole memory game. The screen only forwards taps and draws [uiState].
 *
 * Both constructor parameters are defaulted, so Kotlin emits a no-arg constructor and
 * `viewModel()` can build this without a factory - while tests can still hand in a
 * seeded [Random] for a reproducible board.
 *
 * State lives here, so it survives rotation and every other configuration change. It is
 * deliberately not persisted across process death: a round lasts about a minute, and
 * because the "a miss is showing" flag is derived from the cards rather than stored, adding
 * a SavedStateHandle later would not require touching the logic below.
 */
class MiniHuntViewModel(
    private val ingredients: List<Ingredients> = GameData.cakeIngredients,
    private val random: Random = Random.Default
) : ViewModel() {

    private val _uiState = MutableStateFlow(MiniHuntUiState())
    val uiState: StateFlow<MiniHuntUiState> = _uiState.asStateFlow()

    private var flipBackJob: Job? = null

    init {
        startNewGame()
    }

    fun onRestart() = startNewGame()

    fun onCardClick(cardId: Int) {
        val state = _uiState.value
        val tapped = state.cards.firstOrNull { it.id == cardId } ?: return
        if (tapped.isFaceUp || tapped.isMatched) return

        // A miss is still on screen. Rather than swallow the tap, settle the old pair and
        // take this one in the same frame - waiting out the timer feels like a dropped tap.
        if (state.isMismatched) {
            flipBackJob?.cancel()
            val settling = state.revealed.map { it.id }.toSet()
            _uiState.update { current ->
                current.copy(
                    cards = current.cards.map { card ->
                        when {
                            card.id in settling -> card.copy(isFaceUp = false)
                            card.id == cardId -> card.copy(isFaceUp = true)
                            else -> card
                        }
                    }
                )
            }
            return
        }

        val flipped = state.cards.map { if (it.id == cardId) it.copy(isFaceUp = true) else it }
        val revealed = flipped.filter { it.isFaceUp && !it.isMatched }

        // First of the pair - nothing to judge yet.
        if (revealed.size < 2) {
            _uiState.update { it.copy(cards = flipped) }
            return
        }

        val (first, second) = revealed
        val isMatch = first.ingredient.id == second.ingredient.id

        _uiState.update { current ->
            current.copy(
                cards = if (isMatch) {
                    flipped.map {
                        if (it.id == first.id || it.id == second.id) it.copy(isMatched = true) else it
                    }
                } else {
                    flipped
                },
                moves = current.moves + 1
            )
        }

        if (!isMatch) scheduleFlipBack(first.id, second.id)
    }

    private fun startNewGame() {
        flipBackJob?.cancel()
        _uiState.value = MiniHuntUiState(cards = GameData.buildDeck(ingredients, random))
    }

    /**
     * Turns a missed pair back over. The wait covers the flip animation plus the time the
     * player actually needs to read the symbols, and the cards are re-checked by id
     * afterwards so a restart or an early resolve turns this into a no-op.
     */
    private fun scheduleFlipBack(firstId: Int, secondId: Int) {
        flipBackJob = viewModelScope.launch {
            delay(CARD_FLIP_DURATION_MS + REVEAL_HOLD_MS)
            _uiState.update { current ->
                val stillWaiting = listOf(firstId, secondId).all { id ->
                    current.cards.any { it.id == id && it.isFaceUp && !it.isMatched }
                }
                if (!stillWaiting) {
                    current
                } else {
                    current.copy(
                        cards = current.cards.map { card ->
                            if (card.id == firstId || card.id == secondId) {
                                card.copy(isFaceUp = false)
                            } else {
                                card
                            }
                        }
                    )
                }
            }
        }
    }
}
