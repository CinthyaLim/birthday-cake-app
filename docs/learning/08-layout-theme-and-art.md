# 08 — Layout, Theme and Art

## What you'll learn

- The scaffold pattern, and the "exactly one cat" rule
- Edge-to-edge layout and window insets done manually (and why a `Scaffold` failed)
- A custom `Shape` drawing pixel-art corners with a `Path`
- A real performance reason to avoid `Modifier.border`
- Animated GIFs via Coil, and Lottie for the finale
- Why the theme rejects Material You

---

## `GamePageScaffold` — the frame every page sits in

[`GamePageScaffold.kt`](../../app/src/main/java/com/cinthya/birthdaycake/ui/components/GamePageScaffold.kt)
is 77 lines and carries one of the project's most important decisions.

```kotlin
@Composable
fun GamePageScaffold(
    expression: CharacterExpressions,
    modifier: Modifier = Modifier,
    systemInsets: PaddingValues = PaddingValues(0.dp),
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable GamePageScope.() -> Unit
) {
    BoxWithConstraints(modifier.fillMaxSize().background(GameColors.PastelPink)) {
        val scope = remember(maxWidth) { GamePageScope(maxWidth * CHARACTER_WIDTH_FRACTION) }

        Box(Modifier.fillMaxSize().padding(systemInsets).padding(PAGE_PADDING)) {
            scope.content()
        }

        overlay()

        CharacterPlaceholder(
            expression,
            Modifier.align(Alignment.TopEnd).padding(systemInsets).padding(PAGE_PADDING)
                .size(scope.characterSize)
        )
    }
}
```

### ⭐ Exactly one cat

Three children, in this order: **page**, **overlay**, **cat**. In a Compose `Box`, later
siblings draw on top. So the cat is above the dialog's scrim.

The comment explains why this had to change:

> The character is drawn here, last, so it sits above both the page and the overlay's scrim.
> That is why it moved out of the pages: a dialog speaks with its own expression, so with a copy
> in the page and a copy in the overlay there were two cats, and the page's one was dimmed while
> the dialog's was not.

**The bug that was fixed:** originally each page drew its own cat, and the dialog drew one too.
When a dialog opened you had two cats — one dimmed by the scrim, one not — in slightly different
positions.

**The fix:** exactly one cat exists, drawn by the scaffold, and everything that wants to
influence it *reports a mood*. Modules 03 and 06 showed both halves of that reporting chain.

This is a pattern worth naming: **when two components can disagree about something, make one of
them the only one that exists.** The bug becomes structurally impossible rather than carefully
avoided.

### `GamePageScope` — passing layout info down

```kotlin
@Immutable
data class GamePageScope(val characterSize: Dp)
```

The cat is drawn by the scaffold, floating in the top-right corner **outside the page's layout
flow**. But the page needs to know how big it is, so it can leave that corner empty:

```kotlin
// MixingPage.kt:116
// The cat lives in the scaffold now, above the dialog scrim. This just keeps its corner clear.
Spacer(Modifier.size(characterSize))
```

Pages are declared as **extension functions on the scope**:

```kotlin
fun GamePageScope.MixingPage(...)
fun GamePageScope.MiniHuntPage(...)
fun GamePageScope.FinalPage(...)
```

so `characterSize` is available as if it were a local. Same idea as `BoxScope.align()` or
`RowScope.weight()` — Compose's standard way of exposing "things only valid inside this parent."

**Why a `Dp` and not a fraction?** From `MiniHuntPage.kt:130`:

> A Dp size rather than a width fraction, which in a weighted Row would measure against the
> leftover space.

`fillMaxWidth(0.24f)` inside a `Row` where a sibling has `weight(1f)` measures 24% of what's
*left*, not 24% of the row. A `Dp` is absolute and can't be misread by context.

**`remember(maxWidth)`** — recompute the scope only when the width actually changes (rotation,
resize), not on every recomposition. A keyed `remember` is the general form: cache until this
input changes.

---

## Edge-to-edge and insets

```kotlin
// MainActivity.kt:30
enableEdgeToEdge()
```

The app draws behind the status and navigation bars. Necessary here because the pink background
and the dialog scrim must reach the screen edges — a scrim that stops short of the status bar
looks broken.

But *content* must not sit under the system bars. So insets are passed down as a value:

