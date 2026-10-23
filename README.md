# Kotlin Budget Tracker (Android)

This is the **native Android Kotlin/Jetpack Compose port** of [next-budget-tracker](https://github.com/justinangeloperez327/next-budget-tracker), **not a Ktor website**.

## Current state: Group 2 — native Android and secure mobile account foundation

Android Studio project (Kotlin 2.2.21, Android Gradle Plugin 8.13.2, Gradle 8.13, Jetpack Compose BOM 2025.10.01 / Material 3, SDK 36, minimum API 26). Includes a responsive native navigation drawer, Sakura-inspired finance UI, local monthly budgets and category allocations, offline expense entry/list/delete, exact AED calculations, character-theme palettes and appearance modes, basic unit tests and GitHub Actions CI.

**Not yet an exact functional copy.** Online authentication and revision-aware synchronization are implemented but require the updated backend to be deployed manually. Bills, debt, savings, remittances, SSS, PhilHealth, Pag-IBIG, MP2, financial reporting, reminders, CSV and companion artwork are not yet implemented. The app labels incomplete sections rather than fabricating records. [See the complete parity checklist](docs/PARITY_CHECKLIST.md).

## Run in Android Studio

1. Install Android Studio, JDK 17 and Android SDK platform 36.
2. Clone and open the repository root as a Gradle project.
3. Sync and run the `app` configuration on a physical Android device or emulator.
4. Alternatively, using installed Gradle 8.13: `gradle :app:assembleDebug :app:testDebugUnitTest`.

An official Gradle Wrapper is not yet included; use installed Gradle 8.13 until wrapper files are added. CI installs this exact version.

## Finance/privacy rules

Money is integer fils or centavos, not floating-point; AED and PHP never combine. An unpaid bill does not count as a transaction, savings/MP2 deposits are not consumption, and linked payment expenses will be unique. Android backup is disabled. Device-only records are stored in private SQLite and are **not** automatically imported into web accounts. Once signed in to a deployed mobile API, web-account records are read and saved directly against the existing Next.js backend using revision checks. Do not uninstall or clear app storage expecting recovery. Never embed PostgreSQL credentials inside an APK.

GitHub Actions builds and tests only. No publishing or automatic deployment.

## Group 2 — Web account authentication and synchronization

The app supports two intentionally separate modes. **Device-only mode** keeps local budgets, allocations, and expenses in private SQLite; they are never uploaded or overwritten automatically. **Web-account mode** uses the Next.js PostgreSQL workspace through a new HTTPS mobile API, with the same email/password or account registration.

Tap **Sign in**, provide the HTTPS origin of an updated next-budget-tracker deployment, and authenticate. The Android client downloads the full authenticated workspace. Its initial screens can change budgets, category allocations and manual expenses; unported tracker records are preserved unchanged in the JSON payload on every revision-checked save.

The server requires a dedicated, revocable mobile bearer token separate from web browser cookies. Android encrypts the token using Android Keystore AES-GCM; no plaintext HTTP, direct database connections or saved account passwords are permitted.

**Manual backend deployment is required:** the Next.js source must be deployed with its new mobile routes after the Prisma migration named 20261023150000_mobile_session has been applied. Merely building this APK does not activate mobile endpoints on an older web deployment.

Mobile requests use POST /api/mobile/auth/login, POST /api/mobile/auth/register, POST /api/mobile/auth/logout, and GET/PUT /api/mobile/workspace.

Conflicting revisions (HTTP 409) or uncertain save outcomes block further editing until an explicit **Refresh**. No blind retry, merging, offline queue, or automatic device-only-record import is performed. Signing out returns to the untouched device-only records. An unconfirmed remote token revocation may leave its bearer credential active until its seven-day expiration.

Android CI keeps a debug APK as a seven-day workflow artifact for manual installation. There is no automatic Play Store publishing or production deployment.
