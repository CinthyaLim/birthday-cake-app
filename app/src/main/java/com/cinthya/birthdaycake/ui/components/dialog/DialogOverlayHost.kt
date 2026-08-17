package com.cinthya.birthdaycake.ui.components.dialog

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.Crossfade
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cinthya.birthdaycake.R
import com.cinthya.birthdaycake.data.DialogCatalog
import com.cinthya.birthdaycake.data.GameData
import com.cinthya.birthdaycake.data.previewDialog
import com.cinthya.birthdaycake.model.CharacterExpressions
import com.cinthya.birthdaycake.model.dialog.ActiveDialog
import com.cinthya.birthdaycake.model.dialog.DialogButton
import com.cinthya.birthdaycake.model.dialog.DialogStep
import com.cinthya.birthdaycake.model.dialog.DialogText
import com.cinthya.birthdaycake.model.dialog.resolve
import com.cinthya.birthdaycake.ui.components.CHARACTER_WIDTH_FRACTION
import com.cinthya.birthdaycake.ui.components.GamePageScaffold
import com.cinthya.birthdaycake.ui.screen.MixingPage
import com.cinthya.birthdaycake.ui.theme.BirthdayCakeTheme
import com.cinthya.birthdaycake.ui.theme.GameColors
import kotlin.math.abs
import kotlin.math.roundToInt

const val TAG_DIALOG_SCRIM = "dialog_scrim"
const val TAG_DIALOG_PANEL = "dialog_panel"

private const val SCRIM_FADE_MS = 220

/** The outgoing line clears out first, so two texts are never legible at once. */
private const val CONTENT_OUT_MS = 90

/** Then the incoming one fades and lifts in, while the frame is still settling. */
private const val CONTENT_IN_MS = 150

/** Panel height easing: a floor for short hops, scaled by distance, with a ceiling. */
private const val SIZE_MIN_MS = 220
private const val SIZE_MAX_MS = 420
private const val SIZE_MS_PER_PX = 0.35f

private const val CARET_BLINK_MS = 700

private const val PANEL_WIDTH = 0.96f

/** Never let a tall variant grow far enough to swallow the cat. */
private const val PANEL_MAX_HEIGHT = 0.74f

/**
 * The dialog layer: a scrim over the page, and a panel that never leaves while a
 * conversation runs.
 *
 * Deliberately an in-composition overlay rather than an `androidx` [androidx.compose.ui.window.Dialog].
 * A platform dialog is a separate window, so its dim would fall over the whole app - cat
 * included - and the only way to put the character back on top would be to draw a second
 * one inside the dialog window, which is the exact duplication this design removes. A
 * window boundary also makes a shared size animation impossible, and a hard cut between
 * steps is the one thing this is built to avoid.
 */
@Composable
fun DialogOverlayHost(
    active: ActiveDialog?,
    onAdvance: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    // Non-dismissable, half one. The empty body is the point: it exists to stop the event.
    BackHandler(enabled = active != null) { }

    // The overlay outlives `active` by one fade, so hold the last step to draw on the way
    // out - otherwise the panel empties before it has finished disappearing.
    var shown by remember { mutableStateOf(active) }
    if (active != null && active != shown) shown = active

    AnimatedVisibility(
        visible = active != null,
        enter = fadeIn(tween(SCRIM_FADE_MS)),
        exit = fadeOut(tween(SCRIM_FADE_MS)),
        modifier = modifier.fillMaxSize()
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val maxPanelHeight = maxHeight * PANEL_MAX_HEIGHT
            // Line the panel up under the cat, which the scaffold parks in the top corner.
            val topOffset = maxWidth * CHARACTER_WIDTH_FRACTION

            // Non-dismissable, half two. Its own node, below the panel in z.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(GameColors.Scrim)
                    .swallowGestures()
                    .testTag(TAG_DIALOG_SCRIM)
            )

            shown?.let { current ->
                DialogPanel(
                    active = current,
                    onAdvance = onAdvance,
                    maxPanelHeight = maxPanelHeight,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(contentPadding)
                        .padding(top = topOffset + 24.dp, start = 12.dp, end = 12.dp)
                        .fillMaxWidth(PANEL_WIDTH)
                )
            }
        }
    }
}

