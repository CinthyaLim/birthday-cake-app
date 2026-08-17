package com.cinthya.birthdaycake.model

/**
 * One tile on the memory board.
 *
 * [id] identifies the tile, not the symbol: the same ingredient appears on several
 * cards, so every lookup and every list key goes through [id].
 */
data class MemoryCard(
    val id: Int,
    val ingredient: Ingredients,
    val isFaceUp: Boolean = false,
    val isMatched: Boolean = false
)
