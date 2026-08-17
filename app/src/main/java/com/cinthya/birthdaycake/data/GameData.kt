package com.cinthya.birthdaycake.data

import com.cinthya.birthdaycake.R
import com.cinthya.birthdaycake.model.Ingredients
import com.cinthya.birthdaycake.model.IngredientsCount
import com.cinthya.birthdaycake.model.MemoryCard
import kotlin.random.Random

object GameData {

    val cakeIngredients = listOf(
        Ingredients("heart", R.string.ingredient_heart, R.string.flavour_heart, R.drawable.img_heart, R.drawable.img_card_front_hearts, 3),
        Ingredients("smile", R.string.ingredient_smile, R.string.flavour_smile, R.drawable.img_smile, R.drawable.img_card_front_smiles, 2),
        Ingredients("sparkle", R.string.ingredient_sparkle, R.string.flavour_sparkle, R.drawable.img_sparkle, R.drawable.img_card_front_sparkles, 1),
        Ingredients("chocolate", R.string.ingredient_chocolate, R.string.flavour_chocolate, R.drawable.img_chocolate, R.drawable.img_card_front_choco, 2),
        Ingredients("rainbow", R.string.ingredient_rainbow, R.string.flavour_rainbow, R.drawable.img_rainbow, R.drawable.img_card_front_rainbow, 1),
        Ingredients("ribbon", R.string.ingredient_ribbon, R.string.flavour_ribbon, R.drawable.img_ribbon, R.drawable.img_card_front_ribbon, 1),
    )

    val totalIngredients = cakeIngredients.sumOf { it.requiredAmount }

    /** The recipe with nothing found yet - what the game starts with and the pantry shows. */
    val emptyInventory: List<IngredientsCount> =
        cakeIngredients.map { IngredientsCount(it, currentAmount = 0) }

    /**
     * Builds the memory board: one pair per unit the recipe calls for, so the number of
     * pairs equals [totalIngredients] and clearing the board collects exactly the amounts
     * in [cakeIngredients].
     *
     * That means a symbol can repeat - three heart pairs share the board, and any two
     * hearts match. Intentional, and what keeps the board tied to the recipe.
     */
    fun buildDeck(
        ingredients: List<Ingredients> = cakeIngredients,
        random: Random = Random.Default
    ): List<MemoryCard> = ingredients
        .flatMap { ingredient -> List(ingredient.requiredAmount * 2) { ingredient } }
        .shuffled(random)
        // Ids come after the shuffle so they stay unique across repeated symbols.
        .mapIndexed { index, ingredient -> MemoryCard(id = index, ingredient = ingredient) }
}
