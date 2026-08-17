# 02 — State in Compose

> This is the module that matters most. Everything else in the project is an application of
> what's here.

## What you'll learn

- What recomposition actually is, and what triggers it
- The three places state can live, and how to choose
- `StateFlow` + `collectAsStateWithLifecycle` — what each piece does
- **Stored vs derived state** — the single most important idea in this codebase
- `@Immutable`, `data class`, and why `copy()` is everywhere
- State hoisting, and how to spot when you got it wrong

---

## 1. Recomposition, honestly

A `@Composable` function is not like a normal function. Compose *calls it again* whenever
something it read has changed. That re-call is recomposition.

```kotlin
@Composable
fun Counter() {
    var count by remember { mutableStateOf(0) }   // ← a snapshot state object
    Text("$count")                                 // ← reading it subscribes this scope
    Button(onClick = { count++ }) { Text("+") }    // ← writing it invalidates that scope
}
```

Three things are happening:

1. **`mutableStateOf(0)`** creates an *observable* holder. Not a variable — a box Compose
   watches.
2. **`remember { ... }`** says "don't rebuild this box on every recomposition." Without it,
   every recomposition would create a fresh box holding `0` and the counter would never move.
3. **Reading `count`** inside a composable registers a subscription. When `count` changes,
   Compose re-runs the smallest enclosing scope that read it.

The mental correction most people need: **recomposition is not a redraw and it is not
cheap-by-magic.** It's a re-execution of your Kotlin code. Anything expensive you compute
inline runs again. Anything you allocate inline is allocated again.

### The trap this project is built to avoid

Recomposition happens *often* and at times you don't control. So:

- A value you create inline (`{ }` lambdas, `object : ...`, lists built with `map`) is a
  **new object every recomposition**.
- If a Compose API compares old and new to decide whether something changed, a new-every-time
  object means "always changed."

That's exactly the bug that shaped the dialog system. You'll see it in Module 04 (`DialogStep`
carries no lambdas) and Module 07 (`rememberUpdatedState` around a callback). Keep it in mind.

---

## 2. Where state can live

Three options, in increasing scope:

### (a) `remember { mutableStateOf(...) }` — local, disposable

Lives as long as the composable is in the composition. **Dies on rotation.**

Used in this project for genuinely local, throwaway UI facts:

```kotlin
// MixingPage.kt:86 — is a finger currently hovering over the bowl?
var isOver by remember { mutableStateOf(false) }
```

If you rotate mid-drag and lose "the bowl is highlighted," nobody cares. Correct choice.

### (b) `rememberSaveable { mutableStateOf(...) }` — local, survives rotation

Same, but written into the saved-instance-state `Bundle`. Not used in this project — nothing
local here is worth saving.

### (c) `ViewModel` — survives configuration change, outlives the composable

```kotlin
// GameViewModel.kt:45
private val _uiState = MutableStateFlow(GameUiState())
val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()
```

Used for both games' actual state. Rotate the phone and your cards stay flipped, your bowl
stays filled, the dialog stays on the same line.

### How this project chose

`GameViewModel`'s own doc comment states the reasoning ([`GameViewModel.kt:34`](../../app/src/main/java/com/cinthya/birthdaycake/ui/viewmodel/GameViewModel.kt)):

> State lives in a view model rather than in a `remember` because finishing a conversation
> *changes the game*: the pantry dialog ends and the hunt begins. Screen and dialog have to
> move in one update or the scrim flickers between them, and a composable holding one half
> of that cannot promise it.

Read that twice. The argument isn't "ViewModels are best practice." It's a *specific*
correctness argument: **two facts must change together, atomically.** If `screen` were in one
`remember` and `dialog` in another, there'd be a frame where the screen has changed but the
dialog hasn't, and you'd see a flicker.

That's how you should justify architecture in a portfolio project. Not "the docs say so" —
"here's the bug it prevents."

---

## 3. The `StateFlow` pipeline

Trace one value from the ViewModel to the screen:

```kotlin
// 1. GameViewModel.kt:45 — private mutable holder
private val _uiState = MutableStateFlow(GameUiState())

// 2. GameViewModel.kt:46 — public read-only view of the same holder
val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

// 3. MainActivity.kt:66 — the composable subscribes
val game by gameViewModel.uiState.collectAsStateWithLifecycle()

// 4. MainActivity.kt:69 — reading it registers the subscription
GamePageScaffold(expression = game.expression, ...)
```

Piece by piece:

**`MutableStateFlow`** — a hot flow that always holds exactly one current value. New
subscribers immediately get the latest. Perfect for "the current state of the screen."

