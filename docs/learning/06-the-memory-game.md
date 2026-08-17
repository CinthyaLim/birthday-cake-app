# 06 — The Memory Game

## What you'll learn

- How the deck is built so the board matches the recipe exactly
- The match algorithm, and the "early resolve" that makes it feel responsive
- Cancellable coroutine jobs, and re-checking state after a delay
- The 3D card flip with `graphicsLayer` and `rotationY`
- Constructor defaults as poor-man's dependency injection

---

## The design constraint

The hunt has to produce **exactly** the ingredients the recipe calls for. Not more, not fewer.
If the board yielded 8 hearts, the mixing page would show 8 hearts against a recipe asking for
3, and the bowl would never reach exactly full.

So the deck isn't "some pairs" — it's derived from the recipe.

```kotlin
// GameData.kt:11
val cakeIngredients = listOf(
    Ingredients("heart",     ..., requiredAmount = 3),
    Ingredients("smile",     ..., requiredAmount = 2),
    Ingredients("sparkle",   ..., requiredAmount = 1),
    Ingredients("chocolate", ..., requiredAmount = 2),
    Ingredients("rainbow",   ..., requiredAmount = 1),
    Ingredients("ribbon",    ..., requiredAmount = 1),
)
// GameData.kt:20 — derived, not written down: 3 + 2 + 1 + 2 + 1 + 1 = 10
val totalIngredients = cakeIngredients.sumOf { it.requiredAmount }
```

```kotlin
// GameData.kt:34
fun buildDeck(
    ingredients: List<Ingredients> = cakeIngredients,
    random: Random = Random.Default
): List<MemoryCard> = ingredients
    .flatMap { ingredient -> List(ingredient.requiredAmount * 2) { ingredient } }
    .shuffled(random)
    .mapIndexed { index, ingredient -> MemoryCard(id = index, ingredient = ingredient) }
```

Read it as a pipeline:

1. **`flatMap { List(requiredAmount * 2) { it } }`** — one *pair* per unit needed. Heart needs
   3, so 6 heart cards. Total: 20 cards, 10 pairs.
2. **`shuffled(random)`** — randomise positions.
3. **`mapIndexed { index, ... }`** — assign ids.

### The ordering detail

> Ids come after the shuffle so they stay unique across repeated symbols.

There are six heart cards and they're all `Ingredients("heart", ...)` — the same object.
Numbering *after* the shuffle guarantees each of the 20 cards gets a distinct id (0..19)
regardless of duplication. Every lookup and every "is this the card I flipped?" check goes
through that id.

This is why `MemoryCard` documents:

```kotlin
// MemoryCard.kt:5
/**
 * [id] identifies the tile, not the symbol: the same ingredient appears on several cards,
 * so every lookup and every list key goes through [id].
 */
```

### The consequence: symbols repeat

> That means a symbol can repeat — three heart pairs share the board, and any two hearts match.
> Intentional, and what keeps the board tied to the recipe.

Six hearts are on the board and *any two* of them match. That's a departure from classic
memory, where each symbol appears exactly twice. It makes hearts easier to clear than ribbon
(which has exactly one pair), and it's the price of having the board produce the recipe. Worth
knowing that it's a conscious trade, not an oversight.

---

## Constructor defaults as injection

```kotlin
// MiniHuntViewModel.kt:33
class MiniHuntViewModel(
    private val ingredients: List<Ingredients> = GameData.cakeIngredients,
    private val random: Random = Random.Default
) : ViewModel()
```

Both parameters have defaults, which means Kotlin also emits a **no-argument constructor**.
That's what lets `viewModel()` build it with no factory:

```kotlin
// MiniHuntPage.kt:60
viewModel: MiniHuntViewModel = viewModel()
```

`viewModel()` uses reflection to find a no-arg constructor. A ViewModel with required
constructor parameters needs a `ViewModelProvider.Factory` — boilerplate, or Hilt.

And yet a test can still do:

```kotlin
MiniHuntViewModel(random = Random(42))   // reproducible board
```

The comment names the payoff:

> Both constructor parameters are defaulted, so Kotlin emits a no-arg constructor and
> `viewModel()` can build this without a factory — while tests can still hand in a seeded
> `Random` for a reproducible board.

**This is dependency injection without a DI framework.** For a project this size it's the right
call: Hilt would add a Gradle plugin, annotation processing, an `@HiltAndroidApp` class and
build-time cost, to solve a problem two default parameters already solve. Being able to say
*"I chose not to use Hilt, and here's the mechanism I used instead"* is stronger than having
used it reflexively.

Seeded randomness also drives the previews
([`MiniHuntPage.kt:197`](../../app/src/main/java/com/cinthya/birthdaycake/ui/screen/MiniHuntPage.kt)):

