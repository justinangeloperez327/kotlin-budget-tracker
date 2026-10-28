# Kotlin Budget Tracker (Android)

This is the **native Android Kotlin/Jetpack Compose port** of [next-budget-tracker](https://github.com/justinangeloperez327/next-budget-tracker), **not a Ktor website**.

## Current state: Group 10 — production acceptance and signed-release verification

Android Studio project (Kotlin 2.2.21, Android Gradle Plugin 8.13.2, Gradle 8.13, Jetpack Compose BOM 2025.10.01 / Material 3, SDK 36, minimum API 26). Includes a responsive native navigation drawer, Sakura-inspired finance UI, complete monthly budget plan editor, category charts, device-only or web-account expense add/edit/delete, global search/category/month filters, safe CSV document export, character palettes and appearance modes, unit tests, Android instrumentation test sources and GitHub Actions CI.

**Not yet an exact functional copy.** Online authentication and revision-aware synchronization are implemented but require the updated backend to be deployed manually. Bills, debt and savings now have native trackers with history. Remittances, SSS, PhilHealth, Pag-IBIG and MP2 have native screens. Financial Overview, Reports, History and in-app Reminders are now implemented; character artwork and offline write queuing remain. Group 7 adds an actual headless Android emulator instrumentation CI job, Compose UI tests and usability fixes; see its CI status before considering device UI verified. The app labels incomplete sections rather than fabricating records. [See the complete parity checklist](docs/PARITY_CHECKLIST.md).

## Run in Android Studio

1. Install Android Studio, JDK 17 and Android SDK platform 36.
2. Clone and open the repository root as a Gradle project.
3. Sync and run the `app` configuration on a physical Android device or emulator.
4. Alternatively, using installed Gradle 8.13: `gradle :app:assembleDebug :app:testDebugUnitTest`.

Gradle 8.13 Wrapper is checked in and its distribution checksum is pinned. Use `./gradlew` or Windows `gradlew.bat`; installation of Gradle separately is no longer required.

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


## Group 3 — Budget and expense parity

- Monthly budget plan saves the total and all category allocations **atomically** (SQLite transaction in device-only mode, one revision-checked full-workspace PUT in web-account mode). Existing server data for bills, debts, remittances, savings and government modules are preserved.
- Category budget vs actual: actual, variance, unallocated, percent used, category under-budget and category overspent, plus native category spending bars. An over-allocation yields negative unallocated, just like the source application.
- Expenses: add, edit while preserving transaction ID, delete after confirmation, case-insensitive search, category and optional month filter **across all available months**, and count/total for the filtered results. Managed linked bill/debt/remittance expenses are read-only in the expense editor.
- CSV export via Android's system document picker. Exports only filtered results, includes an UTF-8 BOM and escapes quotes, commas and spreadsheet formula prefixes. Android needs no external storage permission.
- Regression tests port selected source budget-calculation fixtures. Android database instrumentation tests use their own isolated test database; CI compiles them but does not run emulator tests yet.
- No automatic local-to-account migration, no offline write queue, and no automatic deployment to Vercel or Google Play. Online save conflicts and uncertain outcomes still require manual refresh.

## Group 4 — Bills, Debts and Savings

- Native Bills, Debt / Utang and Savings screens: create, edit, activate/deactivate, browse records and history; confirm before deleting a record or undoing a movement.
- Recurring bills: monthly, quarterly or yearly, configurable due day (clamped to month end), start/end period, category, notes, period selection, due/paid status, one payment per bill-period.
- Each paid bill generates **one linked AED expense** with the same amount, date and category. Undoing a payment removes that linked expense. Renaming or recategorizing a bill updates its payment expenses.
- Debts: lender, original balance, due date, optional monthly target, active state, remaining balance, payment progress, repayments constrained to remaining principal. Repayments create one linked AED expense; debt principal itself is not an expense.
- Savings goals: target date, destination/wallet, optional monthly target, saved and remaining balance, deposit history and progress. Savings deposits **never** create consumption expenses.
- Records with payment/deposit history cannot be deleted; deactivate/pause the record instead. Payment and deposit records can be undone with confirmation.
- Offline: SQLite schema v2 non-destructively migrates the original v1 database. Tracker state and its linked expense changes commit/rollback in the SAME transaction. Existing budgets and manual expenses remain intact.
- Web-account: the original source-shaped records live in the full canonical workspace; every change uses the shared validated, optimistic-revision API from Group 2. No automatic merge of device-only and online data.
- Tests cover source calculation policies, recurrence/date clamping, unique payment periods, repayments, savings transfers, linked expense metadata, and isolated SQLite transactions. CI builds debug APK and tests; instrumentation runs only when explicitly executed with an emulator.

No automatic deployment, Play Store publication or pull requests.

## Group 5 — Remittances, Philippine contributions and MP2

- Remittances: AED sent and fee; PHP received; status Pending/Completed; recipient, provider, destination, effective rate and reference. Edit/delete reconciles the matching AED expense in the same operation. Own-account principal is excluded from expenses when the principal toggle is off, while the fee counts as an AED expense.
- SSS, PhilHealth, Pag-IBIG: separate member-type profiles, account identifiers, frequency, monthly target, paid/pending/missed/not-required periods and year-by-year histories. Missing periods are shown as "No record", not automatically missed.
- MP2: several accounts, account number, dividend option, deposit history, monthly target and estimated five-year maturity from first payment; deposits are PHP savings rather than AED expenses.
- Private SQLite preserves Group 4 data, while remittance expenses and source data commit in one transaction. Web-account edits use the existing revision-checked authenticated workspace API.
- JVM regression tests and Android database instrumentation test sources cover money separation, expense links, PHP contributions, MP2 maturity and cross-module preservation. CI compiles instrumentation tests but does not run an emulator.

Financial records entered in this app are not official confirmation of government contributions or bank transactions. MP2 maturity is an estimated date, not a guaranteed dividend/withdrawal event. No automated Play Store or Vercel deployment is enabled.

## Group 6 — Financial Overview, Reports, History and Reminders

- Financial overview separates AED budget, bills, debt, savings, remittances and fees from PHP government contributions, MP2 and received remittances. All period-sensitive figures use **as-of date = earlier of today or the selected month end**; future-dated payments are excluded from completed totals.
- Monthly reports include budget versus actual, under/over variance, category spending, paid bills, repaid debt, AED savings transfers, completed/pending remittances, fees, family support and own-account transfers. PHP rows include paid contributions, MP2 deposits and received remittances.
- Yearly history: only elapsed months through today, with AED budget/spending/variance, gross unspent and overspent months, category rollups and native month-by-month trend bars. Money is represented as integer minor units and currencies are never combined.
- Export monthly and yearly CSV using Android's system document picker, without storage permissions. CSV headers explicitly identify AED or PHP; results contain UTF-8 BOM, fixed two-decimal amounts and CRLF rows.
- In-app Reminders: outstanding bills, debt due dates, explicitly missed or pending SSS/PhilHealth/Pag-IBIG contributions, expected-period gaps (not automatically marked missed), savings/MP2 targets and estimated MP2 maturity, pending remittances. Reminders have severity filters and links to the relevant native screen. They do not schedule OS notifications or issue financial transactions.
- Device-only reporting reads complete historical SQLite budgets, allocations and expenses plus tracker records in memory. No schema migration or write is needed. Web-account reporting uses the authenticated revision-safe workspace snapshot without modification.
- Kotlin unit regression tests exercise cutoffs, source-shaped data, double-count protection, AED/PHP separation, CSV exports, reminder priority and category/year performance. Android instrumentation tests cover local historical SQLite projections, but CI only **compiles**, not executes, emulator instrumentation.

Missing or incomplete: device/emulator UI validation, notification scheduling, more advanced customizable visualizations, offline queue/automatic local-account merge, complete character artwork and full end-to-end parity audit. The original web account API must be deployed manually before remote login works.

## Group 7 — Navigation and usability hardening

- Explicit, bounded Android navigation history; Back returns to the previous visited section and closes an open drawer first. Main navigation history survives Activity recreation.
- Stable Compose semantic identifiers for screens/navigation, with heading semantics. Expense form validation is inline (positive AED amounts, valid dates, descriptions); budget editor disables Save until all currency fields are valid.
- Original theme names preserved with contrast-safe light/dark variants. Primary action text colors are explicitly chosen for readability; no theme changes financial records.
- Dashboard totals now use the same time cutoff as Financial Overview and reports, excluding future-dated movements.
- GitHub Actions retains standard compile/unit/lint/APK job and adds a KVM-backed API 35 Android emulator (ReactiveCircus Runner v2, Node.js 24) running Compose UI and SQLite instrumentation test suites. Superseded main-branch runs are cancelled.
- Tests cover drawer/back navigation, form validation, theme selection, screen navigation, expense entry, and Activity recreation, in addition to SQLite financial invariants.
- User data is never uploaded automatically. No PR, Google Play upload or Vercel auto-deployment.

Detailed test plan: [Group 7 Android hardening](docs/GROUP7_ANDROID_HARDENING.md). **Passing compiled instrumentation tests is not the same as running them**; verify the emulator CI job has succeeded before asserting tested device behavior.

## Group 8 — Integrity and device-only backup

The **Data integrity & backup** screen checks source-shaped financial relationships, payment ownership, UUIDs, contribution periods and amount limits. In device-only mode, export a versioned JSON backup through Android's document picker; restore only after validation and explicit replacement confirmation. The restore transaction covers budgets, expenses and all financial trackers; it does not merge or upload local data to the web account. **Exported backup JSON is not encrypted.** Store it securely. The SHA-256 checksum detects accidental corruption, not malicious edits.

[Group 8 source-parity and recovery audit](docs/GROUP8_PARITY_AUDIT.md) documents implemented features and remaining gaps. This is not yet a fully source-identical or production-signed app.

## Group 9 — Release candidate build and privacy hardening

The repository now builds Android release-candidate `0.9.0-rc.1` (`versionCode 9`) with R8 minification and resource shrinking. The pinned Gradle 8.13 Wrapper supports Windows/Linux. Android auto-backup/cloud/device-to-device exclusions protect the private finance database and Keystore-backed credentials. CI verifies an **unsigned** release APK/AAB and publishes only audit checksums; a **manual** GitHub Actions workflow may package unsigned candidates, without deploying anywhere.

Production APK/AAB signing is opt-in and reads **four required** `BT_RELEASE_*` environment variables from the user's machine; no secrets are stored in GitHub. See [Group 9 release checklist](docs/GROUP9_RELEASE_CHECKLIST.md). No Play Store upload or automatic deployment has been configured.

## Group 10 — Signed release preparation and production acceptance

CI now includes API 35 and API 36 emulator checks, a disposable-key signing smoke test, offline release-security/upgrade tests, and a SQLite v1-to-v2 upgrade acceptance test. The CI-only key is not suitable for distribution and neither signed artifacts nor keys are uploaded.

Manual release scripts verify that a user-signed APK/AAB matches the chosen upload key and that an in-place APK upgrade preserves the signing identity and increases versionCode before touching a device. Actual installation requires explicit confirmation. Android 16 edge-to-edge handling and accessible Back navigation have been hardened.

**A real release key, staging backend and physical devices were not provided. No production signing or Play Store publication was performed.** See [Group 10 production checklist](docs/GROUP10_PRODUCTION_ACCEPTANCE.md) and [manual release approval template](docs/RELEASE_APPROVAL_TEMPLATE.md).


### Group 10 continuation — mobile API safety checks

Added in-process HTTPS/mobile API contract tests, Android Keystore tamper tests, and fail-closed remote-save recovery. An uncertain save always blocks another remote edit until authoritative refresh, including malformed HTTP 200 acknowledgments. For a **manually deployed staging test environment only**, [run the opt-in read-only workspace probe](docs/GROUP10_PRODUCTION_ACCEPTANCE.md#group-10-continuation--transport-and-staging-acceptance). It never modifies remote data or automatically performs a production test.
