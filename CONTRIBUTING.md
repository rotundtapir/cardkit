# Contributing to cardkit

Thanks for your interest in contributing! `cardkit` is the shared, game-agnostic
foundation for a suite of card-game apps, so changes here affect every game that
depends on it.

## License of contributions

This project is licensed under the **GNU General Public License v3.0 or later,
WITH** the Google Mobile Ads / Play Billing linking exception described in
[`LICENSE-EXCEPTION.md`](LICENSE-EXCEPTION.md).

**Inbound = outbound:** by submitting a contribution you agree that it is
licensed under exactly those terms — GPLv3-or-later **with** the linking
exception. This is what allows contributed code to ship in both the free/libre
builds *and* the ad-supported (Google Play) builds without a separate CLA.

### Developer Certificate of Origin (DCO)

We use the [Developer Certificate of Origin](https://developercertificate.org/)
instead of a CLA. Every commit must be signed off:

```bash
git commit -s -m "your message"
```

This appends a `Signed-off-by: Your Name <you@example.com>` trailer, certifying
that you wrote the code (or have the right to submit it) and agree to the DCO.
Commits without a sign-off will not be merged.

## Code style & module boundaries

- **`cardkit-core`** is a pure Kotlin/JVM module. It **must not** depend on any
  Android API (`android.*`, AndroidX, Compose). This keeps the game engine
  reusable on a server — which the 500 app's online multiplayer now does. CI
  enforces this.
- **`cardkit-core` and `cardkit-ui` must not** reference the Google Mobile Ads
  SDK, Play Billing, Firebase, or any other proprietary dependency. All such
  code lives only in `cardkit-monetization-play`. This is what keeps F-Droid
  builds free of non-free code.
- Add unit tests for engine/logic changes. Keep game logic deterministic
  (seeded RNG) so tests are reproducible.
- Add a SPDX header to new source files:
  `// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception`

## Building

```bash
./gradlew build          # compiles all modules and runs tests
./gradlew :cardkit-core:test
```

Requires JDK 21. Android modules additionally require the Android SDK.

## Git hooks

A pre-commit hook runs `./gradlew qualityCheck lint jvmTest` so CI lint/test failures are caught
locally. Enable it once per clone:

```bash
git config core.hooksPath scripts/hooks
```

It skips doc-only commits and selects a JDK 21 automatically. Bypass in a pinch
with `git commit --no-verify`.
