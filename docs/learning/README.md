# Birthday Cake App — Learning Module

A guided tour of this codebase.

You know Compose basics — `@Composable`, `Column`/`Row`, `remember`. This module assumes that
and goes deep on the parts that are actually load-bearing here: **state**, **data-driven UI**,
and **animation timing**.

## How to use this

Read in order. Each module builds on the last. Every module has:

- **What you'll learn** — the concepts, named
- **The walkthrough** — the actual code, with file references you can click
- **Why it's built this way** — the decision and its alternatives
- **Self-check** — questions to answer without looking, plus answers at the bottom

Have the code open next to this. The file references are real (`app/src/main/...:42`).

Don't just read the answers. Try to answer first, even badly. The gap between your guess and
the answer is where the learning happens.

## The modules

| # | Module | What it covers |
|---|--------|----------------|
| 01 | [The Big Picture](01-the-big-picture.md) | What the app does, the player's journey, the architecture in one diagram, the file map |
| 02 | [State in Compose](02-state-in-compose.md) | **Start here if you read only one.** Recomposition, `remember` vs `ViewModel`, `StateFlow`, stored vs derived state, `@Immutable`, unidirectional data flow |
| 03 | [The Game Flow](03-the-game-flow.md) | `GameViewModel`, `GameUiState`, the checkpoint table, how screens switch without a nav library |
| 04 | [The Dialog Data Model](04-the-dialog-data-model.md) | Dialog as **data**: `DialogText` → `DialogStep` → `DialogSequence` → `ActiveDialog`, the no-lambdas rule, sealed `DialogExtra` |
| 05 | [The Dialog Overlay](05-the-dialog-overlay.md) | Rendering and animating it: `AnimatedContent`, `contentKey`, `SizeTransform`, gesture swallowing, why not a real `Dialog` |
| 06 | [The Memory Game](06-the-memory-game.md) | `MiniHuntViewModel`, deck building, match logic, the cancellable flip-back job, the 3D card flip |
| 07 | [Drag and Drop Mixing](07-drag-and-drop-mixing.md) | Compose drag & drop, `rememberUpdatedState`, the drag-decoration trap, modifier order and hit targets |
| 08 | [Layout, Theme and Art](08-layout-theme-and-art.md) | `GamePageScaffold`, the single-cat decision, edge-to-edge insets, weights vs fixed sizes, custom `Shape`, Coil GIFs, Lottie |
| 09 | [Testing](09-testing.md) | What's tested, why it's testable at all, seeded randomness, what's missing |
| 10 | [Portfolio Talking Points](10-portfolio-talking-points.md) | Interview questions with answers, honest known gaps, roadmap ideas |

## The one-sentence summary

> A pixel-art birthday card disguised as a three-screen game, built entirely in Jetpack Compose,
> where the entire script is plain data and all game logic lives in two ViewModels that a JVM
> unit test can drive without an emulator.

If you can explain *why* each half of that sentence is true, you understand this project.
