# Group 7 — Android UI, accessibility and parity verification

## Scope and acceptance

Group 7 hardens the native Kotlin / Jetpack Compose application rather than inventing new financial functionality.

1. Navigation must retain an explicit, bounded history, survive activity recreation, reject unknown routes, and navigate back from Reminders to the originating screen. The Android system Back button closes an open drawer before returning to the previous screen.
2. User inputs must reject invalid expenses and budget amounts before the Save button is enabled. Invalid expense dates and amounts show contextual error text. Draft expense form fields use saveable Compose state to survive configuration changes.
3. Every page has a stable Compose semantics tag and an accessible section title. The navigation drawer, theme/account buttons and editing form controls have selectors suitable for UI testing. Accessibility remains compatible with large fonts and TalkBack review; fully automated accessibility auditing is not yet present.
4. Each theme has an independently selected light/dark primary color. Primary text has at least 4.5:1 contrast against the main light or dark surface; light primary buttons use white text, dark primary buttons use black text. Color is not the only status indicator.
5. Dashboard financial figures must follow the selected month's reporting cutoff. Future-dated expenses, MP2 deposits, remittances and contributions must not enter completed activity early. Unspent budget is never a savings deposit.
6. Device-only and authenticated web-account records stay separate. Linked expenses are edited only through their owning tracker. All account writes retain the existing revision and conflict protections.

## Automated tests

- `BudgetNavigationTest`: stack safety, duplicate routes, history cap, reminder-to-tracker back behavior.
- `BudgetUiTest` (Android emulator): drawer navigation, Back, account-independent forms, report and reminder navigation, theme controls, state restoration after rotation/recreation, and a real device-only expense entry.
- Existing `FinanceDbTest`, `TrackerDbTest`, `PhilippineDbTest`, `FinancialReportsDbTest`, `TrackerMigrationTest` (Android emulator) cover local ledger links, non-destructive migration, remittance atomicity and historical reporting.
- Source-shaped JVM tests continue to run on every push.

GitHub Actions contains **two jobs**: `android` for Gradle, unit tests, lint, APK output and instrumentation **compilation**; `android-ui` for **execution** of `connectedDebugAndroidTest` in an API 35 x86_64 headless Android emulator on an Ubuntu KVM runner. Emulator tests do not connect to the Next.js backend or real financial accounts.

The UI job uses the maintained `reactivecircus/android-emulator-runner@v2` action, verified to declare `using: 'node24'` in its action metadata, and a hardware-accelerated API 35 emulator. It does not rely on deprecated Node.js 20 actions. Emulator artifacts are uploaded for inspection, and superseded branch runs are cancelled to reduce duplicate execution.

## Running locally (Windows / Android Studio)

Install Android Studio with JDK 17, Android SDK 36 and a Google APIs Android 35 emulator. Use Gradle 8.13:

```powershell
gradle :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
gradle :app:assembleDebugAndroidTest
gradle :app:connectedDebugAndroidTest
```

Use a fresh emulator for isolation. Device-only financial records are private app data; clearing application storage or uninstalling deletes them. Do not use an emulator configured with production accounts for test execution.

## Remaining manual acceptance

- Verify TalkBack traversal, large font scaling (1.3–2.0x), RTL layout, keyboard focus and touch targets on physical devices.
- Verify device rotation while each long form is dirty; only expense drafts have saveable state as part of Group 7.
- Verify OS process-death and restore separately from activity recreation.
- Verify web login, token expiry, concurrent-device revision conflicts and user-authorized device data import with a manually deployed mobile API and test accounts.
- Complete companion/character illustrations and fine-grained visual parity with the Next.js source.
- Evaluate background notifications separately. Group 6 Reminders are in-app, not scheduled alarms.
