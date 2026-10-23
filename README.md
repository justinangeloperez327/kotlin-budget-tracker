# Kotlin Budget Tracker (Android)

This is the **native Android Kotlin/Jetpack Compose port** of [next-budget-tracker](https://github.com/justinangeloperez327/next-budget-tracker), **not a Ktor website**.

## Current state: first Android migration milestone

Android Studio project (Kotlin 2.2.21, Android Gradle Plugin 8.13.2, Gradle 8.13, Jetpack Compose/Material 3, SDK 36, minimum API 26). Includes a responsive native navigation drawer, Sakura-inspired finance UI, local monthly budgets and category allocations, offline expense entry/list/delete, exact AED calculations, character-theme palettes and appearance modes, basic unit tests and GitHub Actions CI.

**Not yet an exact functional copy.** Authentication, sync to Next.js PostgreSQL, bills, debt, savings, remittances, SSS, PhilHealth, Pag-IBIG, MP2, financial reporting, reminders, CSV and companion artwork are not yet implemented. The app labels incomplete sections rather than fabricating records. [See the complete parity checklist](docs/PARITY_CHECKLIST.md).

## Run in Android Studio

1. Install Android Studio, JDK 17 and Android SDK platform 36.
2. Clone and open the repository root as a Gradle project.
3. Sync and run the `app` configuration on a physical Android device or emulator.
4. Alternatively, using installed Gradle 8.13: `gradle :app:assembleDebug :app:testDebugUnitTest`.

An official Gradle Wrapper is not yet included; use installed Gradle 8.13 until wrapper files are added. CI installs this exact version.

## Finance/privacy rules

Money is integer fils or centavos, not floating-point; AED and PHP never combine. An unpaid bill does not count as a transaction, savings/MP2 deposits are not consumption, and linked payment expenses will be unique. Android backup is disabled. For this foundation, locally entered data is stored in the app's private SQLite database: it is **not** automatically linked to the original Next.js account or synchronized between devices. Do not uninstall or clear app storage expecting recovery. Never embed PostgreSQL credentials inside an APK.

GitHub Actions builds and tests only. No publishing or automatic deployment.
