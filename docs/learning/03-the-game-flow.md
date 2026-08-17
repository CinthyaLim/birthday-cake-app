# 03 — The Game Flow

## What you'll learn

- How `GameViewModel` drives the entire game with five public methods
- The **checkpoint table** — the whole plot in one `when`
- How screens swap with no navigation library, and when that stops being enough
- Why the cat's expression has two sources and how they're resolved
- The one place a `delay` is used, and why

---

## The whole game in five methods

Open [`GameViewModel.kt`](../../app/src/main/java/com/cinthya/birthdaycake/ui/viewmodel/GameViewModel.kt).
It's 136 lines including comments, and its entire public surface is:

```kotlin
val uiState: StateFlow<GameUiState>

fun onDialogAdvance()                                  // player tapped the dialog
fun onHuntFinished(collected: List<IngredientsCount>)  // board cleared
fun onIngredientDropped(id: String)                    // something went in the bowl
fun onMixClick()                                       // Mix It! pressed
fun onPageExpression(expression: CharacterExpressions) // page suggests a cat mood
```

Five events. That's every input the game can receive. Everything else — which screen, which
dialog, what's in the bowl, whether Mix is enabled — is a consequence computed from those.

That's a genuinely good sign in a codebase. When you can list every way state can change on
one hand, you can reason about all of them.

---

## The state, and where it starts

```kotlin
// GameUiState.kt:19
@Immutable
data class GameUiState(
    val screen: GameScreen = GameScreen.MIXING,
    val inventory: List<IngredientsCount> = GameData.emptyInventory,
    val bowl: Map<String, Int> = emptyMap(),
    val dialog: ActiveDialog? = null,
    val pageExpression: CharacterExpressions = CharacterExpressions.SLEEPING
)
```

Every field has a default, so `GameUiState()` is a valid new game: mixing screen, empty
pantry, empty bowl, no dialog, cat asleep.

Then the ViewModel's `init` block runs:

```kotlin
// GameViewModel.kt:48
init {
    // The intro plays over the mixing page, which is why the game opens there.
    show(DialogSequenceId.INTRO)
}
```

That comment answers a question you'd otherwise ask: *why does the game start on MIXING and
not on some intro screen?* Because there is no intro screen. The intro is a **dialog over the
mixing page**, which the player can see dimmed behind the scrim. It's a nice touch — you see
where you are before you're told what to do.

---

## The checkpoint table

Here's the heart of it ([`GameViewModel.kt:124`](../../app/src/main/java/com/cinthya/birthdaycake/ui/viewmodel/GameViewModel.kt)):

```kotlin
private fun onSequenceComplete(id: DialogSequenceId) = when (id) {
    DialogSequenceId.INTRO    -> show(DialogSequenceId.RECIPE)
    DialogSequenceId.RECIPE   -> show(DialogSequenceId.RAN_OUT)
    DialogSequenceId.RAN_OUT  -> goTo(GameScreen.HUNT_INGREDIENTS)
    DialogSequenceId.HUNT_WON -> goTo(GameScreen.MIXING)
    DialogSequenceId.FINALE   -> Unit   // terminal
}
```

**The entire plot of the game is these five lines.** Read them top to bottom and you have the
story: intro leads to the recipe, the recipe leads to the empty pantry, the empty pantry sends
you hunting, winning the hunt brings you back to mix, and the finale ends.

Three properties worth noticing:

