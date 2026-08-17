# 05 — The Dialog Overlay

## What you'll learn

- Why this is an in-composition overlay and not an Android `Dialog`
- `AnimatedContent`, `contentKey`, and `SizeTransform`
- The "hold the last value" trick for exit animations
- Blocking input two different ways, and why both are needed
- Reading `target`, not the captured variable, inside an animation block

The file is [`DialogOverlayHost.kt`](../../app/src/main/java/com/cinthya/birthdaycake/ui/components/dialog/DialogOverlayHost.kt)
— 416 lines, the largest in the project. Most of it is careful animation timing.

---

## Why not a real Dialog?

Compose has `androidx.compose.ui.window.Dialog`. This project doesn't use it. From the doc
comment at line 105:

> A platform dialog is a separate window, so its dim would fall over the whole app — cat
> included — and the only way to put the character back on top would be to draw a second one
> inside the dialog window, which is the exact duplication this design removes. A window
> boundary also makes a shared size animation impossible.

Two independent reasons:

**1. The cat must stay above the dim.** A platform `Dialog` is a new window with its own dim
layer over everything beneath it. The cat lives in the activity's window, so it would be
dimmed. To un-dim it you'd draw a *second* cat inside the dialog — and now two cats exist, and
they can disagree. Module 08 covers why that's unacceptable here.

**2. Size animation can't cross a window boundary.** The panel grows from a two-line greeting
to a twelve-row recipe card, smoothly. That needs the panel and its container in the same
layout pass. Two windows, two layout passes, no shared animation.

So the overlay is just a `Box` drawn on top, inside the same composition:

```kotlin
// GamePageScaffold.kt:57
Box(...) { scope.content() }   // the page
overlay()                       // the dialog layer
CharacterPlaceholder(...)       // the cat — last, so on top of both
```

Z-order in a Compose `Box` is **declaration order**: later siblings draw on top. The cat is
declared last, so it's above the scrim. That single ordering is the whole solution.

---

## Holding the last value through the exit

```kotlin
// DialogOverlayHost.kt:124
var shown by remember { mutableStateOf(active) }
if (active != null && active != shown) shown = active
```

This looks odd. Why keep a second copy of something you were handed?

Because of the exit animation. When a conversation ends, `GameViewModel` sets `dialog = null`.
The `AnimatedVisibility` below needs ~220ms to fade out — but `active` is *already* null. If
the panel drew from `active`, it would empty instantly and you'd watch an empty box fade away.

So: `shown` holds the last non-null value. `active` controls *whether* it's visible; `shown`
controls *what's drawn*.

```kotlin
AnimatedVisibility(
    visible = active != null,        // ← drives the fade
    enter = fadeIn(tween(220)),
    exit = fadeOut(tween(220)),
) {
    // ...
    shown?.let { current ->
        DialogPanel(active = current, ...)   // ← drives the content
    }
}
```

The guard `if (active != null && active != shown)` only ever assigns non-null, so `shown` never
goes back to null once set. The panel keeps its last content through the fade.

**Note the pattern name:** writing to a `remember`d state directly in the composable body,
rather than in a `LaunchedEffect` or an event handler. That's normally discouraged — but here
it's a pure derivation from the parameter, it's idempotent, and it can't loop (the condition is
false immediately after assignment). It's a known-safe use of the pattern.

---

## `AnimatedContent` — the core of the transition

```kotlin
// DialogOverlayHost.kt:201
AnimatedContent(
    targetState = active,
    contentKey = { it.sequence.id to it.index },
    contentAlignment = Alignment.TopStart,
    transitionSpec = DialogStepTransition,
    label = "dialogStep",
    modifier = Modifier.fillMaxWidth()
) { target ->
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        DialogStepBody(target.step, onAdvance)
        if (target.step.advancesOnTap) {
            ContinueCaret(Modifier.align(Alignment.End).padding(top = 8.dp))
        }
    }
}
```

`AnimatedContent` keeps **both** the old and new content alive during a transition, running
one out while the other comes in.

### `contentKey` — explicit identity

```kotlin
contentKey = { it.sequence.id to it.index }
```

By default `AnimatedContent` compares `targetState` with `==`. Here it's told to compare a
`Pair<DialogSequenceId, Int>` instead. Two reasons, both in the comment:

> Cheap, explicit identity: two steps with identical copy still transition, and a different
> sequence at index 0 still counts as a change.

