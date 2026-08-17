# 01 — The Big Picture

## What you'll learn

- What the app actually does, as a player experiences it
- The three-layer architecture and what lives in each layer
- Where every file sits and why it's in that folder
- The two ViewModels and the single point where they meet

---

## What the app is

A birthday card that you play instead of read. A cat wakes up, tells you it's someone's
birthday, shows you a recipe, discovers the pantry is empty, sends you off to find the
ingredients in a memory-matching game, then lets you drag what you found into a mixing bowl.
Mix, and you get a cake with confetti and a closing message.

Three screens. One character. No network, no database, no login. Everything happens in memory
and the whole thing lasts about two minutes.

That constraint is important, and it's worth saying out loud in your README: **this app has no
persistence layer because it doesn't need one.** A reviewer who sees no Room database will
either think you didn't know how, or that you made a call. Make it clear you made a call.

---

## The player's journey

```
     ┌─────────────────────────────────────────────────────┐
     │  MIXING screen                                      │
     │                                                     │
     │  INTRO dialog     "zzz... oh! hello"                │
     │       ↓                                             │
     │  RECIPE dialog    the recipe card                   │
     │       ↓                                             │
     │  RAN_OUT dialog   "the pantry is empty!"            │
     └───────────────────────┬─────────────────────────────┘
                             ↓
     ┌─────────────────────────────────────────────────────┐
     │  HUNT_INGREDIENTS screen                            │
     │                                                     │
     │  20-card memory game, 10 pairs                      │
     │  clear the board  ──────────────┐                   │
     └─────────────────────────────────┼───────────────────┘
                                       ↓
     ┌─────────────────────────────────────────────────────┐
     │  MIXING screen (again — now stocked)                │
     │                                                     │
     │  HUNT_WON dialog  "you found 10 things!"            │
     │       ↓                                             │
     │  drag 10 ingredient units into the bowl             │
     │  "Mix It!" unlocks                                  │
     └───────────────────────┬─────────────────────────────┘
                             ↓
     ┌─────────────────────────────────────────────────────┐
     │  FINAL screen                                       │
     │                                                     │
     │  cake + confetti, 900ms pause, FINALE dialog        │
     └─────────────────────────────────────────────────────┘
```

Notice the mixing screen is visited **twice**. The first time the pantry is empty (all chips
show 0), the second time it's stocked with whatever the hunt turned up. Same screen, different
state — that's not a coincidence, it's the design. There is no "empty pantry screen" and
"stocked pantry screen"; there is one screen that draws whatever state it's given.

That idea — *one component, many states* — repeats everywhere in this codebase. Hold onto it.

---

## The architecture

Three layers, top to bottom:

```
   ┌──────────────────────────────────────────────────────────────┐
   │  UI            Composables. Draw state, emit events.         │
   │                Own no game logic.                            │
   │                                                              │
   │                MainActivity, ui/screen/, ui/components/      │
   └──────────────────────────────────────────────────────────────┘
                      ↑ state flows up          events flow down ↓
   ┌──────────────────────────────────────────────────────────────┐
   │  STATE + LOGIC  ViewModels hold the truth and the rules.     │
   │                 UiState classes shape it for drawing.        │
   │                                                              │
   │                 GameViewModel, MiniHuntViewModel             │
   │                 GameUiState, MiniHuntUiState                 │
   └──────────────────────────────────────────────────────────────┘
                                    ↑ reads
   ┌──────────────────────────────────────────────────────────────┐
   │  DATA          Plain immutable values. No Compose, no        │
   │                Android Context, no behaviour.                │
   │                                                              │
   │                GameData, DialogCatalog, model/               │
   └──────────────────────────────────────────────────────────────┘
```

This is **MVVM** with **unidirectional data flow (UDF)**. The names matter less than the rule:

> State flows **down**. Events flow **up**. Nothing flows sideways.

A composable never reaches into another composable's state. A screen never calls another
screen. When `MiniHuntPage` finishes, it doesn't navigate anywhere — it calls
`onFinished(collected)`, an event travels up to `GameViewModel`, and `GameViewModel` decides
what happens next. The page doesn't know there *is* a next.

---

## The two ViewModels

Most tutorials show one ViewModel per app or one per screen. This project has two, and the
split is deliberate:

| | `GameViewModel` | `MiniHuntViewModel` |
|---|---|---|
| **Scope** | The whole game | The memory board only |
| **Owns** | Current screen, inventory, bowl contents, active dialog, cat expression | The 20 cards, the move counter |
| **Lives in** | `MainActivity` / `BirthdayCakeApp` | `MiniHuntPage` |
| **Lifetime** | Entire app session | Recreated whenever `MiniHuntPage` leaves composition |
| **File** | [`GameViewModel.kt`](../../app/src/main/java/com/cinthya/birthdaycake/ui/viewmodel/GameViewModel.kt) | [`MiniHuntViewModel.kt`](../../app/src/main/java/com/cinthya/birthdaycake/ui/viewmodel/MiniHuntViewModel.kt) |

**They meet exactly once.** When the board is cleared, `MiniHuntPage` fires `onFinished(...)`,
which is wired to `GameViewModel::onHuntFinished`. That's the only wire between them. The
memory game knows nothing about dialogs, screens, bowls or cakes — you could lift
`MiniHuntViewModel` + `MiniHuntUiState` + `FlipCard` into a different app and it would work.

