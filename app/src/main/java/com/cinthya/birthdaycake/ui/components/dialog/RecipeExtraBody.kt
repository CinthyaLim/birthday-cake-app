package com.cinthya.birthdaycake.ui.components.dialog

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cinthya.birthdaycake.R
import com.cinthya.birthdaycake.data.GameData
import com.cinthya.birthdaycake.model.dialog.DialogExtra
import com.cinthya.birthdaycake.model.dialog.RecipeEntry
import com.cinthya.birthdaycake.ui.components.DashedDivider
import com.cinthya.birthdaycake.ui.theme.BirthdayCakeTheme
import com.cinthya.birthdaycake.ui.theme.GameColors

private val ROW_ICON_SIZE = 20.dp

/**
 * The recipe card's middle: a divider, "Ingredients Needed:", one row per ingredient, and
 * a closing divider. The title, button and footnote come from the step's shared fields, so
 * this only draws the part that is unique to a recipe.
 */
@Composable
fun RecipeExtraBody(extra: DialogExtra.Recipe, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        DashedDivider(Modifier.padding(bottom = 10.dp))

        Text(
            stringResource(R.string.recipe_needed),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = GameColors.MainBlack
        )

        Spacer(Modifier.padding(top = 4.dp))

        extra.entries.forEach { entry ->
            RecipeRow(entry, Modifier.padding(top = 6.dp))
        }

        DashedDivider(Modifier.padding(top = 12.dp))
    }
}

/** "2x  [icon]  Hearts" on the left, a small muted "Love" on the right. */
@Composable
private fun RecipeRow(entry: RecipeEntry, modifier: Modifier = Modifier) {

    Box(
        modifier.fillMaxWidth()
            .padding(2.dp)
    ) {
        Image(painterResource(R.drawable.img_bg_recipe),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.FillBounds)
        Box(
            Modifier
                .fillMaxWidth()
                .padding(PaddingValues(horizontal = 10.dp, vertical = 7.dp)),
        ){

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.recipe_amount, entry.amount),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = GameColors.MainBlack
                )

                Spacer(Modifier.width(10.dp))

                Image(
                    painterResource(entry.ingredient.imageResourceId),
                    contentDescription = null,
                    modifier = Modifier.size(ROW_ICON_SIZE),
                    contentScale = ContentScale.Fit
                )

                Spacer(Modifier.width(8.dp))

                Text(
                    stringResource(entry.ingredient.nameResId),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = GameColors.MainBlack
                )

                Text(
                    stringResource(entry.ingredient.flavourResId),
                    style = MaterialTheme.typography.labelSmall,
                    color = GameColors.Muted
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFF8F0, widthDp = 300)
@Composable
private fun RecipeExtraBodyPrev() {
    BirthdayCakeTheme {
        RecipeExtraBody(
            DialogExtra.Recipe(GameData.cakeIngredients.map { RecipeEntry(it, it.requiredAmount) }),
            Modifier.padding(12.dp)
        )
    }
}