**`asStateFlow()`** — returns a read-only wrapper. The underscore-prefixed `_uiState` is
private; the public `uiState` cannot be written to from outside. This is the ViewModel saying:
*I own this. You can watch it, you can send me events, you cannot set it.*

Without `asStateFlow()`, a composable could do `viewModel.uiState.value = ...` and your entire
UDF discipline evaporates.

**`collectAsStateWithLifecycle()`** — subscribes to the flow and converts it to a Compose
`State<T>`, so reads trigger recomposition. The `WithLifecycle` part matters: it stops
collecting when the app goes to the background (below `STARTED`) and resumes on return. Plain
`collectAsState()` keeps collecting while backgrounded, doing pointless work.

**`by`** — Kotlin property delegation. `val game by ...` unwraps `State<GameUiState>` so you
write `game.screen` instead of `game.value.screen`. It needs the
`import androidx.compose.runtime.getValue` you'll see at the top of these files — that import
is not decorative, and forgetting it is a confusing compile error.

### The update pattern

Every write in this project goes through `update`:

```kotlin
// GameViewModel.kt:133
private fun goTo(screen: GameScreen) {
    _uiState.update { it.copy(screen = screen) }
}
```

`update { }` takes the current value, gives it to your lambda, and atomically stores the
result — retrying if another thread changed it in between. Compare:

```kotlin
// Fragile — read and write are two separate steps
_uiState.value = _uiState.value.copy(screen = screen)

// Safe — read-modify-write is one atomic operation
_uiState.update { it.copy(screen = screen) }
```

This matters most where a *guard* and a *write* must not be split. Look at
[`GameViewModel.kt:80`](../../app/src/main/java/com/cinthya/birthdaycake/ui/viewmodel/GameViewModel.kt):

```kotlin
fun onIngredientDropped(id: String) {
    _uiState.update { state ->
        val held = state.pantry.firstOrNull { it.ingredients.id == id }
        if (held == null || held.currentAmount <= 0) return@update state  // ← guard
        state.copy(bowl = state.bowl + (id to (state.bowl[id] ?: 0) + 1)) // ← write
    }
}
```

The check "do we still have one of these?" and the write "put one in the bowl" happen inside
the same lambda. Pull the check outside and two fast drops could both pass the check and both
write — you'd get 11 ingredients into a 10-ingredient bowl.

`return@update state` is Kotlin's labelled return: *return from this lambda, with the state
unchanged.* That's the "do nothing" branch.

---

## 4. Stored vs derived state ⭐

**This is the big one.** If you take one idea from this whole module, take this.

Look at [`MiniHuntUiState.kt`](../../app/src/main/java/com/cinthya/birthdaycake/ui/state/MiniHuntUiState.kt):

```kotlin
data class MiniHuntUiState(
    val cards: List<MemoryCard> = emptyList(),   // ← STORED
    val moves: Int = 0                            // ← STORED
) {
    val revealed: List<MemoryCard>                // ← DERIVED
        get() = cards.filter { it.isFaceUp && !it.isMatched }

    val isMismatched: Boolean get() = revealed.size >= 2          // ← DERIVED
    val totalPairs: Int get() = cards.size / 2                    // ← DERIVED
    val matchedPairs: Int get() = cards.count { it.isMatched } / 2 // ← DERIVED
    val isFinished: Boolean                                        // ← DERIVED
        get() = cards.isNotEmpty() && matchedPairs == totalPairs
    val collected: List<IngredientsCount> get() = ...              // ← DERIVED
    val expression: CharacterExpressions get() = ...               // ← DERIVED
}
```

**Two stored fields. Seven derived.**

A naive version would store all nine as constructor parameters, and every one of them would be
a bug waiting to happen:

```kotlin
// The version you must NOT write
data class BadState(
    val cards: List<MemoryCard>,
    val moves: Int,
    val matchedPairs: Int,      // ← now you must remember to increment this
    val isFinished: Boolean,    // ← and set this
    val isMismatched: Boolean,  // ← and clear this, from a coroutine that might not run
)
```

Every stored field is a promise you have to keep on every single update. Miss one path — an
early return, a cancelled coroutine, a restart — and the counter says 8 pairs while the board
shows 9. That's a class of bug that simply cannot exist in the version above, because
`matchedPairs` *is* `cards.count { it.isMatched } / 2`. It can't disagree with the cards; it's
made of them.

