# cardkit

Reusable, game-agnostic foundation for a suite of Android card-game apps.

`cardkit` provides the parts that every card game in the suite shares, so each
game app only has to implement its own rules and screens:

| Module | Platform | Contents |
| --- | --- | --- |
| `cardkit-core` | pure Kotlin/JVM | card & deck model (supports non-standard decks), seeded RNG/shuffle, `Player`/`PlayerView` abstraction, game-loop driver scaffolding, AI `Strategy` interface |
| `cardkit-ui` | Android / Jetpack Compose | card rendering, hand fan, table layout, card animations, theming, generic bidding/score panels |
| `cardkit-monetization` | Android | `Monetization` interface + a **FOSS no-op** implementation (donation link). No proprietary dependencies. |
| `cardkit-monetization-play` | Android | Google Mobile Ads + Google Play Billing implementation. Depended on **only** by an app's `play` build flavor. |
| `templates/` | — | reusable CI workflow, `FUNDING.yml`, `PRIVACY.md`, and license files for each app |

## Design principles

- **`cardkit-core` has no Android dependency.** The authoritative game engine is
  pure Kotlin so it can also run server-side when online multiplayer is added.
- **Proprietary code is quarantined** in `cardkit-monetization-play`. Everything
  else is free of Google Mobile Ads / Play Billing / Firebase, so a game app's
  free/libre (F-Droid) flavor can exclude that one module and contain zero
  non-free code.
- **Deterministic engine.** Shuffling and AI use a seeded RNG so games are
  reproducible and unit-testable.

## Consuming cardkit from a game app

Each game app includes this repository as a **git submodule** and wires it in as
a Gradle **composite build** (built from source — no binary artifacts to
publish, and F-Droid-friendly):

```kotlin
// <app>/settings.gradle.kts
includeBuild("cardkit")
```

Then app modules depend on the published coordinates, which Gradle substitutes
with the local submodule build:

```kotlin
dependencies {
    implementation("io.github.rotundtapir.cardkit:cardkit-core")
}
```

## Building

```bash
./gradlew build            # all modules + tests (JDK 21)
./gradlew :cardkit-core:test
```

`cardkit-core` builds with only a JDK. The Android modules require the Android
SDK (`compileSdk 35`).

## Artwork

The card faces in `cardkit-ui` are Byron Knoll's
[Vector Playing Cards](https://code.google.com/archive/p/vector-playing-cards/)
(released into the **public domain** by the author), bundled as downscaled PNGs
under `cardkit-ui/src/main/res/drawable-nodpi/`.

## License

GPLv3-or-later **with** a Google Mobile Ads / Play Billing linking exception —
see [`LICENSE`](LICENSE) and [`LICENSE-EXCEPTION.md`](LICENSE-EXCEPTION.md). This
lets contributed code ship in both free/libre and ad-supported builds. See
[`CONTRIBUTING.md`](CONTRIBUTING.md) (contributions require a DCO sign-off).

> **Namespace:** the Maven group id / package prefix `io.github.rotundtapir` is
> the `io.github.<org>` namespace for the GitHub org `rotund-tapir`, with the
> hyphen dropped (hyphens aren't valid in Java/Kotlin package names).
