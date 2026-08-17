# 09 — Testing

## What you'll learn

- Why this project's logic is testable without an emulator, and what design choices bought that
- The three test files and what each pins down
- Test naming with backticks, and writing tests as documentation
- What *isn't* tested, and how to be honest about it
- How to run them

---

## The precondition: logic with no Android in it

Open [`GameViewModelTest.kt`](../../app/src/test/java/com/cinthya/birthdaycake/dialog/GameViewModelTest.kt).
Its imports are JUnit and your own classes. No Robolectric. No `AndroidJUnit4`. No emulator.

That's not luck. It's the payoff for three decisions made much earlier:

| Decision | Where | Why it makes tests possible |
|---|---|---|
| `DialogText` holds a `@StringRes Int`, not a `String` | Module 04 | Reading the catalog needs no `Context` |
| `DialogStep` holds no lambdas and no Compose types | Module 04 | The script is comparable, printable, plain data |
| ViewModels import no Compose | Module 03 | `GameViewModel()` is constructible in a JVM test |

`androidx.lifecycle.ViewModel` itself is a plain class — it only becomes Android-dependent when
you touch `viewModelScope` (which needs a main dispatcher) or `SavedStateHandle`.

**This is the real argument for the architecture.** Not "MVVM is best practice" — *"the game
logic runs in 40ms on the JVM because no part of it knows what a `Context` is."*

---

## The three test files

### `DialogSequenceTest` — the cursor

Tests `ActiveDialog` and `DialogSequence` in isolation:

```kotlin
@Test
fun `next walks the sequence and stops at the end`() {
    var cursor: ActiveDialog? = ActiveDialog(sequence(3))
    assertEquals(0, cursor?.index)
    cursor = cursor?.next()
    assertEquals(1, cursor?.index)
    cursor = cursor?.next()
    assertEquals(2, cursor?.index)
    assertNull(cursor?.next())   // past the last step: nothing to show
}
```

Also covers `isLast`, the single-step edge case, `advancesOnTap`, and:

```kotlin
@Test(expected = IllegalArgumentException::class)
fun `an empty sequence is rejected at construction`() {
    DialogSequence(DialogSequenceId.INTRO, emptyList())
}
```

That last one pins the `require(steps.isNotEmpty())` invariant. Invariants that aren't tested
tend to get deleted by someone who doesn't see the point.

Note the local helpers:

```kotlin
private fun step(res: Int) = DialogStep(CharacterExpressions.IDLE_SIT, desc = DialogText(res))
private fun sequence(size: Int) = DialogSequence(DialogSequenceId.INTRO, List(size) { step(...) })
```

The test builds its own synthetic sequences rather than using `DialogCatalog`. Right call — this
is testing the *cursor mechanics*, and it shouldn't break when someone adds a line to the intro.

### `GameUiStateTest` — the derived values

Tests `GameUiState`'s computed properties as pure functions. No ViewModel involved.

```kotlin
@Test
fun `the pantry is what is collected minus what is already in the bowl`() {
    val heart = GameData.cakeIngredients.first { it.id == "heart" }
    val state = stocked(bowl = mapOf("heart" to 2))

    assertEquals(heart.requiredAmount - 2,
        state.pantry.first { it.ingredients.id == "heart" }.currentAmount)
    // Everything else is untouched, and the hunt's record itself never moves.
    assertEquals(GameData.totalIngredients, state.collectedIngredients)
    assertEquals(GameData.totalIngredients - 2, state.pantry.sumOf { it.currentAmount })
}
```

Three assertions capturing the whole `inventory`/`bowl`/`pantry` relationship from Module 02:
the pantry falls, the inventory doesn't move, and the totals stay consistent.

And this one, with a comment explaining its existence:

```kotlin
/**
 * The case that rules out `bowlCount == collectedIngredients`: on the opening screen both are
 * zero, and an empty bowl must not read as ready.
 */
@Test
fun `a fresh game is not ready to mix`() {
    assertEquals(false, GameUiState().isMixReady)
}
```

