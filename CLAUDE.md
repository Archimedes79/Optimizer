# Portfolio Optimizer Classic

Android app (Java, no Compose) that rebalances a private securities portfolio with
three classical strategies, blended by three sliders. Data comes from Yahoo
Finance's public endpoints and is stored locally in `filesDir/portfolio.json`.
The maintainer writes German — answer in German.

## Where things are

- `app/src/main/java/de/mm/portfoliooptimizerclassic/` — 12 classes, three activities
  (`MainActivity`, `ManageSecuritiesActivity`, `OptimizeActivity`), the maths in
  `PortfolioOptimizer`, the data layer in `Portfolio` / `Security` / `DataConverter`,
  networking in `YahooFinanceService`.
- `tools/generate_icons.py` regenerates the legacy mipmaps from the same geometry as
  `res/drawable/ic_launcher_*.xml`. Change the vectors and the script together, never
  one alone. Needs Pillow.
- `.github/workflows/build.yml` builds a release APK on every push and publishes a
  GitHub release on a `v*` tag. `docs/RELEASING.md` covers the signing secrets.

## Current state

Two commits are ahead of `origin/master` and **not pushed yet**:

1. Rename to Portfolio Optimizer Classic (`com.example.optimizer` →
   `de.mm.portfoliooptimizerclassic`), new icon, CI, proprietary licence.
2. Fixes from a code review: crash and data-loss bugs, several wrong optimiser
   results, doc corrections.

Open, in order: `git push` → rename the repo on GitHub to
`Portfolio_Optimizer_Classic` → `git remote set-url origin …` → `git tag v1.0.0 &&
git push origin v1.0.0`. Nothing has ever been compiled since the rename — the first
CI run is the first real compiler pass. If it fails, `compileSdk { release(36) {
minorApiLevel = 1 } }` needing SDK platform 36.1 is the most likely cause.

## Commands

    ./gradlew testDebugUnitTest      # unit tests
    ./gradlew assembleRelease        # APK in app/build/outputs/apk/release/

Needs JDK 21 and Android SDK platform 36 (minor API level 36.1). minSdk is 24.

## Conventions

- Every user-facing string lives in `res/values/strings.xml` **and**
  `res/values-de/strings.xml`. The two must stay in sync, including the number and
  order of format placeholders, or the build fails.
- The licence is proprietary and source-available. Do not suggest an OSI licence or
  a licence badge. A new dependency must be permissively licensed and must be added
  to `THIRD-PARTY-NOTICES.md`.
- `isMinifyEnabled = false` on purpose: the Gson keep rules in `proguard-rules.pro`
  are written but never verified on a device.
- The working copy is Windows with CRLF. On a Linux side, set
  `git config core.autocrlf true` in the repo first, or every file shows as modified.

## Known, deliberately unfixed

- Rotating `ManageSecuritiesActivity` while editing a position adds a duplicate
  instead of updating it — needs `onSaveInstanceState`.
- `Security` is not fully thread-safe while a sync rewrites its history. The crash is
  gone (indices are clamped); a briefly inconsistent chart is still possible.
- Monthly prices are interpolated to daily, which understates volatility by roughly a
  factor of 30 and skews comparisons against securities with daily data.
