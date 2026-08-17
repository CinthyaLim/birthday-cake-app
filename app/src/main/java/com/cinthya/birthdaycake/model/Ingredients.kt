package com.cinthya.birthdaycake.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

data class Ingredients(
    val id : String,
    @StringRes val nameResId: Int,
    /** What this ingredient stands for - the muted label on the right of a recipe row. */
    @StringRes val flavourResId: Int,
    /** Bare icon, used by the inventory chips. */
    @DrawableRes val imageResourceId: Int,
    /** Whole pre-rendered card face, used by the memory game. */
    @DrawableRes val cardResourceId: Int,
    val requiredAmount: Int
)

data class IngredientsCount(
    val ingredients: Ingredients,
    val currentAmount: Int
)