This is a **regression test for a design decision**. `isMixReady` compares against
`GameData.totalIngredients` rather than `collectedIngredients` — and if you didn't know why, the
"simpler" version looks appealing. It's wrong: before the hunt, both are 0, so an empty bowl on
the opening screen would report ready. The test makes the trap permanent knowledge.

The same reasoning appears in `GameUiState.kt:50` as a comment. Comment *and* test — one explains,
one enforces.

### `GameViewModelTest` — the flow

The biggest file, walking the whole game.

```kotlin
/** Taps through whatever conversation is currently up. */
private fun GameViewModel.finishCurrentSequence() {
    val steps = uiState.value.dialog?.sequence?.steps?.size ?: return
    repeat(steps) { onDialogAdvance() }
}

/** Plays the three opening conversations, leaving the board up and no dialog showing. */
private fun GameViewModel.runToHunt() { repeat(3) { finishCurrentSequence() } }

/** Carries on past the board: a cleared hunt banked, celebrated, and back on the bowl. */
private fun GameViewModel.runToMixing() {
    runToHunt()
    onHuntFinished(fullInventory)
    finishCurrentSequence()
}
```

Private extension functions on `GameViewModel`, scoped to the test class. They give tests a
vocabulary — `GameViewModel().apply { runToMixing() }` reads as "a game that has reached the
mixing stage," and the test that follows can be about one thing.

Note `finishCurrentSequence` reads the step count from the sequence rather than hardcoding it,
so adding a line to the intro doesn't break every test.

The tests themselves read as a specification:

```
the game opens on the mixing page with the intro running
advancing walks the steps of a sequence before ending it
the opening conversations chain intro to recipe to ran out, then start the hunt
clearing the board banks the ingredients and celebrates
finishing the celebration returns to the mixing page
a second hunt result is ignored while the celebration is still up
advancing with nothing showing does nothing
a page expression only shows through once the dialog is gone
dropping an ingredient moves one unit from the chips into the bowl
an ingredient cannot be dropped more times than it was collected
an id that is not in the recipe is ignored
mixing unlocks only once the last unit is in the bowl
```

**Read that list top to bottom and you have the game's rules.** That's what test names are for.

### Backtick names

```kotlin
@Test
fun `an ingredient cannot be dropped more times than it was collected`() { ... }
```

Kotlin allows any string as an identifier inside backticks. On JVM unit tests this is idiomatic
and encouraged — the failure output reads as English.

Caveat: **don't do this in `androidTest`.** The Android runtime doesn't accept spaces in method
names on some API levels, and it fails at runtime. The instrumented test in this project
(`ExampleInstrumentedTest`) correctly uses a normal name.

---

## The pattern in each test

Arrange, act, assert — with a blank line between:

```kotlin
@Test
fun `dropping an ingredient moves one unit from the chips into the bowl`() {
    val viewModel = GameViewModel().apply { runToMixing() }        // arrange

    viewModel.onIngredientDropped("heart")                          // act

    val state = viewModel.uiState.value                             // assert
    assertEquals(1, state.bowlCount)
    assertEquals(2, state.pantry.first { it.ingredients.id == "heart" }.currentAmount)
    // The hunt's record is a separate fact and does not move.
    assertEquals(GameData.totalIngredients, state.collectedIngredients)
}
```

One action per test. The visual rhythm makes it obvious at a glance what's being tested.

Note the assertions are read from `uiState.value` directly — no coroutines, no `runTest`, no
`Turbine`. Because `MutableStateFlow` is synchronous for `.value` reads and `update` applies
immediately, testing a `StateFlow`-based ViewModel needs no async machinery **as long as the
code under test doesn't launch coroutines**. Which brings us to the gap.

---

## What isn't tested — and being honest about it

The test file states its own limitation, at the top:

```kotlin
/**
 * Walks the checkpoint table without Compose or a device.
 *
 * `onMixClick` is the one path not covered here: it schedules the closing dialog on
 * `viewModelScope`, which needs a main dispatcher that a plain JVM test does not have.
 * Everything it does synchronously - the move to GameScreen.FINAL - is verified through the
 * screen assertions below instead.
 */
```

`viewModelScope` uses `Dispatchers.Main`, which doesn't exist on the JVM without the Android
framework. Calling `onMixClick()` in a plain unit test throws.

