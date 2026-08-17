# 07 — Drag and Drop Mixing

## What you'll learn

- Compose's drag-and-drop API: source, target, and the `ClipData` payload
- `rememberUpdatedState` — the fix for stale-but-stable callbacks
- A real trap in `dragAndDropSource` that freezes your UI into a snapshot
- Refusing a drag by returning `null`
- Modifier order as a correctness concern, not styling

File: [`MixingPage.kt`](../../app/src/main/java/com/cinthya/birthdaycake/ui/screen/MixingPage.kt)

---

## The interaction

Six ingredient chips at the top, a bowl in the middle. Long-press a chip, drag it to the bowl,
release. The chip's count goes down, the bowl's counter goes up. When all 10 units are in,
"Mix It!" lights up.

Compose's drag-and-drop is built on **Android's platform drag-and-drop** (`View.startDragAndDrop`,
`ClipData`, `DragEvent`). That's why the payload is a `ClipData` holding a string rather than
a Kotlin object — the data crosses a platform boundary designed for cross-*app* drags.

---

## The target: the bowl

```kotlin
// MixingPage.kt:88
val callback = remember {
    object : DragAndDropTarget {
        override fun onDrop(event: DragAndDropEvent): Boolean {
            isOver = false
            val id = event.toAndroidDragEvent().clipData
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)?.text?.toString() ?: return false
            onDropped(id)
            return true
        }

        override fun onEntered(event: DragAndDropEvent) { isOver = true }
        override fun onExited(event: DragAndDropEvent) { isOver = false }
        override fun onEnded(event: DragAndDropEvent) { isOver = false }
    }
}
```

Four callbacks: entered, exited, dropped, ended. `onEnded` fires when the whole gesture
finishes regardless of outcome — that's the one that clears the highlight if you drop somewhere
invalid.

The `onDrop` chain is defensive at every link:

```kotlin
event.toAndroidDragEvent().clipData      // could be null
    ?.takeIf { it.itemCount > 0 }        // could be empty
    ?.getItemAt(0)?.text                 // could be non-text
    ?.toString() ?: return false         // give up cleanly
```

Returning `false` from `onDrop` tells the platform the drop was rejected. This is data crossing
a platform boundary — it isn't your type system anymore, and this is the right amount of
paranoia. (`GameViewModel.onIngredientDropped` then validates the *content* too, which is why
`onIngredientDropped("pickles")` is a no-op.)

### Attaching it

```kotlin
// MixingPage.kt:148
Image(
    painterResource(R.drawable.img_bowl),
    modifier = Modifier
        .weight(1f, false)
        .fillMaxWidth(BOWL_WIDTH)
        .dragAndDropTarget(
            shouldStartDragAndDrop = { event ->
                event.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN)
            },
            target = callback
        )
        .padding(vertical = 12.dp)
        .scale(bowlScale),
    contentScale = ContentScale.Fit
)
```

`shouldStartDragAndDrop` filters by MIME type — this target only cares about plain text. In a
bigger app you'd use a custom MIME type to avoid catching drags from other apps.

### ⭐ Modifier order is correctness here

```
.dragAndDropTarget(...)   ← 1st: registers the drop area
.padding(vertical = 12.dp) ← 2nd: shrinks what's inside
.scale(bowlScale)          ← 3rd: visual only
```

The comment explains:

> Ahead of the padding, so those 12dp catch a near-miss instead of sitting outside the target.
> The scale is last: it moves the drawing only, leaving the drop bounds still while the bowl
> pulses.

Modifiers apply **outside-in**: earlier ones wrap later ones.

- `dragAndDropTarget` **before** `padding` → the drop region includes the padding. Drop 10px
  outside the bowl art and it still counts. A forgiving hit target, for free, from ordering.
- `scale` **last** → it's a draw-time transform. The bowl visually swells 8% when a finger is
  over it, but the drop bounds don't move. If the bounds pulsed with the animation, the target
  would be shifting under the player's finger mid-drag.

Swap those three lines and the feature still "works," but it becomes fiddly in a way that's
very hard to diagnose. **Modifier order is not styling.**

---

## ⭐ `rememberUpdatedState`

```kotlin
// MixingPage.kt:85
// The callback arrives as a fresh method reference every recomposition. Holding it in an
// updated state keeps the target below a single object without staling the lambda.
val onDropped by rememberUpdatedState(onIngredientDropped)
```

This solves a real conflict. Work through it.

**Fact 1:** `remember { object : DragAndDropTarget { ... } }` is created **once**. That's
necessary — recreating the target object every recomposition would re-register it with the
platform mid-drag.