```kotlin
val deck = GameData.buildDeck(random = Random(7))
```

Every render of that preview shows the identical board.

---

## The match algorithm

[`MiniHuntViewModel.onCardClick`](../../app/src/main/java/com/cinthya/birthdaycake/ui/viewmodel/MiniHuntViewModel.kt),
line 49. Walk it in order.

### Guard

```kotlin
val state = _uiState.value
val tapped = state.cards.firstOrNull { it.id == cardId } ?: return
if (tapped.isFaceUp || tapped.isMatched) return
```

Unknown id → ignore. Already face-up or already matched → ignore. `FlipCard` also sets
`clickable(enabled = !card.isFaceUp && !card.isMatched)`, so this is the same
belt-and-braces as `onMixClick`: the UI prevents it, the logic enforces it.

### The early resolve ⭐

```kotlin
// MiniHuntViewModel.kt:56
// A miss is still on screen. Rather than swallow the tap, settle the old pair and take this
// one in the same frame — waiting out the timer feels like a dropped tap.
if (state.isMismatched) {
    flipBackJob?.cancel()
    val settling = state.revealed.map { it.id }.toSet()
    _uiState.update { current ->
        current.copy(
            cards = current.cards.map { card ->
                when {
                    card.id in settling -> card.copy(isFaceUp = false)  // hide the miss
                    card.id == cardId   -> card.copy(isFaceUp = true)   // show the new one
                    else                -> card
                }
            }
        )
    }
    return
}
```

This is the most player-focused code in the project.

**The scenario:** you flip two cards, they don't match, and a 1050ms timer starts to flip them
back. During that second, you already know your next move and you tap.