**Documenting the gap is the right move.** It says: this is known, here's why, here's what's
covered instead.

### The honest inventory

| Area | Status |
|---|---|
| `ActiveDialog` cursor mechanics | ✅ covered |
| `GameUiState` derived properties | ✅ covered |
| `GameViewModel` flow and checkpoints | ✅ covered |
| Bowl/pantry/inventory rules | ✅ covered |
| **`MiniHuntViewModel` match logic** | ❌ **not covered** |
| `onMixClick` + the delayed finale | ❌ not covered (documented) |
| Any Compose UI test | ❌ none |
| `GameData.buildDeck` | ❌ not covered |

The `MiniHuntViewModel` gap is the notable one — it's the most intricate logic in the project
(early resolve, cancellable jobs, the re-check) and it has no tests. See the exercises.

Note also that the `TAG_DIALOG_SCRIM` / `TAG_DIALOG_PANEL` test tags exist in
`DialogOverlayHost.kt:78` and the Compose UI test dependencies are in `build.gradle.kts` — the
scaffolding for UI tests is in place, but no UI tests were written.

---

## Running them

```bash
# JVM unit tests — fast, no device
./gradlew test

# just the debug variant
./gradlew testDebugUnitTest

# instrumented tests — needs an emulator or device
./gradlew connectedAndroidTest
```

On Windows use `gradlew.bat` (or `.\gradlew` in PowerShell).

HTML report afterwards: `app/build/reports/tests/testDebugUnitTest/index.html`

In Android Studio: right-click the `test` source folder → Run.

---

## Exercises

**A.** Write `MiniHuntViewModelTest`. Cover: a fresh board has 20 cards all face-down; flipping
one card doesn't increment `moves`; flipping two matching cards marks both matched and
increments `moves`; flipping two non-matching cards leaves them face-up and sets `isMismatched`;
clearing the board sets `isFinished` and produces the full recipe in `collected`.

Hint: use `MiniHuntViewModel(random = Random(7))` for a deterministic board, then find two cards
with the same `ingredient.id` from `uiState.value.cards`.

**B.** Test `GameData.buildDeck`: the deck size equals `totalIngredients * 2`, every ingredient
appears an even number of times, and the same seed produces the same order twice.

**C.** Make `onMixClick` testable. Hint: `Dispatchers.setMain(...)` from
`kotlinx-coroutines-test`, in a `@Before`/`@After` pair — you'll need to add
`testImplementation(libs.kotlinx.coroutines.test)`.

**D.** Write one Compose UI test using the existing tags: assert the scrim is displayed when a
dialog is up, then that tapping the panel advances the step.

<details>
<summary><b>Sketch for A</b></summary>

```kotlin
class MiniHuntViewModelTest {

    private fun viewModel() = MiniHuntViewModel(random = Random(7))

    /** Two face-down cards showing the same symbol. */
    private fun MiniHuntViewModel.findMatchingPair(): Pair<Int, Int> {
        val available = uiState.value.cards.filter { !it.isFaceUp && !it.isMatched }
        val pair = available.groupBy { it.ingredient.id }.values.first { it.size >= 2 }
        return pair[0].id to pair[1].id
    }

    private fun MiniHuntViewModel.findMismatchedPair(): Pair<Int, Int> {
        val available = uiState.value.cards.filter { !it.isFaceUp && !it.isMatched }
        val first = available.first()
        val second = available.first { it.ingredient.id != first.ingredient.id }
        return first.id to second.id
    }

    @Test
    fun `a fresh board is face down and unmatched`() {
        val state = viewModel().uiState.value
        assertEquals(GameData.totalIngredients * 2, state.cards.size)
        assertEquals(0, state.moves)
        assertEquals(false, state.cards.any { it.isFaceUp || it.isMatched })
    }

    @Test
    fun `flipping one card is not yet a move`() {
        val vm = viewModel()
        vm.onCardClick(vm.uiState.value.cards.first().id)

        assertEquals(0, vm.uiState.value.moves)
        assertEquals(1, vm.uiState.value.revealed.size)
        assertEquals(false, vm.uiState.value.isMismatched)
    }

    @Test
    fun `a matching pair is marked matched and counts as a move`() {
        val vm = viewModel()
        val (a, b) = vm.findMatchingPair()

        vm.onCardClick(a)
        vm.onCardClick(b)

        val state = vm.uiState.value
        assertEquals(1, state.moves)
        assertEquals(1, state.matchedPairs)
        assertEquals(false, state.isMismatched)   // matched pairs drop out of `revealed`
    }

    @Test
    fun `a mismatched pair stays up and the board is waiting`() {
        val vm = viewModel()
        val (a, b) = vm.findMismatchedPair()

        vm.onCardClick(a)
        vm.onCardClick(b)

        assertEquals(1, vm.uiState.value.moves)
        assertEquals(0, vm.uiState.value.matchedPairs)
        assertEquals(true, vm.uiState.value.isMismatched)
    }

    @Test
    fun `clearing the board collects exactly the recipe`() {
        val vm = viewModel()
        repeat(GameData.totalIngredients) {
            val (a, b) = vm.findMatchingPair()
            vm.onCardClick(a)
            vm.onCardClick(b)
        }

        val state = vm.uiState.value
        assertEquals(true, state.isFinished)
        assertEquals(
            GameData.cakeIngredients.map { it.requiredAmount },
            state.collected.map { it.currentAmount }
        )
    }
}
```

