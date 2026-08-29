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

Released. `master` and the tag `v1.0.0` are pushed, the repo is renamed, and CI has
run green three times — twice on `master`, once on the tag — so the code compiles
and the 18 unit tests pass. The feared `compileSdk { release(36) { minorApiLevel =
1 } }` problem did not materialise: the workflow's fallback to plain
`platforms;android-36` is what actually carries the build.

One thing is open: the four `RELEASE_*` secrets do not exist yet, so the published
APK is **debug-signed** (`PortfolioOptimizerClassic-v1.0.0-debugsigned.apk`). It
installs, but no later signed build can update it. Add the secrets as
`docs/RELEASING.md` describes and cut a new tag; the asset is then named
`…-signed.apk`.

## Commands

    ./gradlew testDebugUnitTest      # unit tests
    ./gradlew assembleRelease        # APK in app/build/outputs/apk/release/

Needs JDK 21 and Android SDK platform 36 (minor API level 36.1). minSdk is 24.

## Conventions

- Every user-facing string lives in `res/values/strings.xml` **and**
  `res/values-de/strings.xml`. The two must stay in sync, including the number and
  order of format placeholders, or the build fails.
- `README.md` is English and `README.de.md` is German; they are translations of one
  another. Change one and translate the change into the other in the same commit.
  Nothing checks this, so it only holds if you do it.
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
