<!-- Thanks for contributing to cardkit! Please fill in each section — it keeps reviews fast. -->

## What & why

<!-- What does this PR change, and what problem does it solve? Link any related issue. -->

## How

<!-- Anything a reviewer needs to know about the approach: design choices, trade-offs, alternatives rejected. -->

## Testing

<!-- Which modules did you build/test, and what did you add? Paste relevant output if a suite can't run in your environment and say so explicitly. -->

**All pull requests must include tests for any added feature.** Bug fixes should include a regression test that fails without the fix.

## Checklist

- [ ] Tests are included for every added feature (and a regression test for every bug fix)
- [ ] `./gradlew qualityCheck lint jvmTest` passes locally (the pre-commit hook runs this too — detekt + CPD duplication + lint + tests)
- [ ] Module boundaries are intact: no proprietary dependency (GMS/Billing/UMP) reaches `cardkit-core`, `cardkit-ui`, or `cardkit-monetization` — it stays quarantined in `cardkit-monetization-play`
- [ ] New source files carry the SPDX header (`GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception`)
- [ ] Commits are signed off (`git commit -s`, DCO)
