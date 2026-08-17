package com.cinthya.birthdaycake.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.cinthya.birthdaycake.data.GameData.cakeIngredients
import com.cinthya.birthdaycake.model.Ingredients
import com.cinthya.birthdaycake.ui.theme.BirthdayCakeTheme
import com.cinthya.birthdaycake.ui.theme.GameColors

/** Chip aspect ratio, taken from img_chip_box so the frame never stretches oddly. */
private const val CHIP_ASPECT_RATIO = 3.4f

/** How much of the chip height the icon and the count take up. */
private const val CHIP_CONTENT_HEIGHT = 0.5f

private const val DISABLED_ALPHA = 0.45f

@Composable
fun IngredientBox(
    ingredients: Ingredients,
    currentCount: Int,
    modifier: Modifier = Modifier
) {
    val contentAlpha = if (currentCount > 0) 1f else DISABLED_ALPHA
    val currentCountString = currentCount
    Box(
        modifier.aspectRatio(CHIP_ASPECT_RATIO),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painterResource(R.drawable.img_chip_box),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.FillBounds,
            alpha = contentAlpha
        )

        Row(
            Modifier.fillMaxHeight(CHIP_CONTENT_HEIGHT),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Image(
                painterResource(ingredients.imageResourceId),
                contentDescription = stringResource(ingredients.nameResId),
                modifier = Modifier.fillMaxHeight().aspectRatio(1f),
                contentScale = ContentScale.Fit,
                alpha = contentAlpha
            )
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.ingredient_count, currentCountString),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = GameColors.MainBlack.copy(alpha = contentAlpha)
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 120)
@Composable
private fun IngredientBoxPrev() {
    BirthdayCakeTheme {
        IngredientBox(cakeIngredients.first(), 2, Modifier.fillMaxWidth())
    }
}