/**
 * The panel is anchored at its top edge, so growing into a taller step extends downward and
 * the header never shifts under the reader's eye.
 */
@Composable
private fun DialogPanel(
    active: ActiveDialog,
    onAdvance: () -> Unit,
    maxPanelHeight: Dp,
    modifier: Modifier = Modifier
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {

        // The bubble's tail, pointing up at the cat. Outside the frame, above it.
        Image(
            painterResource(R.drawable.img_dialog_arrow),
            contentDescription = stringResource(R.string.cd_dialog_tail),
            modifier = Modifier
                .align(Alignment.End)
                .padding(end = 28.dp)
                .height(10.dp),
            contentScale = ContentScale.FillHeight
        )

        DialogFrame(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxPanelHeight)
                // The tap target belongs to the frame, not the body: it must not blink in
                // and out with every step, and it has to cover the panel's padding too.
                .clickable(
                    enabled = active.step.advancesOnTap,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onAdvance
                )
                .testTag(TAG_DIALOG_PANEL)
        ) {
            AnimatedContent(
                targetState = active,
                // Cheap, explicit identity: two steps with identical copy still transition,
                // and a different sequence at index 0 still counts as a change.
                contentKey = { it.sequence.id to it.index },
                contentAlignment = Alignment.TopStart,
                transitionSpec = DialogStepTransition,
                label = "dialogStep",
                modifier = Modifier.fillMaxWidth()
            ) { target ->
                // Read ONLY from `target`. Touching the captured `active` here would make
                // the outgoing copy recompose with the new step, and the crossfade would
                // collapse into a flicker of the same text.
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    DialogStepBody(target.step, onAdvance)

                    if (target.step.advancesOnTap) {
                        ContinueCaret(Modifier.align(Alignment.End).padding(top = 8.dp))
                    }
                }
            }
        }

        // The footnote sits below the frame, so it cannot ride inside the AnimatedContent.
        // A crossfade on the same beat keeps it in step with the body.
        Crossfade(
            targetState = active.step.footnote,
            animationSpec = tween(CONTENT_IN_MS, delayMillis = CONTENT_OUT_MS),
            label = "dialogFootnote"
        ) { footnote ->
            if (footnote != null) {
                Column(Modifier
                    .fillMaxWidth()
                ) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        footnote.resolve(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(GameColors.PastelPink.copy(0.7f), RoundedCornerShape(16.dp))
                            .padding(8.dp),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        color = GameColors.MainBlack
                    )
                }

            }
        }
    }
}

/** The "tap to continue" caret, on steps that have no button to press instead. */
@Composable
private fun ContinueCaret(modifier: Modifier = Modifier) {
    val blink = rememberInfiniteTransition(label = "caret")
    val alpha by blink.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(CARET_BLINK_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "caretAlpha"
    )

    Image(
        painterResource(R.drawable.img_dialog_arrow),
        contentDescription = stringResource(R.string.cd_dialog_continue),
        modifier = modifier
            .size(14.dp)
            .alpha(alpha),
        contentScale = ContentScale.Fit
    )
}

/**
 * Advancing a step must not blink. Three things happen at once, and none of them is a fade
 * of the dialog as a whole:
 *
 *  - the old body clears out over [CONTENT_OUT_MS], with nothing fading *in* yet, because
 *    two lines of body copy legible at the same time read as mud rather than as a change;
 *  - the new body fades and lifts in, starting where the old one left off;
 *  - the frame's height eases over a duration scaled to how far it actually has to travel,
 *    so a one-word change is quick and the jump to the recipe card is not abrupt.
 *
 * `clip = true` is required: without it the taller incoming body spills past the border
 * before the frame has grown to meet it.
 *
 * Every future variant inherits this for free - [SizeTransform] interpolates an `IntSize`
 * and does not care what is inside.
 */
