# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

`cardkit` is a reusable, game-agnostic foundation for a suite of Android card-game
apps. It is **not** an app itself — it ships no `Activity` and no game rules. Each
game app consumes it as a **git submodule** wired in as a Gradle **composite build**
(`includeBuild("cardkit")`), depending on the published coordinate
`io.github.rotundtapir.cardkit:<module>` which Gradle substitutes with the local
source module.

## Build & test

Requires **JDK 21**. The Android Gradle Plugin does not run on JDK 25+; do not use a
newer JDK. JDK auto-download is disabled (`org.gradle.java.installations.auto-download=false`),
so a JDK 21 must be installed locally. Android modules also need the Android SDK
(`compileSdk 36`).

```bash
./gradlew build                    # compile all modules + run tests
./gradlew :cardkit-core:jvmTest    # cardkit-core needs only a JDK (no Android SDK)
./gradlew lint jvmTest             # what the pre-commit hook and CI run

# Run a single test class or method (JUnit 5):
./gradlew :cardkit-core:jvmTest --tests "io.github.rotundtapir.cardkit.core.DeckTest"
./gradlew :cardkit-core:jvmTest --tests "*DeckTest.standard deck has 52 cards"
```

Enable the pre-commit hook (runs `./gradlew lint jvmTest`, skips doc-only commits,
auto-selects a JDK 21) once per clone:

```bash
git config core.hooksPath scripts/hooks
```

Commits must be signed off for DCO (`git commit -s`); unsigned commits are not merged.

## Module architecture

The whole design exists to keep two things quarantined: **Android** away from the
engine, and **proprietary code** away from everything F-Droid ships. Preserve these
boundaries — CI enforces them and they are the project's reason for existing.

| Module | Platform | Role |
| --- | --- | --- |
| `cardkit-core` | KMP: jvm + wasmJs | authoritative game engine — card model, deck DSL, dealing, `GameRules`/`GameDriver`/`Player`. **Must not** touch `android.*`, AndroidX, Compose, or JVM-only APIs (keeps it server-runnable and browser-runnable). Tests in `src/jvmTest`. |
| `cardkit-ui` | KMP/CMP: android + wasmJs | game-agnostic card-rendering Compose Multiplatform primitives (card PNGs as compose resources in `commonMain/composeResources`) + the card-table sound engine (`SoundEffect` + expect/actual `SoundManager`/`rememberSoundManager`: SoundPool + `res/raw` OGGs on Android, Web Audio + wasm-only compose resources in the browser); `api`-depends on `cardkit-core`. |
| `cardkit-monetization` | KMP/CMP: android + wasmJs | the `Monetization` interface (common; **no platform types in signatures** — implementations capture their host at construction) + no-op impls: `FossMonetization` (Android, donation link) and `BrowserMonetization` (wasm). **Zero proprietary deps.** |
| `cardkit-monetization-play` | Android | the *only* module allowed to reference Google Mobile Ads / Play Billing / UMP. Consumed **only** by an app's `play` build flavor; structurally unreachable from wasm. |

`cardkit-core` and `cardkit-ui` must never reference Google Mobile Ads, Play Billing,
Firebase, or any proprietary dependency — that is what lets a game's F-Droid flavor
exclude `cardkit-monetization-play` and contain zero non-free code. The same rule
applies to the `Monetization` interface itself: new members need FOSS-safe defaults
(see `privacyOptionsRequired` / `showPrivacyOptionsForm`) so `FossMonetization` and
consumers never see a GMS type.

## Monetization seams (hard-won invariants)

- **Consent before ads.** `PlayMonetization` gathers UMP/GDPR consent on construction and only
  initialises the Mobile Ads SDK — and loads any ad — once `canRequestAds()` holds. Consent
  failures (offline, misconfiguration) must never block the app: ads just stay off. `Config` has
  debug knobs (`consentDebugGeographyEea`, `consentTestDeviceHashedIds`); note debug geography
  only applies on test devices (emulators qualify automatically, physical phones must be added).
- **`maybeShowInterstitial(onDismissed)`**: the continuation fires when the ad closes
  — or immediately when nothing shows (FOSS, ads removed, none loaded, show failure). Callers
  rely on it to hold game flow (deal animations, bot turns) while an ad owns the screen; every
  implementation must guarantee exactly-once invocation.
- **Sound is silent-context safe.** The Android `SoundManager` actual (cardkit-ui) creates its
  `SoundPool` lazily on the first audible play: at volume 0 no native audio is ever touched
  (instrumentation runs on `-no-audio` emulators crash otherwise), and `rememberSoundManager`
  shares one process-wide instance so activity recreation cannot churn native audio resources.
  The wasm actual also skips playback at volume 0, but decodes its Web Audio buffers eagerly
  at construction — the touch-no-audio-resources constraint is Android-only.

## Engine model (cardkit-core)

The engine is a pure state machine, deliberately transport- and framework-agnostic:

- **`GameRules<State, Action, View>`** — a game implements this: `currentActor`,
  `isTerminal`, `legalActions`, `apply`, and `view(state, seat)`. `view` **redacts
  hidden information** (other seats' hands, the stock) so neither AI nor a remote
  client can cheat. `cardkit-core` provides only the shape; concrete games (e.g. 500)
  live in the consuming apps.
- **`GameDriver`** loops: ask the `Player` at `currentActor` to `decide(view)`, then
  `apply`. Swapping local AI for a networked opponent is purely a change to the
  `players` map.
- **`Player<View, Action>`** is the key seam. `decide` is `suspend` so human/network
  latency needs no engine change. Implementations: `StrategyPlayer` (wraps a
  synchronous AI `Strategy`), `ChannelPlayer` (suspends until a human `submit`s via UI),
  and a networked player for remote seats — realized in the 500 app's online server
  (its own `:net`/`:server` modules; a generic version isn't in cardkit yet).
- **Determinism**: shuffling and AI take a seeded `kotlin.random.Random`
  (`shuffleWith`, `shuffleAndDeal`, `Strategy.decide(view, random)`), so games are
  reproducible and unit-testable. Keep new engine logic deterministic.
- **Cards**: `Card` is a sealed interface (`SuitedCard` | `Joker`) and is intentionally
  **not `Comparable`** — card strength depends on the game (trump, bowers, no-trump).
  Build decks with the `buildDeck { }` DSL, which supports non-standard decks
  (500's 43-card, Euchre's 24-card, etc.).

## Conventions

- Add the SPDX header to every new source file:
  `// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception`
- Version catalog: all dependencies/plugins go through `gradle/libs.versions.toml`
  (`libs.*` aliases). Proprietary libs there are commented as such and belong only to
  `cardkit-monetization-play`.
- `templates/` holds reusable CI/`FUNDING.yml`/`PRIVACY.md`/license files that each
  consuming *app* copies — they are not built by this repo.
