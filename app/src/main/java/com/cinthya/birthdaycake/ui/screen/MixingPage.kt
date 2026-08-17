package com.cinthya.birthdaycake.ui.screen

import android.content.ClipData
import android.content.ClipDescription
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cinthya.birthdaycake.R
import com.cinthya.birthdaycake.data.GameData
import com.cinthya.birthdaycake.model.CharacterExpressions
import com.cinthya.birthdaycake.model.IngredientsCount
import com.cinthya.birthdaycake.ui.components.DashedDivider
import com.cinthya.birthdaycake.ui.components.GamePageScaffold
import com.cinthya.birthdaycake.ui.components.GamePageScope
import com.cinthya.birthdaycake.ui.components.IngredientBox
import com.cinthya.birthdaycake.ui.components.PixelButton
import com.cinthya.birthdaycake.ui.theme.BirthdayCakeTheme
import com.cinthya.birthdaycake.ui.theme.GameColors
import kotlin.collections.chunked

/** Everything below sizes itself off the page width so it scales instead of overflowing. */
private const val BOWL_WIDTH = 0.85f

/** How far the bowl swells while a finger is over it. The only "you can let go now" cue. */
private const val BOWL_HOVER_SCALE = 1.08f

/**
 * The dragged icon's side, as a fraction of the chip's height. Larger than the icon on the
 * chip itself (IngredientBox gives that CHIP_CONTENT_HEIGHT) so it stays readable under a
 * fingertip.
 */
private const val DRAG_ICON_FRACTION = 0.9f

/**
 * The bowl page. [pantry] is what is still on the chips, so a chip empties as its units are
 * dragged in; [bowlCount] is what has gone in, and drives the counter under the bowl.
 *
 * Dragging is started by the platform's long press - `dragAndDropSource` hard-codes that
 * detector and does not expose it - which is why the hint below the bowl says "hold".
 */
@Composable
fun GamePageScope.MixingPage(
    pantry: List<IngredientsCount>,
    bowlCount: Int,
    isMixReady: Boolean,
    modifier: Modifier = Modifier,
    onIngredientDropped: (String) -> Unit = {},
    onMixClick: () -> Unit = {}
) {
    // The callback arrives as a fresh method reference every recomposition. Holding it in
    // an updated state keeps the target below a single object without staling the lambda.
    val onDropped by rememberUpdatedState(onIngredientDropped)
    var isOver by remember { mutableStateOf(false) }

    val callback = remember {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                isOver = false
                val id = event.toAndroidDragEvent().clipData
                    ?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)?.text?.toString() ?: return false
                onDropped(id)
                return true
            }

            override fun onEntered(event: DragAndDropEvent) { isOver = true }
            override fun onExited(event: DragAndDropEvent) { isOver = false }
            override fun onEnded(event: DragAndDropEvent) { isOver = false }
        }
    }

    val bowlScale by animateFloatAsState(
        if (isOver) BOWL_HOVER_SCALE else 1f,
        label = "bowlHover"
    )

    Column(
        modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // The cat lives in the scaffold now, above the dialog scrim. This just keeps its
        // corner clear.
        Spacer(Modifier.size(characterSize))

        Spacer(Modifier.height(16.dp))

        Text(
            stringResource(R.string.available_ingredients),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = GameColors.MainBlack
        )

        DashedDivider(Modifier.padding(vertical = 8.dp))

        pantry.chunked(3).forEach { row ->
            IngredientRow(row)
            Spacer(Modifier.height(8.dp))
        }


        Column(modifier = Modifier.fillMaxWidth()
            .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center){
            Image(
                painterResource(R.drawable.img_bowl),
                contentDescription = stringResource(R.string.cd_mixing_bowl),
                modifier = Modifier
                    .weight(1f, false)
                    .fillMaxWidth(BOWL_WIDTH)
                    // Ahead of the padding, so those 12dp catch a near-miss instead of
                    // sitting outside the target. The scale is last: it moves the drawing
                    // only, leaving the drop bounds still while the bowl pulses.
                    .dragAndDropTarget(
                        shouldStartDragAndDrop = { event ->
                            event.mimeTypes()
                                .contains(ClipDescription.MIMETYPE_TEXT_PLAIN)
                        }, target = callback
                    )
                    .padding(vertical = 12.dp)
                    .scale(bowlScale)
                ,
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.height(16.dp))

            Text(
                stringResource(
                    R.string.drag_and_drop
                ),
                style = MaterialTheme.typography.labelMedium,
                color = GameColors.MainBlack
            )
        }

        Text(
            stringResource(
                R.string.ingredients_added,
                bowlCount,
                GameData.totalIngredients
            ),
            style = MaterialTheme.typography.labelMedium,
            color = GameColors.MainBlack
        )
        Spacer(Modifier.height(12.dp))

        PixelButton(
            stringResource(R.string.mix_it),
            onMixClick,
            Modifier.fillMaxWidth(),
            enabled = isMixReady
        )
    }
}

@Composable
private fun IngredientRow(
    counts: List<IngredientsCount>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        counts.forEach { count ->
            val canDrag = count.currentAmount > 0
            val painter = painterResource(count.ingredients.imageResourceId)

            IngredientBox(
                count.ingredients,
                count.currentAmount,
                Modifier
                    .weight(1f)
                    // Drawing the decoration by hand is deliberate. The overload that
                    // snapshots the composable for you does it by caching the chip into a
                    // graphics layer and painting every later frame from that recording,
                    // which freezes the count at whatever it was when the layer was first
                    // recorded. Returning null from transferData refuses the drag, which is
                    // what makes an empty chip unliftable.
                    .dragAndDropSource(
                        drawDragDecoration = {
                            // The shadow canvas is the whole chip, so centre the icon in it
                            // rather than drawing from the origin, which is the corner.
                            val side = size.height * DRAG_ICON_FRACTION
                            translate(
                                left = (size.width - side) / 2f,
                                top = (size.height - side) / 2f
                            ) {
                                with(painter) { draw(Size(side, side)) }
                            }
                        },
                        transferData = { _ ->
                            if (!canDrag) null
                            else DragAndDropTransferData(
                                ClipData.newPlainText("ingredient", count.ingredients.id)
                            )
                        }
                    )
            )
        }
    }
}

/** The stocked chips, as the page looks the moment the hunt hands them over. */
private val stockedPantry: List<IngredientsCount>
    get() = GameData.cakeIngredients.map { IngredientsCount(it, it.requiredAmount) }

@Preview(showBackground = true)
@Composable
private fun MixingPagePrev() {
    BirthdayCakeTheme {
        GamePageScaffold(CharacterExpressions.SLEEPING) {
            MixingPage(GameData.emptyInventory, bowlCount = 0, isMixReady = false)
        }
    }
}

@Preview(showBackground = true, name = "Stocked")
@Composable
private fun MixingPageStockedPrev() {
    BirthdayCakeTheme {
        GamePageScaffold(CharacterExpressions.WAG_TAIL) {
            MixingPage(stockedPantry, bowlCount = 0, isMixReady = false)
        }
    }
}

/** Everything in the bowl: dimmed chips and a live button, which only happen together. */
@Preview(showBackground = true, name = "Ready to mix")
@Composable
private fun MixingPageReadyPrev() {
    BirthdayCakeTheme {
        GamePageScaffold(CharacterExpressions.LAUGHING) {
            MixingPage(
                GameData.emptyInventory,
                bowlCount = GameData.totalIngredients,
                isMixReady = true
            )
        }
    }
}