```kotlin
// MainActivity.kt:43
BirthdayCakeApp(
    modifier = Modifier.fillMaxSize(),
    systemInsets = WindowInsets.safeDrawing.asPaddingValues()
)
```

and applied selectively in the scaffold — to the content and the cat, **not** to the root. The
pink background and the scrim fill the whole screen; everything readable is inset.

### The `Scaffold` trap

The comment at `MainActivity.kt:37` records a real debugging session:

> These are read here rather than taken from a Scaffold. A Scaffold with
> `contentWindowInsets = WindowInsets(0)` reports zero in its inner padding — that is what the
> parameter means — so routing systemInsets through it handed every page an empty PaddingValues,
> and the bottom button sat under the navigation bar.

The reasoning trap: *"I want my background full-bleed, so I'll set `contentWindowInsets =
WindowInsets(0)`."* But that parameter doesn't mean "don't consume insets" — it means "report
zero insets to your content." So `innerPadding` came back empty, every page got
`PaddingValues(0)`, and the Mix button ended up under the gesture bar.

Reading `WindowInsets.safeDrawing.asPaddingValues()` directly is unambiguous.

`safeDrawing` is the right inset set: it covers system bars, display cutouts, and the IME.
(`systemBars` alone would miss a notch.)

---

## `NotchedFrameShape` — pixel-art corners, drawn

[`NotchedFrameShape.kt`](../../app/src/main/java/com/cinthya/birthdaycake/ui/components/dialog/NotchedFrameShape.kt)
is a custom `Shape` producing the stepped corners of the dialog panel.

### Why not just use the PNG?

> `img_dialog_box_plain.png` is a flat cream rectangle inside a flat black border, with a
> two-tread staircase cut into each corner and no interior detail at all — so a path reproduces
> it exactly. Drawing it is what lets the treads stay square and the border stay even at any
> panel height, which matters here because the panel's height is animated.

The panel's height animates continuously from a two-line greeting to a twelve-row recipe card
(Module 05). A stretched bitmap would distort the corner treads and thicken the border unevenly
as it grew. A nine-patch would help but still can't keep the treads square.

A path is resolution-independent and stays correct at every intermediate height of the
animation. Since the art has no interior detail, nothing is lost.

### The implementation

```kotlin
@Immutable
data class NotchedFrameShape(val step: Dp = FRAME_STEP, val steps: Int = 2) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val n = steps.coerceAtLeast(1)
        val unit = min(with(density) { step.toPx() }, size.minDimension / (4f * n))
        val w = size.width
        val h = size.height

        val path = Path().apply {
            moveTo(n * unit, 0f)
            lineTo(w - n * unit, 0f)
            for (i in 1..n) {                    // top-right staircase
                lineTo(w - (n - i + 1) * unit, i * unit)
                lineTo(w - (n - i) * unit, i * unit)
            }
            // ... three more corners
            close()
        }
        return Outline.Generic(path)
    }
}
```

Each corner is a loop drawing two line segments per tread — one down, one across — producing the
staircase. Because it's computed from `size` on every layout, it's always correct.

**The clamp:**

```kotlin
val unit = min(step.toPx(), size.minDimension / (4f * n))
```

> Cap the staircase against the shorter side, so a squat panel degrades to a smaller notch
> instead of collapsing into a diamond.

If the panel is only 20px tall and each tread is 6dp (~18px on a 3x screen), the corners would
consume the entire height and the "rectangle" would become a diamond. The clamp keeps treads
proportional on small panels.

**Why a `data class`:**

> A data class so the background modifier's outline cache can tell two instances apart.

`Modifier.background(color, shape)` caches the computed outline and invalidates it when the
shape changes — compared with `equals()`. A regular class uses identity equality, so a new
instance each recomposition would blow the cache every frame. `data class` gives value equality,
so two `NotchedFrameShape()` instances are equal and the cache holds.

**Constants tied to the source art:**

```kotlin
val FRAME_STEP = 6.dp    // "16px on the 1368px source art reads as 6.dp here"
val FRAME_BORDER = 4.dp  // "12px on the source art, keeping the same 2:3 ratio to the tread"
```

Recording where a magic number came from is the difference between a constant someone can
maintain and one nobody dares touch.

---

## `DialogFrame` — two fills instead of a border

```kotlin
// DialogFrame.kt:46
Box(modifier.background(borderColor, shape).padding(borderWidth)) {
    Box(
        Modifier.fillMaxWidth().background(fillColor, shape).padding(contentPadding),
        content = content
    )
}
```

A black shape, padded inward, containing a cream shape. The black shows through as the border.

The obvious alternative is `.background(cream, shape).border(4.dp, black, shape)`. The comment
explains why it's avoided:

> For a generic (non-rounded-rect) outline, `Modifier.border` cannot simply stroke the path —
> half the stroke would fall outside the bounds — so foundation strokes at twice the width into
> an offscreen layer and clears the middle with `BlendMode.Clear`. That is a `saveLayer` every
> time the draw cache is invalidated, and this panel's size changes on every frame for the
> length of a step transition. Two path fills, no layers.

The chain: generic outline → `border` needs an offscreen layer → `saveLayer` is expensive →
the panel resizes every frame during a transition → an expensive operation per frame for
~300ms, on every dialog step.

Two path fills have no layer allocation at all.

This is a good example of *knowing why an API is slow* rather than just avoiding it
superstitiously. If someone asks "have you done any performance work?", this is an answer.

`DialogFrame` is also reused by `PixelButton` with a different fill colour — one shape
definition, two components.

---

## The cat: animated GIFs through Coil

Compose's `Image` + `painterResource` **cannot animate GIFs** — you get frame one, frozen.
Coil can.

```kotlin
// BirthdayCakeApplication.kt:13
class BirthdayCakeApplication : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                // ImageDecoder based, so it needs API 28 - which is our minSdk.
                add(AnimatedImageDecoder.Factory())
            }
            .build()
}
```

`AnimatedImageDecoder` is built on `ImageDecoder`, which requires API 28. The project's
`minSdk = 28` — so the comment is confirming the constraint is already satisfied, not just
noting it. (Coil offers `GifDecoder` for older APIs; unnecessary here.)

Remember this must be registered in the manifest with
`android:name=".BirthdayCakeApplication"`, or the decoder never loads and every cat is a still.

```kotlin
// CharacterPlaceholder.kt:28
@Composable
fun CharacterPlaceholder(expression: CharacterExpressions, modifier: Modifier = Modifier) {
    Crossfade(
        targetState = expression,
        animationSpec = tween(EXPRESSION_FADE_MS),   // 200
        label = "expression",
        modifier = modifier.aspectRatio(1f)
    ) { current ->
        if (LocalInspectionMode.current) {
            Image(painterResource(current.toImageResource()), null, Modifier.fillMaxSize())
        } else {
            AsyncImage(model = current.toImageResource(), contentDescription = null, ...)
        }
    }
}
```

**`LocalInspectionMode`** is `true` inside Android Studio's preview renderer. The preview has no
Coil image loader, so `AsyncImage` renders nothing and every preview containing the cat would be
empty. The branch falls back to a static `Image` — animation is lost in previews, which don't
animate anyway, and the previews are usable.

Worth knowing generally: `LocalInspectionMode` is how you stub out anything (network images,
sensors, ViewModels) that can't work in a preview.

**The crossfade runs on its own clock:**

> The crossfade runs on its own clock rather than inside the dialog's step animation, so a change
> of mood never restarts the panel's resize — and swapping GIFs mid-conversation does not cut.

Two independent animations that happen to be triggered by the same event. Coupling them would
mean a mood change restarts the panel resize.

Note also `contentDescription = null` — the cat is decorative and its mood is conveyed by the
dialog text. A screen reader announcing "cat yawning" on every step would be noise. Deliberately
null, not forgotten.

---

## Lottie on the final page

```kotlin
// FinalPage.kt:116
@Composable
private fun LoopingLottie(@RawRes res: Int, speed: Float, modifier: Modifier = Modifier, ...) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(res))
    LottieAnimation(
        composition,
        modifier = modifier,
        iterations = LottieConstants.IterateForever,
        speed = speed,
        contentScale = contentScale
    )
}
```

Three looping animations: confetti over the whole page, sparkles and hearts bound to the cake.
Layering is declaration order again — confetti first (behind), cake, then sparkle and heart
`matchParentSize()`d to the cake's `Box` so they decorate the cake rather than the screen.

```kotlin
private const val CONFETTI_SPEED = 0.5f
private const val SPARKLE_SPEED = 0.6f
private const val HEART_SPEED = 0.5f
```

> All three animations ship faster than a background flourish wants to be, so each plays below
> 1x. The confetti is the slowest because it covers the whole page.

Stock Lottie files are usually authored as hero animations. As ambient decoration they're
frantic. Slowing them is a two-second fix that transforms how the page feels.

And a genuinely useful piece of knowledge:

> The `.lottie` files are zip containers; Lottie detects that from the raw resource itself, so
> they load through the same `LottieCompositionSpec.RawRes` a bare `.json` would.

`.lottie` (dotLottie) is a zipped bundle; `.json` is raw. Same API either way.

---

## The theme

```kotlin
// Theme.kt:39
@Composable
fun BirthdayCakeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = GameColorScheme, typography = Typography, content = content)
}
```

**No dark theme. No dynamic colour.** Deliberate:

> The game is a fixed illustration, so it has one palette and one palette only — no dark variant,
> and dynamic colour deliberately off. Wallpaper-derived colours would repaint the dialog panels
> a different shade on every device.

Material You derives a palette from the user's wallpaper. Excellent for a productivity app;
wrong for a pixel-art illustration where the pink page and cream panel *are* the artwork.

This is a **defensible deviation from platform convention**, which is a more interesting thing
to discuss than compliance. The test is whether you can say why.

The scheme maps every Material role explicitly, and the comment picks out the one that matters:

> That matters most for `onSurface`: it is what an unstyled `Text` inherits, and on the
> wallpaper-derived scheme it could land anywhere — including near-white, which is invisible on
> the cream panel.

Leaving the template scheme in place means any `Text` you forget to colour inherits an unrelated
default. Mapping the roles makes forgetting safe.

---

## Self-check

1. Why is the cat drawn by the scaffold instead of by each page?
2. Why is `characterSize` a `Dp` rather than a width fraction?
3. Why does the dialog panel use a custom `Shape` instead of the PNG the art was drawn from?
4. Why two nested `background` fills instead of `background` + `border`?
5. What is `LocalInspectionMode` doing in `CharacterPlaceholder`?
6. The theme disables dynamic colour. Defend that in two sentences.

<details>
<summary><b>Answers</b></summary>

1. Because there must be exactly one. When pages drew their own and the dialog drew another,
   opening a dialog produced two cats — one dimmed by the scrim, one not. Drawing it once in the
   scaffold, declared last so it's above both the page and the scrim, makes disagreement
   structurally impossible. Everything that wants to influence the cat reports a mood instead.

2. Because a fraction is measured against whatever the parent offers, and that varies with
   context. In `MiniHuntPage`'s header the `Spacer` sits in a `Row` where a sibling has
   `weight(1f)` — `fillMaxWidth(0.24f)` there would measure 24% of the *leftover* space, not of
   the row. A `Dp` is absolute and means the same thing everywhere.

3. Because the panel's height is animated continuously between very different sizes. A stretched
   bitmap would distort the corner treads and thicken the border unevenly at every intermediate
   height. A path is computed from the actual size on every layout, so the treads stay square and
   the border stays even throughout. The art has no interior detail, so nothing is lost by
   drawing it.

4. Because for a *generic* (non-rounded-rect) outline, `Modifier.border` can't just stroke the
   path — it strokes at double width into an offscreen layer and clears the middle with
   `BlendMode.Clear`. That's a `saveLayer` per draw-cache invalidation, and this panel's size
   changes every frame for ~300ms on every dialog step. Two path fills allocate no layer at all.

5. It's `true` inside Android Studio's preview renderer, which has no Coil `ImageLoader`. Without
   the branch, `AsyncImage` renders nothing and every preview containing the cat is empty. The
   fallback draws a static `Image` — no animation, but previews don't animate anyway, and now
   they're usable.

6. The app is a fixed pixel-art illustration whose pink page and cream panel are the artwork, not
   a neutral surface — wallpaper-derived colours would repaint them differently on every device
   and break the composition. Material You exists to make utility apps feel personal; this isn't
   a utility app, and the constant palette is the product.

</details>

---

Next: [09 — Testing](09-testing.md)