**It's exhaustive.** `DialogSequenceId` is an enum, and the `when` is used as an expression
(it's the body of `= when (id)`), so Kotlin requires every constant to be handled. Add a sixth
sequence and this file **stops compiling** until you say what happens when it ends. The
compiler enforces that you finish the thought.

**It's the only place consequences live.** No dialog step carries an `onFinish` lambda. No
composable decides what comes next. If you want to know what happens after the recipe card,
there is exactly one place to look. This is the payoff for the design in Module 04.

**`FINALE -> Unit`** is doing real work. Kotlin needs every branch of an expression-`when` to
produce a value, and `Unit` means "nothing happens." Deleting the branch wouldn't compile;
this is the explicit statement that the game ends here.

### Where it's triggered

```kotlin
// GameViewModel.kt:54
fun onDialogAdvance() {
    val current = _uiState.value.dialog ?: return   // nothing showing → ignore
    val next = current.next()

    if (next != null) {
        _uiState.update { it.copy(dialog = next) }  // same sequence, next step
    } else {
        _uiState.update { it.copy(dialog = null) }  // sequence over: clear it...
        onSequenceComplete(current.sequence.id)     // ...then act on it
    }
}
```

The order in the `else` matters. Clear the dialog **first**, then run the checkpoint. Because
`onSequenceComplete` might call `show(...)`, which sets a *new* dialog — and if you ran it
before clearing, the clear would wipe out the dialog you just opened.

Try it mentally: `INTRO` ends → `show(RECIPE)` sets `dialog = RECIPE` → then
`copy(dialog = null)` erases it. The game would freeze with no dialog and no way to continue.
One line of ordering is the difference between a working game and a soft-lock.

The `?: return` on the first line is a small piece of defensiveness, and there's a test for it:
*"advancing with nothing showing does nothing."* A stray tap during a screen transition can't
crash you.

---

## Building sequences: static vs live

```kotlin
// GameViewModel.kt:109
private fun sequenceFor(id: DialogSequenceId, state: GameUiState): DialogSequence = when (id) {
    DialogSequenceId.INTRO    -> DialogCatalog.intro                          // value
    DialogSequenceId.RECIPE   -> DialogCatalog.recipe(state.recipeEntries)    // function
    DialogSequenceId.RAN_OUT  -> DialogCatalog.ranOut(state.inventory)        // function
    DialogSequenceId.HUNT_WON -> DialogCatalog.huntWon(state.collectedIngredients) // function
    DialogSequenceId.FINALE   -> DialogCatalog.finale                         // value
}
```

Two kinds of dialog:

- **Values** (`intro`, `finale`) — the script never changes. Built once as an `object`
  property, reused forever.
- **Functions** (`recipe`, `ranOut`, `huntWon`) — the content depends on the current run. The
  win dialog says "You found **10** things!", and that number comes from state.

The state is passed *in* as a parameter rather than read from `_uiState.value` inside, so this
function is pure — same inputs, same output. That's a small thing that makes it trivially
testable.

Note the two `when`s in this file switch on the same enum with the same five branches. That's
not duplication to eliminate; they answer different questions (*what does it say?* vs *what
does it do?*), and having both exhaustive means adding a sequence produces two compile errors
in exactly the two places you need to think.

---

## Screen switching without a navigation library

```kotlin
// MainActivity.kt:80
when (game.screen) {
    GameScreen.MIXING -> MixingPage(
        pantry = game.pantry,
        bowlCount = game.bowlCount,
        isMixReady = game.isMixReady,
        onIngredientDropped = gameViewModel::onIngredientDropped,
        onMixClick = gameViewModel::onMixClick
    )
    GameScreen.HUNT_INGREDIENTS -> MiniHuntPage(
        onFinished = gameViewModel::onHuntFinished,
        onExpressionChange = gameViewModel::onPageExpression
    )
    GameScreen.FINAL -> FinalPage()
}
```

No Navigation Compose. No routes, no `NavHost`, no back stack. An enum and a `when`.

**This is the right call here**, and you should be able to defend it:

- Three screens, strictly linear, no branching
- No deep links, no URLs, no arguments to serialize
- **No back navigation at all** — the game deliberately blocks it (see Module 05)
- Nav Compose would add a dependency and ceremony to solve problems this app doesn't have

**When it would stop being enough:** the moment you want a back stack, deep links, a settings
screen you can return from, or transition animations between screens. At that point the `when`
becomes a liability and you'd migrate. Knowing *where* the line is, is more impressive than
having picked a library.

Note also `gameViewModel::onIngredientDropped` — a **method reference**, not
`{ id -> gameViewModel.onIngredientDropped(id) }`. Same behaviour, but it reads better and it's
the idiomatic form when you're just forwarding.

---

## The cat's expression: two sources, one winner

The cat is on screen for the entire game and always needs a mood. Two things want to control
it:

- A **dialog** — each `DialogStep` carries an expression, so the cat acts out its lines
- A **page** — the memory game wants an alert cat on a miss, a laughing cat on a win

Resolved in one derived property ([`GameUiState.kt:34`](../../app/src/main/java/com/cinthya/birthdaycake/ui/state/GameUiState.kt)):

```kotlin
val expression: CharacterExpressions
    get() = dialog?.step?.expression ?: pageExpression
```

**A dialog always wins.** If someone's talking, they own the face. Otherwise the page decides.
One line, no flags, no priority system.

The flow from the hunt is worth tracing:

```
MiniHuntUiState.expression (derived from the board)
        ↓  LaunchedEffect(uiState.expression)
MiniHuntPage → onExpressionChange(...)
        ↓
GameViewModel.onPageExpression → state.pageExpression
        ↓
GameUiState.expression → (only if no dialog)
        ↓
GamePageScaffold → CharacterPlaceholder
```

The mini-game never draws a cat. It *reports a mood* and something else draws it. Module 08
explains why that's a hard rule here.

There's a test for the precedence at
[`GameViewModelTest.kt:115`](../../app/src/test/java/com/cinthya/birthdaycake/dialog/GameViewModelTest.kt):
set `pageExpression` to `WAG_TAIL` while the intro is running, assert the dialog's expression
wins, run past the dialogs, assert `WAG_TAIL` now shows through. The `pageExpression` was
stored the whole time — it just wasn't winning.

---

## The one `delay` in the game

```kotlin
// GameViewModel.kt:88
fun onMixClick() {
    if (!_uiState.value.isMixReady) return

    goTo(GameScreen.FINAL)
    viewModelScope.launch {
        delay(FINAL_PAGE_BEAT_MS)          // 900ms
        show(DialogSequenceId.FINALE)
    }
}
```

With no delay, you'd tap Mix and the cake, the confetti, the title *and* a dimming scrim with a
dialog would all appear in the same frame. You'd never see the cake — the thing the whole game
was building toward.

So: show the final page, let it land for 900ms, *then* dim it and talk. The constant is named
and documented at the top of the file:

> How long the final page is left alone before the closing dialog dims it. The cake and
> "Yay! You Made It!" should land on their own first.

**`viewModelScope`** is a `CoroutineScope` tied to the ViewModel's lifetime. If the ViewModel
is cleared while the delay is pending, the coroutine is cancelled automatically. No leak, no
callback firing into a dead object.

Also note the guard on line 90:

```kotlin
if (!_uiState.value.isMixReady) return
```

The button is already disabled when the bowl isn't full — so this looks redundant. It isn't.
The button being disabled is a *UI affordance*; this is the *rule*. If a future refactor,
an accessibility service, or a test calls `onMixClick()` directly, the rule still holds. The
comment on line 89 says exactly this: *"The button is disabled until the bowl is full; the rule
still belongs here."*

**General principle:** never let a disabled button be the only thing enforcing a rule.

---

## Self-check

1. In `onDialogAdvance`, why must `copy(dialog = null)` run *before* `onSequenceComplete`?
2. Add a `THANK_YOU` sequence after `FINALE`. What breaks, and is that good or bad?
3. Why is `HUNT_WON -> goTo(GameScreen.MIXING)` and not `goTo(GameScreen.FINAL)`?
4. `onIngredientDropped("pickles")` — trace what happens.
5. The final page shows for 900ms before the finale dialog. Where else could that delay have
   lived, and why is the ViewModel the right home?

<details>
<summary><b>Answers</b></summary>

1. Because `onSequenceComplete` may call `show(...)`, which sets a new dialog. Running the
   clear afterwards would immediately erase it, leaving the game with no dialog and no way to
   advance — a soft-lock. Clear first, then let the checkpoint decide what (if anything) opens
   next.

2. Adding the enum constant breaks compilation in **two** places: `sequenceFor` (what does it
   say?) and `onSequenceComplete` (what happens when it ends?). That's *good* — those are
   exactly the two decisions you must make, and the compiler refuses to let you forget either.
   Compare with a `Map<DialogSequenceId, DialogSequence>`, where a missing entry compiles fine
   and crashes at runtime. This is a real argument for enums + exhaustive `when` over map
   lookups.

3. Because the bowl still has to be filled. The hunt gives you ingredients; it doesn't bake.
   Going straight to FINAL would skip the drag-and-drop half of the game entirely. The
   checkpoint table encodes that the mixing page is visited twice, which is exactly the kind of
   plot fact you want in one readable place.

4. `_uiState.update` runs. `state.pantry.firstOrNull { it.ingredients.id == "pickles" }` returns
   `null` (no such ingredient). The guard `held == null` hits, and `return@update state` returns
   the state unchanged. Nothing happens, nothing crashes. There's a test for it: *"an id that is
   not in the recipe is ignored."* Since the id arrives from a drag-and-drop `ClipData` — a
   string from outside your type system — validating it here is not paranoia.

5. It could have been a `LaunchedEffect` in `FinalPage`. But then the *page* would decide when
   the story continues, and the page's job is to draw a cake. Sequencing the story is
   `GameViewModel`'s job — that's the same principle that keeps consequences in
   `onSequenceComplete`. Practical benefit: `viewModelScope` cancels the delay if the ViewModel
   dies, and the timing is visible in the one file you'd read to understand the game's flow
   rather than buried in a UI file.

</details>

---

Next: [04 — The Dialog Data Model](04-the-dialog-data-model.md)
