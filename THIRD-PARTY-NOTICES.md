# Third-party notices

Portfolio Optimizer Classic bundles the open-source components listed below.
Each remains under its own licence; those licences govern those components.

## Apache License 2.0

The full licence text is available at
<https://www.apache.org/licenses/LICENSE-2.0>.

| Component | Version | Copyright |
| --- | --- | --- |
| [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart) | 3.1.0 (declared in the version catalogue as the JitPack tag `v3.1.0`) | Copyright 2020 Philipp Jahoda |
| [Apache Commons Math](https://commons.apache.org/proper/commons-math/) | 3.6.1 | Copyright 2001-2016 The Apache Software Foundation |
| [Gson](https://github.com/google/gson) | 2.11.0 | Copyright 2008 Google Inc. |
| [AndroidX](https://developer.android.com/jetpack/androidx) (AppCompat, Activity, ConstraintLayout, Core, RecyclerView) | see `gradle/libs.versions.toml` | Copyright The Android Open Source Project |
| [Material Components for Android](https://github.com/material-components/material-components-android) | 1.13.0 | Copyright Google LLC |

Apache Commons Math is a product of the Apache Software Foundation
(<https://www.apache.org/>) and ships its own NOTICE file, reproduced here:

> Apache Commons Math
> Copyright 2001-2016 The Apache Software Foundation
>
> This product includes software developed at
> The Apache Software Foundation (http://www.apache.org/).

## Test-only dependencies

Not shipped in the application package.

| Component | Version | Licence |
| --- | --- | --- |
| [JUnit 4](https://junit.org/junit4/) | 4.13.2 | Eclipse Public License 1.0 |
| [AndroidX Test / Espresso](https://developer.android.com/training/testing) | see `gradle/libs.versions.toml` | Apache License 2.0 |

## Market data

Price and search data comes from Yahoo Finance's public endpoints. That data is
not part of this project, is not redistributed with it, and is subject to
Yahoo's terms of service. It is used here for private, non-commercial purposes,
is provided without any guarantee of accuracy, timeliness or availability, and
may become unavailable at any time.
