package com.cinthya.birthdaycake.data

import com.cinthya.birthdaycake.R
import com.cinthya.birthdaycake.model.CharacterExpressions
import com.cinthya.birthdaycake.model.IngredientsCount
import com.cinthya.birthdaycake.model.dialog.ActiveDialog
import com.cinthya.birthdaycake.model.dialog.DialogButton
import com.cinthya.birthdaycake.model.dialog.DialogExtra
import com.cinthya.birthdaycake.model.dialog.DialogSequence
import com.cinthya.birthdaycake.model.dialog.DialogSequenceId
import com.cinthya.birthdaycake.model.dialog.DialogStep
import com.cinthya.birthdaycake.model.dialog.DialogText
import com.cinthya.birthdaycake.model.dialog.RecipeEntry

/**
 * Everything the cat says, in order.
 *
 * This is the script, and it is plain data - no Compose, no context, no lambdas. Adding a
 * line to a conversation is an edit to one list here plus a string in `strings.xml`;
 * nothing in the overlay, the frame or the animation needs to know about it.
 *
 * Sequences that read the run's state are functions; the rest are values.
 */
object DialogCatalog {

    val intro = DialogSequence(
        DialogSequenceId.INTRO,
        listOf(
            DialogStep(
                expression = CharacterExpressions.SLEEPING,
                header = DialogText(R.string.intro_sleep)
            ),
            DialogStep(
                expression = CharacterExpressions.YAWNING,
                header = DialogText(R.string.intro_ask)
            ),
            DialogStep(
                expression = CharacterExpressions.STAND_SIT,
                header = DialogText(R.string.intro_hello)
            ),
            DialogStep(
                expression = CharacterExpressions.IDLE_SIT,
                desc = DialogText(R.string.intro_who)
            ),
            DialogStep(
                expression = CharacterExpressions.SCRATCHING,
                desc = DialogText(R.string.intro_thinking)
            ),
            DialogStep(
                expression = CharacterExpressions.LAUGHING,
                header = DialogText(R.string.intro_today_header),
                desc = DialogText(R.string.intro_today_desc),
                button = DialogButton(DialogText(R.string.intro_today_button))
            ),
        )
    )

    val finale = DialogSequence(
        DialogSequenceId.FINALE,
        listOf(
            DialogStep(
                expression = CharacterExpressions.LAUGHING,
                header = DialogText(R.string.finale_header)
            ),
            DialogStep(
                expression = CharacterExpressions.WAG_TAIL,
                header = DialogText(R.string.intro_sleep)
            ),
            DialogStep(
                expression = CharacterExpressions.IDLE_STAND,
                header = DialogText(R.string.finale_ask)
            ),
            DialogStep(
                expression = CharacterExpressions.STAND_SIT,
                header = DialogText(R.string.finale_for_header)
            ),
            DialogStep(
                expression = CharacterExpressions.LAUGHING,
                header = DialogText(R.string.finale_every)
            ),
            DialogStep(
                expression = CharacterExpressions.LAUGHING,
                header = DialogText(R.string.finale_happy)
            ),
        )
    )

    /** The recipe card. One step, because the card is the whole point of it. */
    fun recipe(entries: List<RecipeEntry>) = DialogSequence(
        DialogSequenceId.RECIPE,
        listOf(
            DialogStep(
                expression = CharacterExpressions.IDLE_STAND,
                header = DialogText(R.string.recipe_title),
                extra = DialogExtra.Recipe(entries),
                button = DialogButton(DialogText(R.string.recipe_button)),
                footnote = DialogText(R.string.recipe_footnote)
            )
        )
    )

    /** The empty pantry. [stock] is live, so this cannot be a constant. */
    fun ranOut(stock: List<IngredientsCount>) = DialogSequence(
        DialogSequenceId.RAN_OUT,
        listOf(
            DialogStep(
                expression = CharacterExpressions.ALERT,
                header = DialogText(R.string.ran_out_header),
                desc = DialogText(R.string.ran_out_desc),
                extra = DialogExtra.Pantry(stock),
                button = DialogButton(DialogText(R.string.ran_out_button)),
                footnote = DialogText(R.string.ran_out_footnote)
            )
        )
    )

    fun huntWon(collectedCount: Int) = DialogSequence(
        DialogSequenceId.HUNT_WON,
        listOf(
            DialogStep(
                expression = CharacterExpressions.LAUGHING,
                header = DialogText(R.string.hunt_won_yay)
            ),
            DialogStep(
                expression = CharacterExpressions.STAND_SIT,
                header = DialogText(R.string.hunt_won_header),
                desc = DialogText(R.string.hunt_won_desc, listOf(collectedCount)),
                button = DialogButton(DialogText(R.string.hunt_won_button))
            )
        )
    )
}

/** Convenience for previews and tests: one step, standing alone. */
fun previewDialog(step: DialogStep): ActiveDialog =
    ActiveDialog(DialogSequence(DialogSequenceId.INTRO, listOf(step)))
