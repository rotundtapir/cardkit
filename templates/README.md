# cardkit templates

Drop-in files for a new game app in the suite. Copy them into the app repo and
replace the placeholders (package name, app name, donation accounts, ad unit /
product ids).

| Template | Copy to | Purpose |
| --- | --- | --- |
| `app-ci.yml` | `<app>/.github/workflows/ci.yml` | Build both flavors, run tests + lint, publish release artifacts on tag. Checks out the `cardkit` submodule recursively. |
| `FUNDING.yml` | `<app>/.github/FUNDING.yml` | Donation links; GitHub renders a "Sponsor" button and F-Droid parses it into `Donate:`/`Liberapay:` metadata. |
| `PRIVACY.md` | `<app>/PRIVACY.md` (and host it publicly) | Privacy policy. **Required by Google Play** for the ad-supported (`play`) build. |

Reusable legal files live at the repository root of `cardkit` and should be
copied verbatim into each app so its license matches:

- `LICENSE` (GPLv3)
- `LICENSE-EXCEPTION.md` (Google Mobile Ads / Play Billing linking exception)
- `CONTRIBUTING.md` (DCO)