private val DialogStepTransition: AnimatedContentTransitionScope<ActiveDialog>.() -> ContentTransform = {
    val enter = fadeIn(tween(CONTENT_IN_MS, delayMillis = CONTENT_OUT_MS)) +
        slideInVertically(
            tween(CONTENT_IN_MS, delayMillis = CONTENT_OUT_MS, easing = FastOutSlowInEasing)
        ) { fullHeight -> fullHeight / 12 }

    val exit = fadeOut(tween(CONTENT_OUT_MS))

    enter togetherWith exit using SizeTransform(clip = true) { initialSize, targetSize ->
        val delta = abs(targetSize.height - initialSize.height)
        tween(
            durationMillis = (SIZE_MIN_MS + delta * SIZE_MS_PER_PX)
                .roundToInt()
                .coerceIn(SIZE_MIN_MS, SIZE_MAX_MS),
            easing = FastOutSlowInEasing
        )
    }
}

/**
 * Eats every gesture that lands on the scrim, so nothing behind the overlay - buttons,
 * memory cards, scrolls - ever sees the touch.
 *
 * The default (Main) pointer pass is deliberate. Main is delivered topmost-node-first, so
 * the panel, a later sibling, still receives its own taps; only what misses the panel
 * reaches here, and here it stops.
 */
private fun Modifier.swallowGestures(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false).consume()
        do {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
    }
}

// ---------------------------------------------------------------------------- previews

@Preview(showBackground = true, backgroundColor = 0xFFFCB8C3, name = "Desc only")
@Composable
private fun DialogDescOnlyPrev() {
    BirthdayCakeTheme {
        DialogOverlayHost(
            previewDialog(
                DialogStep(
                    CharacterExpressions.IDLE_SIT,
                    desc = DialogText(R.string.intro_who)
                )
            ),
            {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFCB8C3, name = "Header + desc + button")
@Composable
private fun DialogFullMessagePrev() {
    BirthdayCakeTheme {
        DialogOverlayHost(
            previewDialog(
                DialogStep(
                    CharacterExpressions.SCRATCHING,
                    header = DialogText(R.string.intro_today_header),
                    desc = DialogText(R.string.intro_today_desc),
                    button = DialogButton(DialogText(R.string.intro_today_button))
                )
            ),
            {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFCB8C3, name = "Recipe", heightDp = 891)
@Composable
private fun DialogRecipePrev() {
    BirthdayCakeTheme {
        DialogOverlayHost(
            ActiveDialog(
                DialogCatalog.recipe(
                    GameData.emptyInventory.map {
                        com.cinthya.birthdaycake.model.dialog.RecipeEntry(
                            it.ingredients,
                            it.ingredients.requiredAmount
                        )
                    }
                )
            ),
            {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFCB8C3, name = "Recipe small", widthDp = 320, heightDp = 640)
@Composable
private fun DialogRecipeSmallPrev() = DialogRecipePrev()

@Preview(showBackground = true, backgroundColor = 0xFFFCB8C3, name = "Ran out", heightDp = 891)
@Composable
private fun DialogRanOutPrev() {
    BirthdayCakeTheme {
        DialogOverlayHost(ActiveDialog(DialogCatalog.ranOut(GameData.emptyInventory)), {})
    }
}

/**
 * The one that matters: the overlay in the place it really runs, over a real page. It is
 * the only preview that shows the layering - the page dimmed by the scrim, the panel and
 * the cat above it, and exactly one cat on screen.
 */
@Preview(showBackground = true, name = "Over a page", heightDp = 891)
@Composable
private fun DialogOverPagePrev() {
    val dialog = ActiveDialog(DialogCatalog.intro, index = DialogCatalog.intro.steps.lastIndex)

    BirthdayCakeTheme {
        GamePageScaffold(
            expression = dialog.step.expression,
            overlay = { DialogOverlayHost(dialog, {}) }
        ) {
            MixingPage(GameData.emptyInventory, bowlCount = 0, isMixReady = false)
        }
    }
}
