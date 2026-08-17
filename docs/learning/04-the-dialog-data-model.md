# 04 — The Dialog Data Model

> The most interesting design in the project. If an interviewer asks you to walk through one
> part of this codebase, walk through this.

## What you'll learn

- Dialog as **data** rather than as code
- The four-type hierarchy: `DialogText` → `DialogStep` → `DialogSequence` → `ActiveDialog`
- **The no-lambdas rule** and the exact bug it prevents
- Sealed interfaces for "one of these, never two"
- Why string resource *ids* travel instead of strings

---

## The problem

You need a character to say a sequence of lines. Each line has a mood, sometimes a bold header,
sometimes a description, sometimes a button, and sometimes a whole embedded card (the recipe,
the pantry). Some lines advance on tap, some need a button press. When a conversation ends,
something has to happen — the next conversation starts, or the screen changes.

The obvious approach:

```kotlin
// The version this project does NOT use
@Composable
fun IntroDialog(onFinished: () -> Unit) {
    var step by remember { mutableStateOf(0) }
    when (step) {
        0 -> DialogBox("zzz...", cat = SLEEPING) { step++ }
        1 -> DialogBox("wait, who's there?", cat = YAWNING) { step++ }
        2 -> DialogBox("oh! hello!", cat = STAND_SIT) { onFinished() }
    }
}
```

It works. And then you need five conversations, and you have five of these, each with its own
`remember`, its own step counter, its own idea of what a dialog looks like. Adding a line means
editing a composable. Changing the panel style means editing five files. Testing the flow means
running an emulator.

## The approach taken

**A dialog is a list of values.** Not code. Data.

```kotlin
// DialogCatalog.kt:26
val intro = DialogSequence(
    DialogSequenceId.INTRO,
    listOf(
        DialogStep(
            expression = CharacterExpressions.SLEEPING,
            header = DialogText(R.string.intro_sleep)
        ),
        DialogStep(
            expression = CharacterExpressions.YAWNING,
            header = DialogText(R.string.intro_ask)
        ),
        // ...
        DialogStep(
            expression = CharacterExpressions.LAUGHING,
            header = DialogText(R.string.intro_today_header),
            desc = DialogText(R.string.intro_today_desc),
            button = DialogButton(DialogText(R.string.intro_today_button))
        ),
    )
)
```

There is **one** dialog renderer in the app. Everything above is content fed into it.

The file's own doc comment states the win:

> Adding a line to a conversation is an edit to one list here plus a string in `strings.xml`;
> nothing in the overlay, the frame or the animation needs to know about it.

---

## The four types

```
DialogText     "a piece of copy"
    ↓ (used by)
DialogStep     "one beat: a mood + optional header/desc/extra/button/footnote"
    ↓ (list of)
DialogSequence "a named conversation"
    ↓ (pointed into by)
ActiveDialog   "which conversation, and where in it"
```

### `DialogText` — copy that isn't a String

```kotlin
// DialogStep.kt:20
@Immutable
data class DialogText(
    @StringRes val resId: Int,
    val args: List<Int> = emptyList()
)
```

Not a `String`. A **resource id plus arguments**.

Resolving `R.string.hunt_won_desc` into "You found 10 things!" needs a `Context`. If
`DialogCatalog` held resolved strings, it would need a `Context`, and then it couldn't be a
plain `object` — it'd need construction, injection, and it couldn't be read by a JVM unit test.

Keeping it as an id means the catalog is pure data. Resolution happens at the last possible
moment, in the composable that draws it:

```kotlin
// DialogStep.kt:26
@Composable
fun DialogText.resolve(): String =
    if (args.isEmpty()) stringResource(resId) else stringResource(resId, *args.toTypedArray())
```

An extension function marked `@Composable`, because `stringResource` needs composition scope.
The `*` is Kotlin's spread operator, turning the list into varargs for the format call.

**Why `List<Int>` and not `List<Any>`?**

The comment says it directly:

> Args are `List<Int>` rather than `List<Any>` on purpose - it keeps this a pure value type,
> which is what lets `DialogStep` be compared with `==`.

