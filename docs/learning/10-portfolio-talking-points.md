# 10 — Portfolio Talking Points

You're putting this on GitHub and social media. This module is about talking about it well.

---

## The 30-second pitch

> It's an interactive birthday card built as a small game in Jetpack Compose — three screens, a
> memory-matching mini-game, and drag-and-drop mixing. The two things I'd point at are that the
> entire dialogue script is plain data rather than code, so adding a line is a one-line edit
> with no UI changes; and that all the game logic runs in JVM unit tests with no emulator,
> because nothing in it touches a `Context`.

Two claims, both specific, both verifiable by opening the repo. Better than "I built a game with
Compose and MVVM."

---

## Five things worth showing

Ranked by how interesting they are to someone who reads a lot of portfolio repos.

### 1. Dialogue as data (Module 04)

The whole script lives in `DialogCatalog` as `DialogStep` values. There's one renderer. Adding a
line means editing one list and adding a string.

The sharp detail: **`DialogStep` deliberately carries no lambdas.** Because it's a `data class`
feeding into `AnimatedContent`, a lambda field would make every step compare unequal to itself
and restart the transition on every recomposition — a permanently flickering dialog with a cause
nowhere near the animation code. Behaviour lives in one `when` in the ViewModel instead.

That's a story with a bug, a mechanism, and a fix. Those land.

### 2. Stored vs derived state (Module 02)

`MiniHuntUiState` stores two fields and derives seven. `GameUiState` stores five and derives
seven.

The example to use: `isMismatched`. Stored, it would be set by a match check and cleared by a
coroutine a second later — and if that coroutine is cancelled by a restart, the flag is stuck on
and the board is dead. Derived from the cards, that bug cannot exist.

"I designed the state so that class of bug is unrepresentable" is a much stronger sentence than
"I used MVVM."

### 3. The early-resolve in the memory game (Module 06)

Flip two cards, miss, and tap a third before the flip-back timer fires. Most implementations
swallow that tap and the game feels dead. This one cancels the timer, settles the old pair and
reveals the new card in a single atomic state update.

Plus the delayed job **re-checks the cards by id when it wakes**, so a restart or an early
resolve turns it into a no-op instead of flipping down cards on a new board.

This is the detail that says you actually played your own game.

### 4. Knowing why an API is slow (Module 08)

`DialogFrame` uses two nested background fills instead of `background` + `border`. The reason:
for a generic (non-rounded-rect) outline, `Modifier.border` strokes at double width into an
offscreen layer and clears the middle with `BlendMode.Clear` — a `saveLayer` per draw-cache
invalidation. The panel resizes every frame for ~300ms on every dialog step.

Most people avoid APIs by superstition. Knowing the mechanism is different.

### 5. Deliberate non-choices

- **No Navigation Compose** — three linear screens, no back stack (back is actively blocked), no
  deep links. An enum and a `when`.
- **No Hilt** — two default constructor parameters give `viewModel()` a no-arg constructor *and*
  let tests inject a seeded `Random`.
- **No Room, no repository** — a two-minute session with no data to persist.
- **No Material You, no dark theme** — the pink page and cream panel *are* the artwork; a
  wallpaper-derived palette would repaint them on every device.

Say these out loud in your README, with the reason and the line where they'd stop being right.
Unjustified absence looks like ignorance; justified absence looks like judgement.

---

## Interview questions, with answers

### "Walk me through the architecture."

Three layers. `model/` and `data/` are pure Kotlin values — no Compose, no `Context`. `ui/state/`
and `ui/viewmodel/` hold the rules. `ui/screen/` and `ui/components/` only draw.

Unidirectional data flow: state down as parameters, events up as lambdas. Two ViewModels —
`GameViewModel` for the whole game, `MiniHuntViewModel` for the memory board — and they touch at
exactly one point, when the board is cleared and `onFinished` carries the result up.

The test of the boundary is that the mini-game knows nothing about dialogs, screens or cakes.
You could lift it into another app.

### "Why two ViewModels instead of one?"

Different lifetimes and different concerns. `MiniHuntViewModel` is scoped to `MiniHuntPage` and
dies with it; `GameViewModel` lives for the session. Merging them would mean the memory board's
card state hangs around for the rest of the game, and the game's dialog logic would sit in the
same class as card matching.