That's a real architectural talking point. It's not "I split my code into files" — it's
"the mini-game is a self-contained module with one output port."

---

## The file map

```
app/src/main/java/com/cinthya/birthdaycake/
│
├── MainActivity.kt              Entry point. Sets up the theme, edge-to-edge,
│                                and the one `when` that swaps screens.
├── BirthdayCakeApplication.kt   Configures Coil so the cat GIFs animate.
│
├── model/                       ── PURE DATA. No Compose. No logic. ──
│   ├── GameScreen.kt            enum: MIXING / HUNT_INGREDIENTS / FINAL
│   ├── Ingredients.kt           Ingredients + IngredientsCount
│   ├── MemoryCard.kt            One tile on the board
│   ├── CharacterExpressions.kt  enum of cat moods → drawable mapping
│   └── dialog/
│       ├── DialogStep.kt        DialogText, DialogStep, DialogButton, DialogExtra
│       └── DialogSequence.kt    DialogSequenceId, DialogSequence, ActiveDialog
│
├── data/                        ── THE CONTENT. Still pure data. ──
│   ├── GameData.kt              The recipe, and the deck builder
│   └── DialogCatalog.kt         The entire script, every line the cat says
│
└── ui/
    ├── state/                   ── SHAPE OF THE STATE ──
    │   ├── GameUiState.kt       The whole game as one immutable value
    │   └── MiniHuntUiState.kt   The board as one immutable value
    │
    ├── viewmodel/               ── THE RULES ──
    │   ├── GameViewModel.kt     Screen transitions, dialog chain, the bowl
    │   └── MiniHuntViewModel.kt Card flipping and matching
    │
    ├── screen/                  ── FULL PAGES ──
    │   ├── MixingPage.kt        Chips + bowl + drag & drop + Mix button
    │   ├── MiniHuntPage.kt      Header + inventory + 4×5 card grid
    │   └── FinalPage.kt         Cake + three Lottie animations + message
    │
    ├── components/              ── REUSABLE PIECES ──
    │   ├── GamePageScaffold.kt  The frame every page sits in. Draws the cat.
    │   ├── CharacterPlaceholder.kt  The cat itself (Coil, crossfading GIFs)
    │   ├── FlipCard.kt          One memory tile, with the 3D flip
    │   ├── IngredientBox.kt     One inventory chip
    │   ├── PixelButton.kt       The pink notched button
    │   ├── DashedBox.kt / DashedDivider.kt   Dashed decorations
    │   └── dialog/
    │       ├── DialogOverlayHost.kt   The scrim + panel + all the animation
    │       ├── DialogFrame.kt         The cream notched panel
    │       ├── NotchedFrameShape.kt   A custom Shape drawing pixel-art corners
    │       ├── DialogStepBody.kt      Renders one step's contents
    │       ├── RecipeExtraBody.kt     The recipe card block
    │       └── PantryExtraBody.kt     The pantry grid block
    │
    └── theme/                   Color.kt, GameColors.kt, Theme.kt, Type.kt
```

**The rule that decides the folder:** can this file be compiled without Compose?

- Yes, and it has no behaviour → `model/` or `data/`
- Yes, and it has behaviour → `ui/viewmodel/` (ViewModels don't import Compose here)
- No → `ui/screen/` if it's a whole page, `ui/components/` if it's a piece

`ui/state/` is the one exception — `GameUiState` imports `@Immutable` from Compose. That's an
annotation, not a dependency on the runtime, and the tests prove it: `GameUiStateTest` runs on
the plain JVM.

---

## Self-check

1. The mixing page appears twice in the flow. What is different about it the second time, and
   where does that difference live?
2. `MiniHuntPage` finishes its game. Trace the call chain that ends with the FINAL screen
   showing. How many objects are involved?
3. Why is `DialogCatalog` in `data/` and not in `ui/`?
4. If you wanted to add a fourth screen, which files would you have to touch?

<details>
<summary><b>Answers</b></summary>

1. The chips are stocked instead of empty. The difference lives entirely in
   `GameUiState.inventory` — the composable is byte-for-byte the same code both times. The
   first visit has `inventory = GameData.emptyInventory` (all zeros); the second has whatever
   `onHuntFinished` banked.

2. `MiniHuntPage` → `onFinished(collected)` → `GameViewModel.onHuntFinished` → sets
   `inventory` and shows `HUNT_WON` → player taps through → `onDialogAdvance` returns null →
   `onSequenceComplete(HUNT_WON)` → `goTo(MIXING)`. Then the player drags and taps Mix →
   `onMixClick` → `goTo(FINAL)`. So: `MiniHuntPage`, `GameViewModel`, `GameUiState`,
   `BirthdayCakeApp`'s `when`. Note the mini-game **does not** go to FINAL — it goes back to
   MIXING. Going straight to FINAL would skip the bowl.

3. Because it's data, not UI. It holds string *resource ids* (`R.string.intro_sleep`), not
   resolved strings — so it needs no `Context` and no `@Composable` scope. It's a script, and
   scripts aren't UI. Test it, print it, translate it, all without Compose.

4. `GameScreen.kt` (add the enum constant), `MainActivity.kt` (add the `when` branch —
   Kotlin will *force* you to, because the `when` is exhaustive), a new file in `ui/screen/`,
   and `GameViewModel.onSequenceComplete` if a dialog should lead into it. That's it. The
   compiler catches two of the four for you.

</details>

---

Next: [02 — State in Compose](02-state-in-compose.md)