Every argument in this game is a number ("You found **10** things"). Restricting to `Int`
guarantees the whole type is comparable by value. `List<Any>` would let someone pass an object
without a sensible `equals`, and — as the next section shows — the entire animation depends on
`==` being trustworthy.

### `DialogStep` — one beat

```kotlin
// DialogStep.kt:43
@Immutable
data class DialogStep(
    val expression: CharacterExpressions,   // always present
    val header: DialogText? = null,         // bold
    val desc: DialogText? = null,           // regular, underneath
    val extra: DialogExtra? = null,         // a structural block
    val button: DialogButton? = null,
    val footnote: DialogText? = null        // below the panel, outside the frame
) {
    val advancesOnTap: Boolean get() = button == null
}
```

`expression` is the only required field, because the cat is always on screen — there's always a
face to choose. Everything else is optional and drawn only when non-null.

That's what lets one renderer handle a bare one-liner, a header-plus-button, and the full
recipe card. `DialogStepBody` just checks each field:

```kotlin
// DialogStepBody.kt:48
step.header?.let { Text(...) }
step.desc?.let { Text(...) }
when (val extra = step.extra) { ... }
step.button?.let { PixelButton(it.label.resolve(), onAdvance, ...) }
```

And `advancesOnTap` is derived, not stored: *if there's no button, the panel itself is the
button.* One rule, one line, can't get out of sync.

---

## ⭐ The no-lambdas rule

This is the part to understand properly. From
[`DialogStep.kt:37`](../../app/src/main/java/com/cinthya/birthdaycake/model/dialog/DialogStep.kt):

> Note what is *not* here: no lambdas. Two lambdas are never equal, so a click handler stored
> on a step would make every step compare unequal to itself and fire a transition on every
> recomposition - the exact blink this system exists to avoid.

Unpack it.

**Step 1 — the animation compares steps.** The overlay uses `AnimatedContent` to crossfade
between steps. `AnimatedContent` decides "has the content changed?" by comparing the old target
state with the new one.

**Step 2 — lambdas don't compare equal.**

```kotlin
val a: () -> Unit = { println("hi") }
val b: () -> Unit = { println("hi") }
a == b          // false
```

Two lambdas with identical bodies are different objects. Worse, a lambda written inline in a
composable is **recreated on every recomposition**, so it isn't even equal to *itself* from the
previous frame.

**Step 3 — the bug.** Suppose `DialogStep` had `onClick: () -> Unit`:

```kotlin
DialogStep(
    expression = LAUGHING,
    header = DialogText(R.string.intro_today_header),
    onClick = { viewModel.doSomething() }   // ← new object every recomposition
)
```

`data class` generates `equals()` from all constructor properties. One of them is now a lambda
that's never equal. So `stepOld == stepNew` is **always false**, even when nothing changed.
`AnimatedContent` sees "content changed!" on every recomposition and restarts its transition.

The visible result: the dialog **flickers continuously**. Text fades out and back in for no
reason, forever. And it's a miserable bug to diagnose, because the code *looks* right — the
text is correct, the step index is correct, nothing is null.

**The fix, taken here:** steps carry no behaviour at all. What a button *does* is decided once,
in `GameViewModel.onSequenceComplete`. The step only says *that there is a button and what it's
labelled*.

```kotlin
// DialogStepBody.kt:73 — every button gets the same handler
step.button?.let {
    PixelButton(it.label.resolve(), onAdvance, Modifier.fillMaxWidth())
}
```

Every button in the game does the same thing: **advance**. What advancing *means* is the
checkpoint table's business.

### The broader lesson

This is a real, general Compose principle worth internalising:

> **Put behaviour where it's decided, not where it's displayed.**

Data classes that feed into equality-sensitive APIs (`AnimatedContent`, `key()`,
`derivedStateOf`, skipping) must hold only comparable values. The moment you put a lambda in
one, you've broken every optimisation and every animation that depends on it.

---

## `DialogSequence` and `ActiveDialog`