The single wire between them is `onFinished(collected)`. That's the whole coupling.

### "Why no navigation library?"

Three screens, strictly linear, no back stack — back is actively blocked during dialogs — no deep
links, no arguments to serialize. Navigation Compose would add a dependency and a routing DSL to
solve problems this app doesn't have.

It would stop being the right call the moment I wanted a back stack, a settings screen you can
return from, or transitions between screens. Then the `when` becomes a liability.

### "How do you handle configuration changes?"

State lives in ViewModels, which survive configuration change — rotate mid-game and your cards
stay flipped and the dialog stays on the same line.

It deliberately does *not* survive process death. A session is about two minutes, so the cost
outweighs the benefit. But the design left the door open: because sequences are named by a
`DialogSequenceId` enum rather than passed around as objects, restoring a conversation is just
saving `(id, index)` — an enum name and an `Int`, both trivially `Bundle`-able. Adding a
`SavedStateHandle` wouldn't require touching the logic.

### "What's the trickiest bug you hit?"

Two good answers.

**The two cats.** Each page drew the character, and so did the dialog. Opening a dialog gave you
two — one dimmed by the scrim, one not. The fix wasn't to synchronise them, it was to make only
one exist: the scaffold draws it, declared last so it's above the scrim, and anything that wants
to influence it reports a mood upward. When two things can disagree, delete one of them.

**The insets.** I wanted the pink background full-bleed, so I set `contentWindowInsets =
WindowInsets(0)` on a `Scaffold` — which doesn't mean "don't consume insets," it means "report
zero to your content." Every page got an empty `PaddingValues` and the Mix button ended up under
the navigation bar. Now I read `WindowInsets.safeDrawing.asPaddingValues()` directly in the
activity and pass it down, applying it to content and the character but not to the root.

### "How is it tested?"

Three JUnit files, no emulator, no Robolectric — `DialogSequenceTest` for the cursor,
`GameUiStateTest` for derived properties, `GameViewModelTest` for the flow.

That's possible because of design choices made earlier: `DialogText` holds a string *resource
id* rather than a resolved string, so the catalog needs no `Context`; and the ViewModels import
no Compose.

The gaps are real and I know them: `MiniHuntViewModel` has no tests, which is the biggest one
since it's the most intricate logic in the project. `onMixClick` isn't covered because it
schedules on `viewModelScope` and needs a main dispatcher — that gap is documented in the test
file. And there are no Compose UI tests, though the test tags and dependencies are in place.

### "What would you do differently?"

See the next section — and answer it honestly. Nobody believes "nothing."

---

## Known gaps — say these before someone finds them

Being first to name your own weak points reads as confidence. Being caught out doesn't.

**1. `MiniHuntViewModel` is untested.** The most intricate logic in the codebase — early resolve,
cancellable jobs, the wake-up re-check — has no test coverage. It's harder to test than
`GameViewModel` because it schedules coroutines, so it needs `kotlinx-coroutines-test` and
`Dispatchers.setMain`. That's the top of the backlog. (Module 09 has a full sketch to work from.)

**2. No process-death survival.** Deliberate, and the design accommodates adding it. Worth saying
that you chose it rather than missed it.

**3. No accessibility audit.** There are genuinely thoughtful touches — face-down cards announce
"hidden card" rather than their symbol, so TalkBack doesn't give the board away; disabled buttons
are marked disabled rather than just swallowing taps; the cat is `contentDescription = null`
because it's decorative. But drag-and-drop has no keyboard or switch-access alternative, and
nothing has been tested with TalkBack end to end.

**4. Two animations kept in sync by hand.** The dialog body's `AnimatedContent` and the
footnote's `Crossfade` share timing constants but are separate animations. Change one duration
and forget the other and they drift. Extracting a shared `AnimationSpec` would make it structural.

**5. Only one Lottie speed constant is arguable.** Minor, but `CONFETTI_SPEED` and `HEART_SPEED`
are both `0.5f` — worth confirming that's intentional rather than a copy-paste.

---

## Two gaps this module found and closed

Both were caught by writing the walkthrough, then fixed. That sequence is itself the talking
point: documenting your own code is a review pass, and it found things the tests didn't.