**Fact 2:** `onIngredientDropped` arrives as `gameViewModel::onIngredientDropped`, a **new
object every recomposition** (method references, like lambdas, aren't stable).

**The conflict:** if the `remember` block captured `onIngredientDropped` directly, it would
capture the *first* one and hold it forever. Usually harmless — it forwards to the same
ViewModel method. But it's a **stale closure**, and the day the callback's identity carries
meaning (a different ViewModel, a captured loop variable, a swapped handler), it silently calls
the wrong thing.

**The fix:** `rememberUpdatedState(x)` returns a `State<T>` that is remembered across
recompositions but whose `.value` is **overwritten with the newest `x` on every recomposition**.

```
recomposition 1:  onIngredientDropped = ref#1  →  state.value = ref#1
recomposition 2:  onIngredientDropped = ref#2  →  state.value = ref#2
                  (same State object throughout)
```

The `object : DragAndDropTarget` captures the *State holder* (stable, created once) and reads
`.value` at call time (always current). One long-lived target, never a stale callback.

**Where you'll need this again:** any long-lived object that captures a callback —
`LaunchedEffect(Unit)`, `DisposableEffect`, a remembered listener, a timer. It's one of the
handful of Compose APIs you should know by name.

---

## ⭐ The drag decoration trap

```kotlin
// MixingPage.kt:215
.dragAndDropSource(
    drawDragDecoration = {
        val side = size.height * DRAG_ICON_FRACTION
        translate(
            left = (size.width - side) / 2f,
            top = (size.height - side) / 2f
        ) {
            with(painter) { draw(Size(side, side)) }
        }
    },
    transferData = { _ ->
        if (!canDrag) null
        else DragAndDropTransferData(ClipData.newPlainText("ingredient", count.ingredients.id))
    }
)
```

The comment above it describes a bug that was actually hit:

> Drawing the decoration by hand is deliberate. The overload that snapshots the composable for
> you does it by caching the chip into a graphics layer and painting every later frame from
> that recording, which freezes the count at whatever it was when the layer was first recorded.

**What happened:** there's a `dragAndDropSource` overload that takes a `GraphicsLayer` and
snapshots the composable to use as the drag shadow. Convenient — the thing you drag looks
exactly like the chip.

But the snapshot is a **recording**, taken once. The chip displays a live count ("×3"). After
the first drag the count changes to 2 — and the drag shadow still shows 3, forever, because
it's replaying an old recording.

**The fix:** draw the decoration by hand. `drawDragDecoration` gives you a `DrawScope`, so you
paint what you want — here, just the ingredient icon, no count. Nothing to go stale.

The maths inside is worth reading:

- `size` is the *whole chip's* bounds — the shadow canvas is chip-sized even though you're
  drawing a small icon.
- Drawing at the origin would put the icon in the top-left corner, floating oddly under the
  finger.
- `translate(left = (size.width - side) / 2f, top = ...)` centres it.
- `DRAG_ICON_FRACTION = 0.9f` makes the dragged icon *larger* than the one on the chip
  (`CHIP_CONTENT_HEIGHT = 0.5f`), so it stays visible under a fingertip.

### Refusing a drag with `null`

```kotlin
transferData = { _ -> if (!canDrag) null else DragAndDropTransferData(...) }
```

where `canDrag = count.currentAmount > 0` (line 201).

> Returning null from transferData refuses the drag, which is what makes an empty chip
> unliftable.

Elegant. No `enabled` flag, no separate disabled component — the source simply declines to
produce a payload, and the platform never starts the drag. An empty chip is dead weight, which
is exactly right.

Paired with the visual: `IngredientBox` dims to `DISABLED_ALPHA = 0.45f` at zero. The player
*sees* it's unavailable and *feels* it when it won't lift.

### The payload

```kotlin
ClipData.newPlainText("ingredient", count.ingredients.id)
```

Just the id string — `"heart"`. Not the whole `Ingredients` object, which would need
`Parcelable`. The receiver looks the id up in the state it already has. **Send the key, not the
object** — the same principle that makes `DialogText` hold a resource id.

---

## The hover feedback

```kotlin
// MixingPage.kt:105
val bowlScale by animateFloatAsState(
    if (isOver) BOWL_HOVER_SCALE else 1f,   // 1.08f
    label = "bowlHover"
)
```

`isOver` is a plain local `remember { mutableStateOf(false) }` — set by `onEntered`, cleared by
`onExited`/`onDrop`/`onEnded`. `animateFloatAsState` turns the boolean flip into a smooth 8%
swell.

The constant's comment: *"How far the bowl swells while a finger is over it. The only 'you can
let go now' cue."* Under a fingertip, with the chip's shadow following you, an 8% scale is the
only affordance you get. Naming it and documenting why is good practice.

The **`label`** parameter on animation APIs isn't decoration either — it names the animation in
Android Studio's Animation Preview inspector. Every animation in this project has one.

---

## The rest of the page

```kotlin
// MixingPage.kt:131
pantry.chunked(3).forEach { row ->
    IngredientRow(row)
    Spacer(Modifier.height(8.dp))
}
```

Rows of three, for however many ingredients there are. This started life as
`IngredientRow(pantry.take(3))` / `IngredientRow(pantry.takeLast(3))`, which renders the same two
rows today but **assumed exactly six ingredients** — with five, the item at index 2 appears in
*both* rows; with seven, one silently vanishes. No crash, no compile error, just a wrong pantry.

`chunked` never duplicates and never drops. It's also the pattern `MiniHuntPage` was already
using for its card grid, which makes this as much a consistency fix as a correctness one: when a
codebase already contains the better version of a pattern, the odd one out is the bug.

```kotlin
// MixingPage.kt:170
Text(stringResource(R.string.ingredients_added, bowlCount, GameData.totalIngredients), ...)
Spacer(Modifier.height(12.dp))
PixelButton(stringResource(R.string.mix_it), onMixClick, Modifier.fillMaxWidth(), enabled = isMixReady)
```

`isMixReady` comes from `GameUiState` (Module 02) — `bowlCount == GameData.totalIngredients`.
The page is told whether to enable the button; it doesn't work it out.

And `totalIngredients` is **derived, not written down**:

```kotlin
// GameData.kt:20
val totalIngredients = cakeIngredients.sumOf { it.requiredAmount }
```

It used to read `val totalIngredients = 10`, which was correct only by arithmetic coincidence —
`3 + 2 + 1 + 2 + 1 + 1`. Change heart's `requiredAmount` from 3 to 4 and three things break at
once: the deck grows to 22 cards, the hunt header reads "11 / 10 pairs found", and `isMixReady`
fires at `bowlCount == 10`, *before* the bowl is full. One stale number, three wrong behaviours,
no compile error anywhere.

That's the stored-vs-derived rule from Module 02 applied to a constant. A constant is just state
that changes rarely, and the same reasoning holds: if it can be computed from the source of
truth, computing it removes the whole class of bug instead of one instance of it.

And the long-press hint:

> Dragging is started by the platform's long press — `dragAndDropSource` hard-codes that
> detector and does not expose it — which is why the hint below the bowl says "hold".

The UI copy was written to match a platform constraint that couldn't be configured. That's the
right response: when you can't change the behaviour, change the instructions.

---

## Exercise

This one has no answer in the code — it's a design question. Try it before reading the solution.

**A.** Right now every chip yields one unit per drag. How would you support dragging *all*
remaining units at once with a double-tap?

<details>
<summary><b>Solution</b></summary>

**A.** Add `fun onIngredientDroppedAll(id: String)` to `GameViewModel`:

```kotlin
fun onIngredientDroppedAll(id: String) {
    _uiState.update { state ->
        val held = state.pantry.firstOrNull { it.ingredients.id == id } ?: return@update state
        if (held.currentAmount <= 0) return@update state
        state.copy(bowl = state.bowl + (id to (state.bowl[id] ?: 0) + held.currentAmount))
    }
}
```

Same guard-inside-update shape. The UI side is harder: `dragAndDropSource` hard-codes long-press
detection, so a double-tap can't start a drag. You'd need the payload to carry an amount —
`ClipData.newPlainText("ingredient", "$id:$amount")` — and a tap-to-add fallback outside the
drag system. Worth noting that the *logic* change is five lines and the *interaction* change is
the hard part; that's a good instinct to develop about where cost actually lives.

</details>

---

## Self-check

1. What does `rememberUpdatedState` fix, and why can't you just capture the callback directly?
2. Why is `dragAndDropTarget` placed before `.padding()` and `.scale()`?
3. What goes wrong with the snapshot-based `dragAndDropSource` overload here?
4. How is an empty chip made undraggable, and why is that better than an `enabled` flag?
5. The payload is a string id rather than the `Ingredients` object. Why?

<details>
<summary><b>Answers</b></summary>

1. It bridges "this object must be created once" with "this callback changes every
   recomposition." The `DragAndDropTarget` is `remember`ed so it isn't re-registered mid-drag;
   `onIngredientDropped` is a method reference recreated every recomposition. Capturing it
   directly inside the `remember` block would freeze the *first* one forever — a stale closure.
   `rememberUpdatedState` gives a stable `State` holder whose `.value` is refreshed every
   recomposition, so the target reads the current callback at call time.

2. Modifiers apply outside-in. `dragAndDropTarget` before `padding` makes the 12dp of padding
   *part of* the drop region, so a near-miss still counts — a more forgiving target, for free.
   `scale` last means it's a draw-only transform: the bowl visually pulses when hovered while
   its drop bounds stay put. Scaling before the target would make the hit area move under the
   player's finger mid-drag.

3. That overload caches the composable into a graphics layer and replays the recording for every
   later frame. The chip shows a live count, so after the first drag the shadow is frozen showing
   the old number. Drawing the decoration by hand in `drawDragDecoration` avoids the recording
   entirely — and drawing only the icon (no count) means there's nothing that *can* go stale.

4. `transferData` returns `null` when `currentAmount <= 0`, so no payload is produced and the
   platform never starts the drag. Better than a flag because there's no second source of truth
   to keep in sync — the source is *unable* to produce a drag rather than *permitted not to*.
   Paired with the 0.45 alpha dimming, the player sees it and feels it.

5. Because the payload crosses into Android's platform drag-and-drop, which carries `ClipData` —
   designed for cross-app transfer, not Kotlin objects. Sending `Ingredients` would require
   `Parcelable` and serialisation for no gain: the receiver already has the full ingredient list
   and can look up `"heart"` instantly. Send the key, not the object.

</details>

---

Next: [08 — Layout, Theme and Art](08-layout-theme-and-art.md)