- **Identical copy still transitions.** The finale reuses `R.string.intro_sleep` at step 2
  ([`DialogCatalog.kt:66`](../../app/src/main/java/com/cinthya/birthdaycake/data/DialogCatalog.kt)).
  If two adjacent steps ever had identical content, `==` would say "no change" and the
  transition would be skipped. The key includes the index, so it always changes.
- **A new sequence at index 0 counts.** When INTRO ends and RECIPE starts, both are at
  `index = 0`. Comparing only the index would see no change. The key includes the sequence id.

- It's also **cheaper** — comparing an enum and an `Int` versus deep-comparing a whole
  `DialogSequence` including its list of steps.

### `target`, not `active` ⭐

```kotlin
} { target ->
    // Read ONLY from `target`. Touching the captured `active` here would make the outgoing
    // copy recompose with the new step, and the crossfade would collapse into a flicker of
    // the same text.
    DialogStepBody(target.step, onAdvance)
```

This is subtle and important.

The content lambda is invoked **twice during a transition** — once for the outgoing state, once
for the incoming. `target` is whichever one this invocation is for.

`active` is captured from the enclosing scope and always holds the *newest* value. So if you
wrote `DialogStepBody(active.step, ...)`, the outgoing copy would recompose with the new step's
content. You'd be crossfading step 4 into step 4 — a flicker, not a transition.

**The rule:** inside an `AnimatedContent` (or `Crossfade`) content lambda, read only the lambda
parameter. Never the captured state.