**The naive handling:** ignore the tap (it's not a valid move yet). To the player, the app just
*didn't respond*. They tap again. Now it might register twice. This is one of the most common
complaints about amateur memory games.

**What this does instead:** cancel the pending timer, flip the missed pair back down, and flip
the new card up — **all in one state update, one frame**. The tap is never lost. The game
always feels alive.

Two implementation details:

- `flipBackJob?.cancel()` stops the scheduled flip-back so it can't fire later and undo this.
- The three-way `when` inside a single `map` is what makes it one atomic update. Hiding the old
  pair and revealing the new card in two separate `update` calls would produce a frame where
  three cards are face-up.

`settling` is a `Set` rather than a `List` — `card.id in settling` is O(1) instead of O(n).
Over 20 cards it doesn't matter, but it's the right instinct.

### Judging the pair

```kotlin
val flipped = state.cards.map { if (it.id == cardId) it.copy(isFaceUp = true) else it }
val revealed = flipped.filter { it.isFaceUp && !it.isMatched }

// First of the pair — nothing to judge yet.
if (revealed.size < 2) {
    _uiState.update { it.copy(cards = flipped) }
    return
}

val (first, second) = revealed
val isMatch = first.ingredient.id == second.ingredient.id

_uiState.update { current ->
    current.copy(
        cards = if (isMatch) {
            flipped.map {
                if (it.id == first.id || it.id == second.id) it.copy(isMatched = true) else it
            }
        } else flipped,
        moves = current.moves + 1
    )
}

if (!isMatch) scheduleFlipBack(first.id, second.id)
```

- `val (first, second) = revealed` — **destructuring** a list. Kotlin's stdlib provides
  `component1()`/`component2()` on `List`, so this works. Safe here because the guard above
  proved `size >= 2`, and the early-resolve branch proved it can never exceed 2.
- Matching compares `ingredient.id`, not card `id` — two *different tiles* showing the *same
  symbol*.
- `moves` increments only when a **pair** is judged, so it counts attempts, not taps. Flipping
  the first card of a pair isn't a move.
- On a match, both cards get `isMatched = true` **in the same update that turns the second one
  over**. That's what keeps `isMismatched` correct: `revealed` filters out matched cards, so a
  matched pair immediately drops out of `revealed` and `revealed.size >= 2` is false. The
  timer is never scheduled for a match.

That interlock is documented in `MiniHuntUiState`:

> A matched pair never lands here: both cards are marked `isMatched` in the same update that
> turns the second one over, which drops them out of `revealed`.

---

## The flip-back job

```kotlin
// MiniHuntViewModel.kt:111
private fun scheduleFlipBack(firstId: Int, secondId: Int) {
    flipBackJob = viewModelScope.launch {
        delay(CARD_FLIP_DURATION_MS + REVEAL_HOLD_MS)   // 350 + 700 = 1050ms
        _uiState.update { current ->
            val stillWaiting = listOf(firstId, secondId).all { id ->
                current.cards.any { it.id == id && it.isFaceUp && !it.isMatched }
            }
            if (!stillWaiting) current
            else current.copy(
                cards = current.cards.map { card ->
                    if (card.id == firstId || card.id == secondId) card.copy(isFaceUp = false)
                    else card
                }
            )
        }
    }
}
```

### The duration

```kotlin
delay(CARD_FLIP_DURATION_MS + REVEAL_HOLD_MS)
```

Not a magic 1050. It's **flip time + reading time**:

- `CARD_FLIP_DURATION_MS = 350L` lives in `FlipCard.kt:28` — the same constant the animation
  uses. Change the flip speed and the wait follows automatically.
- `REVEAL_HOLD_MS = 700L` — "how long a mismatched pair stays readable once it has finished
  turning over."

The two constants are *separate* because they mean different things. Merging them into `1050L`
would lose that, and would silently break if the flip animation changed.

### Re-checking after the delay ⭐

```kotlin
val stillWaiting = listOf(firstId, secondId).all { id ->
    current.cards.any { it.id == id && it.isFaceUp && !it.isMatched }
}
if (!stillWaiting) current   // someone else got there first — do nothing
```

**This is the important part.** A second is a long time. During it:

- The player might tap a third card → the early-resolve already flipped these back
- The player might restart → `startNewGame` replaced the whole deck; ids 3 and 7 now mean
  different cards

If the coroutine blindly did `copy(isFaceUp = false)` on ids 3 and 7, it could flip down two
cards of the *new* board that the player had just legitimately turned over.

So it re-checks the state it's about to act on, inside the same `update` that would change it.
It only proceeds if both cards are still face-up and still unmatched.

> the cards are re-checked by id afterwards so a restart or an early resolve turns this into a
> no-op.

**The general lesson:** any delayed operation must re-validate its assumptions when it wakes up.
The world changed while it slept. `cancel()` covers the cases you thought of; the re-check
covers the ones you didn't.

Belt and braces again — `flipBackJob?.cancel()` in both `onCardClick` and `startNewGame`, *and*
the re-check. Cancellation isn't instantaneous (a coroutine only stops at a suspension point),
so having both is correct, not paranoid.

---

## The 3D flip

[`FlipCard.kt`](../../app/src/main/java/com/cinthya/birthdaycake/ui/components/FlipCard.kt):

```kotlin
val rotation by animateFloatAsState(
    targetValue = if (card.isFaceUp) 180f else 0f,
    animationSpec = tween(CARD_FLIP_DURATION_MS.toInt()),
    label = "cardFlip"
)
val showingFront = rotation >= FACE_SWAP_DEGREES   // 90f

Box(
    modifier
        .clickable(enabled = !card.isFaceUp && !card.isMatched, onClick = onClick)
        .graphicsLayer {
            rotationY = rotation
            cameraDistance = CAMERA_DISTANCE * density
        }
) {
    if (showingFront) {
        Image(
            painterResource(card.ingredient.cardResourceId),
            contentDescription = stringResource(card.ingredient.nameResId),
            modifier = Modifier.matchParentSize().graphicsLayer { rotationY = 180f },
            ...
        )
    } else {
        Image(painterResource(R.drawable.img_card_back), ...)
    }
}
```

**`animateFloatAsState`** — the simplest animation API in Compose. Give it a target; it returns
a `State<Float>` that eases toward it and recomposes as it goes. Change `isFaceUp` and the
rotation animates from 0° to 180° over 350ms.

**`rotationY`** — rotation about the vertical axis, in 3D. At 90° the card is edge-on and
invisible, which is exactly where you swap the face.

**`showingFront = rotation >= 90f`** — the face swaps at the halfway point, hidden by the
edge-on moment. That's what makes it read as one physical object turning over rather than two
images crossfading.

**`cameraDistance`** — how far the virtual camera is from the plane. Too close and the near
edge balloons (fish-eye); the comment says it pulls the vanishing point back far enough that
the turn looks flat and card-like. Multiplied by `density` because it's a physical distance.

**The counter-rotation** is the subtle one:

```kotlin
.graphicsLayer { rotationY = 180f }   // on the front image only
```

The parent is at 180° when the front is showing — which would render the art **mirrored**. The
child rotates another 180° to cancel it. It must be a *separate* `graphicsLayer` on the child;
folding it into the parent's value would cancel the animation itself.

**The clickable is outside the rotated layer.** From the comment:

> The click stays outside the rotated layer so hit testing is unaffected by the turn.

Hit testing happens in the layout's coordinate space. If the clickable were inside the rotation,
the touch target would shrink to nothing at 90°. Modifier order in Compose is
**outside-in** — earlier modifiers wrap later ones — so `.clickable` before `.graphicsLayer`
means the tap area is the unrotated rectangle.

**Accessibility detail:**

```kotlin
// Only name the ingredient once it is visible, or a screen reader gives the board away.
contentDescription = stringResource(card.ingredient.nameResId)   // front
contentDescription = stringResource(R.string.cd_hidden_card)     // back
```

A face-down card announces "hidden card." Labelling it with its symbol would let TalkBack read
out the whole solution. Genuinely thoughtful, and a nice thing to point at.

---

## The board layout

```kotlin
// MiniHuntPage.kt:167
uiState.cards.chunked(COLUMN_COUNT).forEach { rowCards ->   // 5 per row → 4 rows
    Row(Modifier.weight(1f, false).fillMaxWidth(), ...) {
        rowCards.forEach { card ->
            FlipCard(
                card = card,
                onClick = { onCardClick(card.id) },
                modifier = Modifier
                    .weight(1f, fill = false)
                    .aspectRatio(CARD_ASPECT_RATIO, matchHeightConstraintsFirst = true)
            )
        }
    }
}
```

No `LazyVerticalGrid`. A `Column` of `Row`s with weights, because:

> Rows and cards divide the leftover space by weight rather than by fixed sizes, so all 20
> cards stay on screen at any window size — a memory game you have to scroll is one you cannot
> play.

Lazy layouts exist to avoid composing off-screen items. **Nothing here is off-screen** — the
whole point is that all 20 are visible. A lazy grid would add scrolling, which is a bug here,
not a feature.

**`weight(1f, fill = false)`** — take a proportional share, but don't *have* to fill it. That
frees `aspectRatio` to make the card narrower than its slot so the art never stretches.

**`matchHeightConstraintsFirst = true`** — resolve the aspect ratio from the available *height*
rather than width. In a row that's already been given a height by its weight, that's what keeps
four rows fitting vertically.

---

## Self-check

1. Why are card ids assigned *after* the shuffle?
2. What exactly goes wrong if you delete `flipBackJob?.cancel()` from the early-resolve branch?
3. `scheduleFlipBack` re-checks the cards after its delay. Name two situations that make the
   re-check necessary.
4. Why does `isMismatched` never become true for a *matched* pair?
5. Why does the front image carry `rotationY = 180f`, and why can't that be folded into the
   parent?
6. Why a `Column` of `Row`s instead of `LazyVerticalGrid`?

<details>
<summary><b>Answers</b></summary>

1. Because the same `Ingredients` object appears on multiple cards — six heart cards are all
   the same instance. Numbering after the shuffle guarantees 20 distinct ids (0..19) regardless
   of duplication. Every lookup (`firstOrNull { it.id == cardId }`) and every "flip these two
   back" operation goes through the id, so uniqueness is a hard requirement.

2. The old timer stays alive. You tap a third card, the early-resolve flips the missed pair down
   and the new card up — and ~700ms later the orphaned coroutine wakes and flips down *its* two
   ids. In practice the `stillWaiting` re-check saves you (those cards are no longer face-up), so
   it's a no-op — but you'd be relying on the second safety net rather than the first. If the
   player happened to re-flip one of those exact cards in the interim, it would flip down under
   them.

3. (a) **Early resolve** — the player tapped a third card, the pair is already down, and these
   ids may now be legitimately face-up again. (b) **Restart** — `startNewGame` replaced the
   whole deck, so ids 3 and 7 now refer to entirely different cards, and blindly flipping them
   would turn down cards the player just revealed on the new board. The general rule: any
   delayed action must re-validate when it wakes.

4. Because `revealed` is `cards.filter { it.isFaceUp && !it.isMatched }`, and both cards of a
   match are marked `isMatched = true` **in the same state update** that turns the second one
   face-up. There is no intermediate state where two matched cards are face-up and unmatched, so
   `revealed.size >= 2` is never true for them. This is why `isMismatched` can be derived at all.

5. The parent `Box` is at 180° while the front is showing, which would render the artwork
   mirrored. The child rotates a further 180° to cancel it out. It must be a separate
   `graphicsLayer` because `graphicsLayer` blocks compose — putting `rotationY = rotation + 180f`
   on the parent would change what's animating, not add a correction on top of it.

6. Because nothing is ever off-screen, which is the entire reason lazy layouts exist. All 20
   cards must be visible simultaneously — a memory game you scroll is unplayable. The weighted
   `Column`/`Row` divides available space proportionally so the board fits any screen without
   scrolling, which is precisely what a lazy grid would *not* guarantee.

</details>

---

Next: [07 — Drag and Drop Mixing](07-drag-and-drop-mixing.md)