**Two things to notice.** First, none of these need a main dispatcher — `scheduleFlipBack` calls
`viewModelScope.launch`, which on the JVM will throw when the mismatch test runs. You'll likely
need `Dispatchers.setMain(UnconfinedTestDispatcher())` in a `@Before` for the mismatch and
clear-the-board cases. That's exercise C arriving early, and it's a genuine finding: *the
mini-game is harder to test than the main game because it schedules work.*

Second, the last test is the one that matters most — it pins the invariant from Module 06 that
clearing the board yields exactly the recipe. If someone changes `buildDeck`, that test catches
it.

</details>

---

## Self-check

1. Why can these tests run without an emulator? Name two design choices that made it possible.
2. Why does `finishCurrentSequence` read the step count from the sequence instead of hardcoding it?
3. Why does `DialogSequenceTest` build synthetic sequences instead of using `DialogCatalog.intro`?
4. What does `a fresh game is not ready to mix` actually protect against?
5. Why is `MiniHuntViewModel` harder to unit test than `GameViewModel`?

<details>
<summary><b>Answers</b></summary>

1. Because no code under test touches Android. Two of the choices: `DialogText` stores a
   `@StringRes Int` rather than a resolved `String`, so the catalog needs no `Context`; and the
   ViewModels import no Compose, so they're plain JVM classes. A third: `androidx.lifecycle.ViewModel`
   only becomes Android-dependent when you use `viewModelScope` or `SavedStateHandle`.

2. So adding a line to a conversation doesn't break the test suite. Hardcoding `repeat(6)` for
   the intro would mean every flow test fails the moment someone edits `DialogCatalog` — tests
   that break on unrelated changes get deleted rather than fixed.

3. Because it's testing the cursor *mechanics* (`next()`, `isLast`, the empty-sequence guard),
   not the game's content. Using real sequences would couple mechanical tests to the script, so
   editing dialogue would break tests about arithmetic. Test the thing you mean to test.

4. Against "simplifying" `isMixReady` to `bowlCount == collectedIngredients`. That looks
   equivalent and is wrong: on the opening screen both are 0, so an empty bowl would report ready
   and the Mix button would be live before the game began. The test makes that trap permanent —
   a comment explains it, the test enforces it.

5. Because it schedules work. `scheduleFlipBack` calls `viewModelScope.launch { delay(...) }`,
   and `viewModelScope` uses `Dispatchers.Main`, which doesn't exist in a plain JVM test. You need
   `Dispatchers.setMain(...)` from `kotlinx-coroutines-test` plus a test dispatcher to control
   virtual time. `GameViewModel` has the same issue in exactly one method (`onMixClick`), which
   is why its test file documents that one gap rather than pulling in the dependency.

</details>

---

Next: [10 — Portfolio Talking Points](10-portfolio-talking-points.md)
