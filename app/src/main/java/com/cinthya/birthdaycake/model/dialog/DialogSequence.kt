package com.cinthya.birthdaycake.model.dialog

import androidx.compose.runtime.Immutable

/**
 * Names a conversation.
 *
 * An enum rather than a string key so both `when`s that switch on it - the one that builds
 * the sequence and the one that decides what happens when it ends - are exhaustive. Adding
 * a checkpoint then fails to compile until both are handled.
 */
enum class DialogSequenceId {
    /** Opens over the mixing page: hello, and the birthday is today. */
    INTRO,

    /** The recipe card. */
    RECIPE,

    /** The pantry is empty, so we have to go and find everything. */
    RAN_OUT,

    /** The board has been cleared. */
    HUNT_WON,

    /** Closing words, over the finished cake. */
    FINALE
}

@Immutable
data class DialogSequence(
    val id: DialogSequenceId,
    val steps: List<DialogStep>
) {
    init {
        require(steps.isNotEmpty()) { "$id has no steps" }
    }
}

/**
 * Where the player is in a conversation.
 *
 * [step] and [isLast] are computed rather than stored - the same rule
 * [com.cinthya.birthdaycake.ui.state.MiniHuntUiState] follows - so the cursor and the
 * sequence cannot drift apart and [step] can never point past the end.
 */
@Immutable
data class ActiveDialog(
    val sequence: DialogSequence,
    val index: Int = 0
) {
    val step: DialogStep get() = sequence.steps[index]

    val isLast: Boolean get() = index == sequence.steps.lastIndex

    /** The next beat, or null when the conversation is over and the caller should act on it. */
    fun next(): ActiveDialog? = if (isLast) null else copy(index = index + 1)
}