This is one of the most common Compose animation bugs, and it produces a symptom ("my
crossfade doesn't work") whose cause is nowhere near the animation code.

---

## The transition spec

```kotlin
// DialogOverlayHost.kt:293
private val DialogStepTransition: AnimatedContentTransitionScope<ActiveDialog>.() -> ContentTransform = {
    val enter = fadeIn(tween(CONTENT_IN_MS, delayMillis = CONTENT_OUT_MS)) +
        slideInVertically(
            tween(CONTENT_IN_MS, delayMillis = CONTENT_OUT_MS, easing = FastOutSlowInEasing)
        ) { fullHeight -> fullHeight / 12 }

    val exit = fadeOut(tween(CONTENT_OUT_MS))

    enter togetherWith exit using SizeTransform(clip = true) { initialSize, targetSize ->
        val delta = abs(targetSize.height - initialSize.height)
        tween(
            durationMillis = (SIZE_MIN_MS + delta * SIZE_MS_PER_PX)
                .roundToInt()
                .coerceIn(SIZE_MIN_MS, SIZE_MAX_MS),
            easing = FastOutSlowInEasing
        )
    }
}
```

Three things happen at once, on a deliberate schedule:

```
   0ms        90ms                    240ms
   │──────────│───────────────────────│
   │ old text │
   │ fades out│
              │ new text fades + slides in │
   │                                       │
   │ panel height eases: 220–420ms depending on distance │
```

**The 90ms gap is the point.** `fadeIn` has `delayMillis = CONTENT_OUT_MS`, so the new text
doesn't start appearing until the old one is gone. From the comment:

> two lines of body copy legible at the same time read as mud rather than as a change

A plain crossfade would have both at 50% opacity in the middle, overlapping. Sequencing them
means you always read exactly one thing.

**`slideInVertically { fullHeight -> fullHeight / 12 }`** — the new text starts one-twelfth of
its height below its final position and rises. A small lift that reads as "new content
arriving" without being a slideshow.

**`enter togetherWith exit`** — infix syntax for combining. `A togetherWith B` = "run enter A
and exit B simultaneously." (It replaced the older `with` keyword.)

**`SizeTransform(clip = true)`** — animates the container's size between old and new. `clip`
matters:

> `clip = true` is required: without it the taller incoming body spills past the border before
> the frame has grown to meet it.

Without clipping, a tall recipe card would render at full height immediately while the cream
frame is still growing — text hanging outside the box.

**The distance-scaled duration** is the nicest detail:

```kotlin
val delta = abs(targetSize.height - initialSize.height)
durationMillis = (220 + delta * 0.35f).coerceIn(220, 420)
```

A one-word change moves ~0px → 220ms. Jumping to the recipe card moves ~400px → 220 + 140 =
360ms. Without this, either short hops feel sluggish or big jumps feel abrupt. Physical motion
takes longer over longer distances, and matching that is why it feels right.

And it's **free for future content**:

> Every future variant inherits this for free — `SizeTransform` interpolates an `IntSize` and
> does not care what is inside.

---

## The footnote, animated separately

```kotlin
// DialogOverlayHost.kt:226
Crossfade(
    targetState = active.step.footnote,
    animationSpec = tween(CONTENT_IN_MS, delayMillis = CONTENT_OUT_MS),
    label = "dialogFootnote"
) { footnote -> ... }
```

The footnote sits *below* the frame, outside it — so it can't live inside the `AnimatedContent`,
which only covers the frame's contents. It gets its own `Crossfade`, with **the same durations
and the same delay**, so it changes on the same beat.

Two animations, manually kept in sync by sharing constants. Slightly fragile — if someone
changes one duration and not the other, they drift. Worth knowing as a small tidy-up:
extracting a shared `AnimationSpec` value would make it structural.

---

## Blocking input, twice

The dialog must be non-dismissable — the game is linear, and skipping a conversation would
break the flow. Two independent blocks:

### 1. The back button

```kotlin
// DialogOverlayHost.kt:120
// Non-dismissable, half one. The empty body is the point: it exists to stop the event.
BackHandler(enabled = active != null) { }
```

`BackHandler` registers a callback with the activity's `OnBackPressedDispatcher`. An **empty
body** means: intercept the back press and do nothing. `enabled = active != null` means it's
only registered while a dialog is up — press back on the memory board and normal behaviour
resumes.

The empty lambda looks like a mistake, which is why it's commented. Consuming an event by
handling it with nothing is a legitimate technique.

### 2. Touches on the page beneath

```kotlin
// DialogOverlayHost.kt:320
private fun Modifier.swallowGestures(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false).consume()
        do {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
        } while (event.changes.any { it.pressed })
    }
}
```

A `Box` filling the screen with `.background(Scrim)` *draws* over the page but doesn't stop
touches — Compose hit-testing would still deliver taps to the memory cards underneath.

This modifier consumes every pointer event:

- `awaitEachGesture { }` — loop, one gesture at a time
- `awaitFirstDown(requireUnconsumed = false)` — wait for a finger down, even if something else
  already consumed it
- `.consume()` — mark it handled, so nothing else sees it
- the `do/while` — keep consuming moves and the up, until no finger is pressed

**Why the panel still works** ([comment at line 316](../../app/src/main/java/com/cinthya/birthdaycake/ui/components/dialog/DialogOverlayHost.kt)):

> The default (Main) pointer pass is deliberate. Main is delivered topmost-node-first, so the
> panel, a later sibling, still receives its own taps; only what misses the panel reaches here,
> and here it stops.

Compose delivers pointer events in three passes: **Initial** (parent → child), **Main** (child
→ parent, i.e. topmost first), and **Final**. `pointerInput` defaults to Main, so the panel —
declared *after* the scrim, therefore on top — gets first refusal. Only touches that miss the
panel reach the scrim, where they die.

If this used `PointerEventPass.Initial`, the scrim would eat everything including the panel's
own taps, and the dialog would be unadvanceable.

---

## The tap target

```kotlin
// DialogOverlayHost.kt:187
DialogFrame(
    modifier = Modifier
        .fillMaxWidth()
        .heightIn(max = maxPanelHeight)
        // The tap target belongs to the frame, not the body: it must not blink in and out
        // with every step, and it has to cover the panel's padding too.
        .clickable(
            enabled = active.step.advancesOnTap,
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onAdvance
        )
)
```

The clickable is on the **frame**, which sits outside `AnimatedContent`. If it were on the body
inside, it would be destroyed and recreated on every step — and during a transition there'd be
two of them, or briefly none.

`indication = null` + a manual `interactionSource` disables the ripple. A pixel-art cream panel
with a Material ripple would look wrong.

`enabled = active.step.advancesOnTap` — steps *with* a button aren't tappable, so you must press
the button. That's `advancesOnTap` (Module 04) doing its job.

---

## Positioning: lining up with the cat

```kotlin
// DialogOverlayHost.kt:133
BoxWithConstraints(Modifier.fillMaxSize()) {
    val maxPanelHeight = maxHeight * PANEL_MAX_HEIGHT      // 0.74
    val topOffset = maxWidth * CHARACTER_WIDTH_FRACTION    // 0.24
```

`BoxWithConstraints` gives you `maxWidth`/`maxHeight` as `Dp` inside its content — the standard
way to size things relative to available space.

`topOffset` uses the **same constant** the scaffold uses to size the cat
(`CHARACTER_WIDTH_FRACTION` from `GamePageScaffold.kt:22`). The cat is square, parked top-right,
so its height equals `maxWidth * 0.24`. Offsetting the panel by that amount puts it directly
beneath the cat — and it stays correct on any screen, because both derive from one constant.

`PANEL_MAX_HEIGHT = 0.74f` caps the panel at 74% of the screen:

> Never let a tall variant grow far enough to swallow the cat.

And when content exceeds that cap, the `verticalScroll(rememberScrollState())` inside the
`AnimatedContent` lets it scroll rather than clip.

---

## The previews

The bottom third of the file is `@Preview`s: desc-only, header+desc+button, recipe, recipe on a
small phone, ran-out. Then this one, at line 403:

```kotlin
/**
 * The one that matters: the overlay in the place it really runs, over a real page. It is the
 * only preview that shows the layering — the page dimmed by the scrim, the panel and the cat
 * above it, and exactly one cat on screen.
 */
@Preview(showBackground = true, name = "Over a page", heightDp = 891)
@Composable
private fun DialogOverPagePrev() {
    val dialog = ActiveDialog(DialogCatalog.intro, index = DialogCatalog.intro.steps.lastIndex)
    BirthdayCakeTheme {
        GamePageScaffold(
            expression = dialog.step.expression,
            overlay = { DialogOverlayHost(dialog, {}) }
        ) {
            MixingPage(GameData.emptyInventory, bowlCount = 0, isMixReady = false)
        }
    }
}
```

Component previews check a component. **This one checks a decision** — that the layering is
right and there's exactly one cat. That's a habit worth keeping: preview the composition, not
just the components.

Note also `DialogRecipeSmallPrev` (line 386): the same preview at 320×640. Responsive checking
without an emulator.

---

## Self-check

1. Why does `shown` exist when `active` is right there?
2. Inside the `AnimatedContent` lambda, why is reading `active` instead of `target` a bug?
3. `contentKey = { it.sequence.id to it.index }`. Give a concrete scenario where the default
   `==` comparison would fail.
4. `BackHandler(enabled = active != null) { }` — the body is empty. Why isn't that dead code?
5. The scrim consumes all pointer events, yet tapping the panel still advances the dialog.
   Explain.
6. Why is the size animation's duration computed from the height delta instead of being a
   constant?

<details>
<summary><b>Answers</b></summary>

1. For the exit animation. When a conversation ends, `active` becomes `null` immediately, but
   `AnimatedVisibility` needs 220ms to fade out. Drawing from `active` would empty the panel
   instantly and you'd watch a blank box fade. `shown` holds the last non-null value so there's
   still content to draw on the way out.

2. The content lambda runs twice during a transition — once for the outgoing state, once for the
   incoming. `target` is the correct state for *that* invocation; `active` is captured from the
   enclosing scope and always holds the newest value. Reading `active` makes the outgoing copy
   recompose with the new content, so you crossfade step 4 into step 4 — a flicker where a
   transition should be.

3. Two. (a) INTRO finishes and RECIPE starts — both `ActiveDialog`s are at `index = 0`, and if
   the key were the index alone there'd be no detected change. (b) Two adjacent steps with
   identical content — e.g. the finale reuses `R.string.intro_sleep` — would compare equal under
   `==` and the transition would be skipped. The `(id, index)` pair distinguishes both cases,
   and is cheaper than deep-comparing a whole sequence.

4. Because registering a `BackHandler` *intercepts* the back press. The empty body means
   "handled, do nothing" — the event is consumed and never reaches the activity's default
   finish behaviour. Removing it would let back exit the app mid-conversation. The `enabled`
   flag scopes it to exactly when a dialog is up.

5. Pointer events are delivered in passes, and `pointerInput` defaults to `PointerEventPass.Main`,
   which goes **topmost node first**. The panel is declared after the scrim in the `Box`, so it's
   above it in z-order and gets the event first. Only touches that miss the panel reach the
   scrim, where `swallowGestures` consumes them so nothing on the page beneath ever sees them.

6. Because a fixed duration is wrong at one end or the other. A one-word change moving 0px over
   400ms feels sluggish; the jump to the recipe card moving 400px over 220ms feels abrupt. Real
   motion takes longer over longer distances. The formula `220 + delta × 0.35`, clamped to
   [220, 420], gives short hops a snappy floor and long ones proportionally more time, with a
   ceiling so a huge jump never drags.

</details>

---

Next: [06 — The Memory Game](06-the-memory-game.md)