The `isMismatched` case is the sharpest. In a stored version, you'd set `isMismatched = true`
when two cards don't match, and a coroutine would set it back to `false` 1050ms later. If that
coroutine gets cancelled — by a restart, by a rotation, by anything — the flag is stuck on
forever and the board is dead. Derived, that's impossible.

### The same rule, three more times

Once you see it, you see it everywhere in this project:

| Class | Stored | Derived |
|---|---|---|
| `MiniHuntUiState` | `cards`, `moves` | 7 values |
| `GameUiState` | `screen`, `inventory`, `bowl`, `dialog`, `pageExpression` | `expression`, `isDialogShowing`, `collectedIngredients`, `pantry`, `bowlCount`, `isMixReady`, `recipeEntries` |
| `ActiveDialog` | `sequence`, `index` | `step`, `isLast` |

`ActiveDialog` ([`DialogSequence.kt:47`](../../app/src/main/java/com/cinthya/birthdaycake/model/dialog/DialogSequence.kt)) is the tiniest and clearest example:

```kotlin
data class ActiveDialog(val sequence: DialogSequence, val index: Int = 0) {
    val step: DialogStep get() = sequence.steps[index]
    val isLast: Boolean get() = index == sequence.steps.lastIndex
}
```

Store *where you are* (`index`). Derive *what to show* (`step`). Storing the current step
alongside the index would let them drift apart.

### The `pantry` example is worth its own look

```kotlin
// GameUiState.kt:43
val pantry: List<IngredientsCount>
    get() = inventory.map {
        it.copy(currentAmount = it.currentAmount - (bowl[it.ingredients.id] ?: 0))
    }
```

