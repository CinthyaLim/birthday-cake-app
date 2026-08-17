package com.cinthya.birthdaycake.model.dialog

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import com.cinthya.birthdaycake.model.CharacterExpressions
import com.cinthya.birthdaycake.model.Ingredients
import com.cinthya.birthdaycake.model.IngredientsCount

/**
 * A line of dialog copy: a string resource plus any integer arguments it takes, which is
 * every argument this game has ("%1$d/%2$d in stock").
 *
 * Args are `List<Int>` rather than `List<Any>` on purpose - it keeps this a pure value
 * type, which is what lets [DialogStep] be compared with `==`. The whole transition
 * depends on that comparison (see DialogOverlayHost).
 */
@Immutable
data class DialogText(
    @StringRes val resId: Int,
    val args: List<Int> = emptyList()
)

@Composable
fun DialogText.resolve(): String =
    if (args.isEmpty()) stringResource(resId) else stringResource(resId, *args.toTypedArray())

/**
 * One beat of a conversation.
 *
 * [expression] is always present - the cat is on screen for the whole game, so there is
 * always a face to choose. Everything else is optional and drawn only when non-null, so a
 * step can be a bare line of description, or a header and a button, or the whole recipe
 * card.
 *
 * Note what is *not* here: no lambdas. Two lambdas are never equal, so a click handler
 * stored on a step would make every step compare unequal to itself and fire a transition
 * on every recomposition - the exact blink this system exists to avoid. What a button does
 * is decided once, in GameViewModel.
 */
@Immutable
data class DialogStep(
    val expression: CharacterExpressions,
    /** Bold. */
    val header: DialogText? = null,
    /** Regular weight, under the header. */
    val desc: DialogText? = null,
    val extra: DialogExtra? = null,
    val button: DialogButton? = null,
    /** Small centred line drawn *below* the panel, outside the frame. */
    val footnote: DialogText? = null
) {
    /** With no button there is nothing to press, so the panel itself advances. */
    val advancesOnTap: Boolean get() = button == null
}

@Immutable
data class DialogButton(val label: DialogText)

/**
 * The structurally different block a step can carry, on top of its header and description.
 *
 * Sealed rather than a pile of nullable fields, because these carry different payloads and
 * only one can ever apply. Adding a variant is then a compile error in DialogStepBody,
 * which is exactly where the new branch belongs.
 */
@Immutable
sealed interface DialogExtra {

    /** The recipe card: a list of what the cake needs. */
    @Immutable
    data class Recipe(val entries: List<RecipeEntry>) : DialogExtra

    /** The pantry: a dashed panel holding a grid of stock slots. */
    @Immutable
    data class Pantry(
        val slots: List<IngredientsCount>,
        val columns: Int = 3
    ) : DialogExtra
}

/** One recipe line: "2x  [icon]  Hearts" on the left, a small muted "Love" on the right. */
@Immutable
data class RecipeEntry(
    val ingredient: Ingredients,
    val amount: Int
)