**`GameData.totalIngredients` was hardcoded to `10`.** It equalled
`cakeIngredients.sumOf { it.requiredAmount }` only by arithmetic coincidence, so changing any
`requiredAmount` would grow the deck while the constant stayed put — "11 / 10 pairs found" in the
hunt header, and `isMixReady` firing before the bowl was full. Three wrong behaviours, no compile
error. Now derived. (Module 07 has the full story.)

**`take(3)` / `takeLast(3)` assumed exactly six ingredients.** In both `MixingPage` and
`MiniHuntPage`: with five, one item rendered in both rows; with seven, one disappeared. Both now
use `chunked(3)` — the pattern the card grid was already using two functions away.

The honest version if asked: neither could actually happen with the current six ingredients. They
were latent, not live. What makes them worth mentioning is the shape — a constant that duplicated
a computable fact, and a slice that encoded a list length nobody had written down.

---

## Roadmap ideas

Ordered by ratio of "shows skill" to "effort."

| Idea | What it demonstrates | Effort |
|---|---|---|
| `MiniHuntViewModelTest` | Coroutine testing, `Dispatchers.setMain`, virtual time | Low |
| Compose UI tests with the existing tags | `createComposeRule`, semantics, end-to-end testing | Medium |
| Sound effects with `SoundPool` | Lifecycle-aware resource management | Medium |
| Difficulty levels (board size) | Parameterising `buildDeck` — already designed for it | Medium |
| `SavedStateHandle` for process death | State restoration — the design already anticipates it | Medium |
| A "personalise it" screen (name, message) | Forms, validation, sharing an intent | Medium |
| CI with GitHub Actions running `./gradlew test` | Build automation, and a green badge on the README | Low |

That last one is worth doing before you post. A README with a passing-tests badge is read very
differently from one without.

---

## For the README

Things the learning module surfaced that belong in your public docs:

- **A GIF of the full playthrough at the top.** Non-negotiable for a game. Nobody clones a repo
  to see what it looks like.
- **The screenshot set** — mixing page, dialog with the recipe card, the memory board mid-game,
  the final page. You can generate most of these from the existing `@Preview`s.
- **Tech stack, one line:** Kotlin, Jetpack Compose, Material 3, ViewModel + StateFlow, Coil
  (animated GIF), Lottie. minSdk 28.
- **The architecture diagram** from Module 01.
- **The deliberate non-choices**, with reasons. Two sentences each.
- **How to build** — `./gradlew installDebug`, or open in Android Studio.
- **Credits for the art and the Lottie files.** If any asset came from a third party, license and
  attribution go in the README. This matters more than people think when a repo gets attention.
- **A licence file.** You said "free to people" — that needs to be stated. MIT is the usual
  choice for a portfolio project; it lets people learn from and reuse the code with attribution.
  Note that a licence covers your *code*, not necessarily third-party art assets — if the art has
  its own terms, say so separately.
- **A link to `docs/learning/`.** This module is itself a portfolio artifact. "I documented my own
  codebase as a teaching resource" is a real signal.

---

## One honest caution

You built this with Claude Code, and you're posting it as a portfolio piece. Two things worth
being deliberate about:

**Be straightforward if it comes up.** Using AI tooling is normal and increasingly expected. What
distinguishes people is whether they understand and can defend what shipped. That's exactly what
this module is for — and having worked through it, you can walk anyone through any decision in
this codebase. That's the thing that actually matters.

**Make sure the understanding is real, not memorised.** The self-check questions matter more than
the explanations. If you can answer them cold, in your own words, and argue with a choice you
disagree with, the code is yours in every sense that counts. A reviewer will find out in about
four minutes of conversation which one it is.

---

## Final self-check

No answers for these. If you can respond to all six without opening the code, you know this
project.

1. Explain unidirectional data flow using one concrete path through this app.
2. Why does `DialogStep` contain no lambdas? What breaks if it does?
3. Name three values that are derived rather than stored, and say what bug each one prevents.
4. Why is there exactly one cat, and what did the two-cat version look like?
5. What happens if you tap a third card while a mismatched pair is still showing? Why was it
   built that way?
6. Which library did you deliberately not use, and where's the line at which that becomes the
   wrong call?

---

← [Back to the index](README.md)