Two stored facts: `inventory` (what the hunt found — written once, never touched again) and
`bowl` (what's been dropped in). "What's still on the chips" is neither of those — it's the
subtraction. So it isn't stored.

This means dragging an ingredient into the bowl **does not modify the inventory.** It adds to
`bowl`, and the chip count falls out automatically. Look back at `onIngredientDropped` — it
only touches `state.bowl`. One write, two visible effects, zero chance of them disagreeing.

The test at [`GameUiStateTest.kt:41`](../../app/src/test/java/com/cinthya/birthdaycake/dialog/GameUiStateTest.kt)
pins exactly this: after dropping 2 hearts, the pantry shows 1 heart but
`collectedIngredients` is still 10.

### When *not* to derive

Deriving recomputes on every read. Here, every derivation is a filter or a sum over ≤20 items,
which is free. If a derivation were genuinely expensive, the answer is
`derivedStateOf { }` (which caches until inputs change) — not storing it.

---

## 5. `@Immutable` and why `copy()` is everywhere

```kotlin
@Immutable
data class GameUiState(...)
```

`@Immutable` is a **promise to the compiler**: every public property of this type will hold
the same value forever, so if the reference hasn't changed, the content hasn't either.

Given that promise, Compose can skip recomposing a composable whose parameters are all `==` to
last time. Without it, Compose has to be conservative about types it can't verify — notably
`List<T>`, which is an *interface* that could be backed by a mutable list. `GameUiState` holds
two lists, so the annotation earns its place.

**If you lie, you get stale UI with no error.** Annotating a class that holds a `var` or an
`ArrayList` you mutate in place means Compose skips recomposition and your screen silently
stops updating. Only annotate types you never mutate.

Which is why every update in this codebase is a `copy()`:

```kotlin
state.copy(bowl = state.bowl + (id to ...))     // new state, new map
card.copy(isFaceUp = true)                       // new card
current.copy(index = index + 1)                  // new cursor
```

Never `state.bowl[id] = 1`. Never `card.isFaceUp = true`. Always a new value. That's what
makes `==` a reliable signal, which is what makes the whole recomposition-skipping system work
— and, in Module 05, what makes the dialog transition animate correctly.

---

## 6. State hoisting

**Hoisting** = moving state *up* to the lowest common ancestor that needs it, and passing the
value down + events up.

The unhoisted version:

```kotlin
@Composable
fun MixingPage() {
    var bowl by remember { mutableStateOf(emptyMap<String, Int>()) }   // ← trapped
}
```

The state is trapped. `MixingPage` can't be previewed in a specific state, can't be tested
without Compose, and nobody else can read the bowl.

The hoisted version, which is what the project does
([`MixingPage.kt:75`](../../app/src/main/java/com/cinthya/birthdaycake/ui/screen/MixingPage.kt)):

```kotlin
@Composable
fun GamePageScope.MixingPage(
    pantry: List<IngredientsCount>,      // ← state in
    bowlCount: Int,                       // ← state in
    isMixReady: Boolean,                  // ← state in
    modifier: Modifier = Modifier,
    onIngredientDropped: (String) -> Unit = {},   // ← events out
    onMixClick: () -> Unit = {}                   // ← events out
)
```

`MixingPage` is now a **pure function of its parameters**. That's why the file can end with
three `@Preview`s showing empty, stocked, and ready-to-mix states — each is just a different
set of arguments. No fake ViewModel, no test harness.

### Spotting the exception

`MiniHuntPage` breaks the pattern — it takes a ViewModel:

```kotlin
// MiniHuntPage.kt:58
fun GamePageScope.MiniHuntPage(
    modifier: Modifier = Modifier,
    viewModel: MiniHuntViewModel = viewModel(),
    onFinished: (List<IngredientsCount>) -> Unit = {},
    onExpressionChange: (CharacterExpressions) -> Unit = {}
)
```

...but look at line 74. It immediately delegates to a **private overload** that takes plain
state:

```kotlin
MiniHuntPage(uiState, viewModel::onCardClick, modifier)
```

This is the standard **stateful / stateless pair**. The public one wires up the ViewModel; the
private one draws. That's why `MiniHuntPage.kt` can have five previews of specific board
states (line 235 onward) built from a seeded deck — they call the stateless half.

Worth copying into your own future code. The rule: *only one composable per screen should know
a ViewModel exists.*

### `LaunchedEffect` — the escape hatch

```kotlin
// MiniHuntPage.kt:66
LaunchedEffect(uiState.isFinished) {
    if (uiState.isFinished) onFinished(uiState.collected)
}
```

Composables must be side-effect free — they can run any number of times. But "when the board
becomes finished, tell the game" *is* a side effect. `LaunchedEffect(key)` runs its block in a
coroutine when the composable enters composition, and **re-runs it only when `key` changes**.

So `isFinished` flipping `false → true` runs it once. Fifty recompositions with `isFinished`
still `true` run it zero more times. That's what stops `onFinished` from firing repeatedly.

(Belt and braces: `GameViewModel.onHuntFinished` also guards with
`if (_uiState.value.dialog != null) return`. Two independent protections against the same
double-fire. Defensible — the effect key protects against recomposition, the guard protects
against logic.)

---

## Self-check

1. Why does `MiniHuntUiState` store `moves` but derive `matchedPairs`? Both are counters.
2. What would break if `GameUiState.pantry` were a stored field updated in `onIngredientDropped`?
3. `_uiState.update { it.copy(...) }` vs `_uiState.value = _uiState.value.copy(...)` — when does
   the difference bite?
4. You add `@Immutable` to a class holding `val items: MutableList<String>` and mutate it with
   `items.add(...)`. What happens, and when do you notice?
5. Why does `MiniHuntPage` have two overloads with the same name?

<details>
<summary><b>Answers</b></summary>

1. `moves` is **not derivable from the cards.** Nothing on the board records how many attempts
   were made — a matched pair looks the same whether it was found on try 1 or try 30. It's an
   irreducible fact, so it's stored. `matchedPairs` *is* derivable (`count { isMatched } / 2`),
   so it isn't. The test is always: *can I compute this from what I already store?* If yes,
   don't store it.

2. Two bugs. First, you'd have to remember to update `pantry` in every path that touches
   `inventory` or `bowl` — miss one and the chips lie. Second, and worse: `inventory` and
   `pantry` would both claim to be the truth about how much you have, and the moment they
   disagree there's no way to tell which is right. Derived state has exactly one source of truth
   by construction.

3. When two updates race, or when a guard and a write must not be split. The `.value =` form
   reads, computes, then writes as three steps — another update landing in the middle is lost.
   `update { }` retries on conflict. In this single-threaded UI it rarely bites *today*, which
   is exactly why it's worth doing by habit: the day something async writes state, the code is
   already correct.

4. Compose sees the same list reference, believes your `@Immutable` promise, skips
   recomposition, and the new items never appear. **No crash, no warning, no log.** You notice
   when a user reports the screen "not updating," and you lose an afternoon. This is the single
   most dangerous annotation in Compose — it disables a safety check in exchange for
   performance.

5. The stateful/stateless split. The public one (line 58) takes a `MiniHuntViewModel`, collects
   its state, and runs the `LaunchedEffect`s. The private one (line 79) takes a plain
   `MiniHuntUiState` and only draws. That split is what makes the five previews at the bottom
   of the file possible — you can render a mid-game board, a finished board, and a small-phone
   board without ever constructing a ViewModel.

</details>

---

Next: [03 — The Game Flow](03-the-game-flow.md)