```kotlin
// DialogSequence.kt:30
@Immutable
data class DialogSequence(
    val id: DialogSequenceId,
    val steps: List<DialogStep>
) {
    init {
        require(steps.isNotEmpty()) { "$id has no steps" }
    }
}
```

The `init` block is a **construction-time invariant**. An empty sequence is meaningless — you'd
show a dialog with no content and no way to advance. `require` throws immediately, at the point
the mistake was made, rather than letting it become an `IndexOutOfBoundsException` three
screens later.

There's a test asserting the throw
([`DialogSequenceTest.kt:54`](../../app/src/test/java/com/cinthya/birthdaycake/dialog/DialogSequenceTest.kt)),
which is a nice touch: it pins the invariant so a future refactor can't quietly drop it.

```kotlin
// DialogSequence.kt:47
@Immutable
data class ActiveDialog(
    val sequence: DialogSequence,
    val index: Int = 0
) {
    val step: DialogStep get() = sequence.steps[index]
    val isLast: Boolean get() = index == sequence.steps.lastIndex
    fun next(): ActiveDialog? = if (isLast) null else copy(index = index + 1)
}
```

`DialogSequence` is the *script*; `ActiveDialog` is the *bookmark*. The script is static and
shared; the bookmark moves.

`next()` returning `null` at the end is the key API decision. It gives the caller a signal that
can't be ignored — you have to handle the null, and handling it is where `onSequenceComplete`
fires. An alternative like `hasNext()` + `advance()` would let a caller forget to check.

### Why `DialogSequenceId` is an enum

```kotlin
// DialogSequence.kt:12
enum class DialogSequenceId { INTRO, RECIPE, RAN_OUT, HUNT_WON, FINALE }
```

Covered in Module 03, but the reason is worth repeating: two `when`s switch on it, both
exhaustive, so adding a sequence produces exactly two compile errors in exactly the two places
you have a decision to make. A `String` key would compile fine and fail at runtime.

There's a second, subtler payoff, from the `GameViewModel` doc comment:

> If that changes, the only thing that needs saving is the sequence id and the step index -
> which is exactly why sequences are named by `DialogSequenceId` rather than passed around as
> lists.

Saving `(INTRO, 3)` to a `Bundle` is trivial. Saving a whole `DialogSequence` object is not.
The design has left the door open for process-death survival without a rewrite. That's the kind
of forward-thinking you should point at in a portfolio.

---

## `DialogExtra` — a sealed interface

```kotlin
// DialogStep.kt:69
@Immutable
sealed interface DialogExtra {
    @Immutable
    data class Recipe(val entries: List<RecipeEntry>) : DialogExtra

    @Immutable
    data class Pantry(val slots: List<IngredientsCount>, val columns: Int = 3) : DialogExtra
}
```

Two dialogs carry a structural block: the recipe card (a list of ingredient rows) and the
pantry (a 3-column grid of stock slots). They need **different payloads**, and a step can only
ever have one.

The alternative — nullable fields — is worse:

```kotlin
// Don't do this
data class DialogStep(
    val recipeEntries: List<RecipeEntry>? = null,
    val pantrySlots: List<IngredientsCount>? = null,
    val pantryColumns: Int? = null,
)
```

Now nothing stops both being set, `pantryColumns` is meaningless without `pantrySlots`, and the
renderer needs nested null checks. The type doesn't express "exactly one of these."

Sealed does. And it pays off at the render site
([`DialogStepBody.kt:67`](../../app/src/main/java/com/cinthya/birthdaycake/ui/components/dialog/DialogStepBody.kt)):

```kotlin
// Exhaustive: a new variant will not compile until it is handled here.
when (val extra = step.extra) {
    is DialogExtra.Recipe -> RecipeExtraBody(extra)
    is DialogExtra.Pantry -> PantryExtraBody(extra)
    null -> Unit
}
```

`when (val extra = ...)` binds and switches in one line, and inside each branch `extra` is
**smart-cast** to the specific type — `RecipeExtraBody(extra)` gets a `DialogExtra.Recipe`, no
cast needed.

Add `DialogExtra.Photo` and this `when` fails to compile. The compiler walks you to the exact
line where the new rendering branch belongs.

