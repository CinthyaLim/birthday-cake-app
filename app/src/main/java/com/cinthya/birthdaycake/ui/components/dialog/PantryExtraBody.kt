package com.cinthya.birthdaycake.ui.components.dialog

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cinthya.birthdaycake.R
import com.cinthya.birthdaycake.data.GameData
import com.cinthya.birthdaycake.model.IngredientsCount
import com.cinthya.birthdaycake.model.dialog.DialogExtra
import com.cinthya.birthdaycake.ui.components.dashedBorder
import com.cinthya.birthdaycake.ui.theme.BirthdayCakeTheme
import com.cinthya.birthdaycake.ui.theme.GameColors

/** Matches IngredientBox: a slot the player has nothing for reads as switched off. */
private const val EMPTY_ALPHA = 0.45f

private val SLOT_ICON_SIZE = 24.dp

/**
 * The pantry: a dashed panel with a header line and a grid of stock slots.
 *
 * The counts are live - this is the same [IngredientsCount] list the hunt fills in - which
 * is why it is composed rather than shipped as a flat image. It goes stale the moment the
 * first pair is matched.
 *
 * Laid out as chunked rows rather than a LazyVerticalGrid on purpose: a lazy container
 * inside the overlay's SizeTransform reports an unstable height and makes the panel's
 * resize jitter. Six fixed slots do not need laziness.
 */
@Composable
fun PantryExtraBody(extra: DialogExtra.Pantry, modifier: Modifier = Modifier) {
    val inStock = extra.slots.sumOf { it.currentAmount }
    val total = extra.slots.sumOf { it.ingredients.requiredAmount }

    Column(
        modifier
            .fillMaxWidth()
            .dashedBorder()
            .padding(12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.pantry_title),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = GameColors.MainBlack
            )
            Text(
                stringResource(R.string.pantry_stock, inStock, total),
                style = MaterialTheme.typography.labelSmall,
                color = GameColors.Muted
            )
        }

        Spacer(Modifier.height(10.dp))

        extra.slots.chunked(extra.columns).forEach { rowSlots ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                rowSlots.forEach { slot ->
                    PantrySlot(slot, Modifier.weight(1f))
                }
                // Keeps a short last row aligned with the ones above it.
                repeat(extra.columns - rowSlots.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PantrySlot(count: IngredientsCount, modifier: Modifier = Modifier) {
    val alpha = if (count.currentAmount > 0) 1f else EMPTY_ALPHA

    DialogFrame(
        modifier = modifier,
        fillColor = GameColors.AccentOne.copy(alpha = alpha),
        borderColor = GameColors.MainBlack.copy(alpha = alpha),
        borderWidth = 2.dp,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painterResource(count.ingredients.imageResourceId),
                contentDescription = null,
                modifier = Modifier.size(SLOT_ICON_SIZE),
                contentScale = ContentScale.Fit,
                alpha = alpha
            )
            Text(
                stringResource(
                    R.string.pantry_slot_count,
                    count.currentAmount,
                    count.ingredients.requiredAmount
                ),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = GameColors.MainBlack.copy(alpha = alpha)
            )
            Text(
                stringResource(count.ingredients.nameResId),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = GameColors.Muted.copy(alpha = alpha)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFF8F0, widthDp = 300, name = "Empty")
@Composable
private fun PantryExtraBodyEmptyPrev() {
    BirthdayCakeTheme {
        PantryExtraBody(DialogExtra.Pantry(GameData.emptyInventory), Modifier.padding(12.dp))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFF8F0, widthDp = 300, name = "Part full")
@Composable
private fun PantryExtraBodyPartialPrev() {
    BirthdayCakeTheme {
        PantryExtraBody(
            DialogExtra.Pantry(
                GameData.cakeIngredients.mapIndexed { index, ingredient ->
                    IngredientsCount(ingredient, if (index % 2 == 0) ingredient.requiredAmount else 0)
                }
            ),
            Modifier.padding(12.dp)
        )
    }
}