---

## The highlight markup

A small detail worth knowing about, in
[`DialogStepBody.kt:79`](../../app/src/main/java/com/cinthya/birthdaycake/ui/components/dialog/DialogStepBody.kt):

```kotlin
private const val HIGHLIGHT_OPEN = "[["
private const val HIGHLIGHT_CLOSE = "]]"

/** Turns `is [[TODAY]]!` into `is TODAY!` with the marked run on a pink field. */
private fun highlighted(raw: String): AnnotatedString { ... }
```

The string in `strings.xml` contains `[[TODAY]]`, and the renderer converts the marked run into
an `AnnotatedString` with a pink `SpanStyle` background.

The reasoning, from the comment:

> Marking it up in the string keeps it a translator's decision rather than a guess made from
> the casing.

The alternative — "highlight any ALL-CAPS word" — breaks the moment a translator uses a
language without that convention, or writes an acronym. Putting the markup *in the translatable
string* means whoever translates it decides what gets emphasised. That's a genuinely thoughtful
i18n call for a hobby project.

`AnnotatedString` is Compose's rich-text type: a string plus styled ranges. `buildAnnotatedString { }`
+ `withStyle(SpanStyle(...)) { }` is how you build one.

---

## Self-check

1. Explain, to someone who knows Compose basics, why putting `onClick: () -> Unit` on
   `DialogStep` would make the dialog flicker.
2. Why is `DialogCatalog.intro` a `val` but `DialogCatalog.huntWon(...)` a `fun`?
3. `DialogText` holds `@StringRes val resId: Int` instead of a `String`. Name two things that
   would become impossible if it held a `String`.
4. Add a `DialogExtra.Photo(@DrawableRes val res: Int)`. What breaks, and where does the
   compiler send you?
5. Every dialog button calls `onAdvance`. Doesn't that mean every button does the same thing?

<details>
<summary><b>Answers</b></summary>

1. `DialogStep` is a `data class`, so its generated `equals()` includes every constructor
   property. Lambdas have identity equality, and a lambda written inline in a composable is a
   fresh object on every recomposition — so `oldStep == newStep` would be `false` even when
   nothing changed. `AnimatedContent` reads that as "content changed" and restarts its
   fade-out/fade-in on every recomposition. The visible result is a permanently flickering
   dialog, with a cause that isn't visible anywhere in the animation code.

2. `intro` is fixed copy — the cat says the same six lines every time — so it's built once and
   reused. `huntWon(collectedCount)` embeds a number from the current run into "You found %d
   things!", so it can't exist until that number does. Static content → `val`; content that
   reads live state → `fun`.

3. (a) `DialogCatalog` could no longer be a plain `object` — resolving a string needs a
   `Context`, so the catalog would need construction and injection. (b) The JVM unit tests
   (`GameViewModelTest`, `GameUiStateTest`) couldn't run without Robolectric or an emulator,
   because touching the catalog would touch Android. A third: localisation would break, since
   the string would be resolved once at class-init in whatever locale was active then.

4. Two places. `DialogStepBody.kt:67`'s `when` becomes non-exhaustive and fails to compile —
   which is exactly right, because a new kind of block needs a new way to draw it. You'd add
   the branch and write a `PhotoExtraBody`. Nothing else in the system needs to change: not the
   overlay, not the animation, not `DialogStep`. That's the sealed-interface payoff.

5. Every button does the same thing *at the UI level* — advance the conversation. What
   *advancing* means depends on where you are, and that's `onSequenceComplete`'s job: finishing
   RAN_OUT starts the hunt, finishing HUNT_WON returns to mixing. So the behaviour differs, but
   the difference lives in one readable table instead of being scattered across the script.
   That's the trade: buttons lose per-step flexibility, and in exchange the whole plot is five
   lines you can read at once. For a linear story game, a very good trade. For a branching
   dialogue tree, you'd need something richer — probably an `action: DialogAction` enum on the
   step, which is still data and still compares by value.

</details>

---

Next: [05 — The Dialog Overlay](05-the-dialog-overlay.md)
