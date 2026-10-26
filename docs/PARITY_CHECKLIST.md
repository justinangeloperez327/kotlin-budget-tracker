# Source parity: next-budget-tracker → native Android

Target reference: https://github.com/justinangeloperez327/next-budget-tracker (main).

The goal is a feature-complete Kotlin/Jetpack Compose Android adaptation preserving business logic, accounts, data, charts, forms, themes, and reports. Desktop browser layouts will become idiomatic touch/mobile layouts; exact CSS pixels are not a sensible Android requirement.

| Feature from Next.js | Android migration state |
| --- | --- |
| Login/register/logout/account/session | Implemented via HTTPS mobile API, Android Keystore token storage, manual backend deployment needed |
| Dashboard summary | Groups 3–7: cutoff-aware selected-month AED/PHP financial overview, bills, debt and savings |
| Financial overview separated AED/PHP | Group 6: period cutoff-aware AED commitments and PHP government/MP2/remittance summaries; balances and attention |
| Monthly and category budgets | Group 3: whole-plan atomic save, monthly report, category variance charts in device-only and web-account modes |
| Expense CRUD, filters, CSV | Group 3: add/edit/delete, cross-month search and filters, filtered CSV export, managed linked-expense protection |
| Bills recurring and linked payment expense | Group 4: native recurring bill create/edit, schedule, payment/history/undo and atomic linked AED expenses |
| Debt and repayment expense | Group 4: native debt tracker, progress, repayment history and linked expenses |
| Savings goals and deposit history | Group 4: native goal management, target progress, deposit history; transfers excluded from expenses |
| Remittances AED sent, fee, PHP received, rate | Group 5: native AED/PHP remittances, fees, effective rates, linked expenses and history |
| SSS/PhilHealth/Pag-IBIG and history | Group 5: provider accounts, statuses, year history and explicit missed records |
| MP2 savings, maturity, history | Group 5: multiple PHP accounts, deposits, maturity and history |
| Reports, CSV, historical analysis | Group 6: monthly/yearly report pages, category history and budget trend bars, system document-picker CSV export |
| In-app reminders | Group 6: derived bills/debt/savings/contribution/MP2/remittance reminders with severity and tracker navigation; no OS notifications |
| Sakura, Kuromi, Keroppi, Vegeta, Naruto, Kakashi, Sasuke, Ichigo | Native palette selector; illustrations pending |
| Light/Dark/System | Native toggle |
| Workspace save revisions and recovery | Server 409 conflict detection + explicit refresh after uncertain saves; offline queued writes not implemented |
| Secure backend/data sync with existing web account | Explicit login/registration, mobile GET/PUT, revision checks, separate local storage; automatic local import and background/offline sync not implemented |

Source invariants: two currency lanes, integer minor-unit accounting, a scheduled bill is not yet an expense, exactly one linked expense per bill/debt payment, savings and MP2 are transfers rather than expenses, remittance fee is expense while own-account principal is transfer, no record is not automatically missed government contribution, ownership/validation/revision checks on all saves, no fake seeded transactions.

**Two separate modes:** Device-only records remain in SQLite. Web-account mode authenticates through dedicated secure mobile API tokens and stores records in the existing server PostgreSQL workspace. Switching modes does not import or merge local records. Android never connects directly to PostgreSQL.

Port order: (1) native Android foundation + local budgets/expenses, (2) secure mobile auth/API and safely reconciled web data, (3) complete budgets/expenses, (4) bills/debts/savings, (5) remittances/government/MP2, (6) reports/history/reminders, all artwork and release testing. Financial fixtures from the original TypeScript tests must be used for calculation parity.

No automatic deployment, Play Store publishing or destructive migration.

## Group 2 shipped: secure mobile account pathway

- Browser and Android share owner-scoped canonical PostgreSQL workspace logic; web-cookie endpoints retain same-origin checks.
- Android uses dedicated, independently revocable, SHA-256-hashed mobile bearer tokens, encrypted with Android Keystore AES-GCM locally, with HTTPS-only networking and no automatic redirects.
- Server exposes login/register/logout and revision-checked workspace GET/PUT under /api/mobile. The Next.js mobile-session migration must be applied before manual deployment.
- The mobile client keeps a complete untouched copy of server-side unported module arrays when editing supported budgets, allocations and manual expenses, avoiding partial saves that would delete SSS/MP2/bills/remittances.
- A server save with HTTP 409 or uncertain delivery blocks further online edits until the user explicitly refreshes. Device-only SQLite data stays separate and cannot be automatically merged into the web account yet.
- Unit tests verify HTTPS origin restrictions and preservation of linked expenses/unported modules; backend integration tests exercise web/mobile write visibility, session revocation and stale revisions.
- Remaining parity: native UI/CRUD for every other financial module, secure local import, offline queue, reports, reminders, character artwork and running Android UI instrumentation on emulators.


## Group 3 delivered: budget and expense feature parity

- Atomic complete monthly category plan in SQLite or one revision-checked web server PUT. Permits over-allocation with negative unallocated variance, matching the Next.js source.
- Monthly overall and category variance calculations, rate and bar visualizations, with the distinction between unspent budget and actual savings.
- Full device/account expense ledger, text and category/month filtering across history, edit/delete manual entries, preserved expense UUID, managed payment write protection.
- Android document picker CSV export of filtered rows, with BOM, RFC-like quotes and formula-prefix neutralization.
- Kotlin tests based on source TypeScript fixtures, in addition to existing backend integration tests. SQLite Android instrumentation tests are compiled by CI; emulator execution is a later hardening task.
- Future work: full financial overview, deep reports, bills, debts, savings, remittances, contributions, MP2, reminders, complete themes, reliable offline/online import and sync.


## Group 4 implementation

- Native screens for all three tracker types with details, status, active/pause, histories, edit, payment/deposit, undo and guarded delete.
- Source-compatible mutation and validation in `TrackerEngine` for bill schedules, debt remaining balances and savings targets; editing a paid bill or debt keeps linked expense metadata in sync.
- Savings deposits do not create expenses; bill and debt payments do. A payment and its expense insert/delete are atomic in SQLite and submitted as one workspace PUT when connected to the server.
- Non-destructive private SQLite database upgrade from v1 to v2. No local data reset and no automatic merge to PostgreSQL.
- Java/Kotlin unit tests port source invariants, and compiled Android instrumentation tests cover transaction rollback and private storage.
- Outstanding parity: remittances, SSS, PhilHealth, Pag-IBIG, MP2, deep reports, reminders, complete artwork, and verified device UI testing.

## Group 5 — Implemented modules

- Remittances: AED principal and fee, PHP received, pending/completed status, reference, effective PHP/AED rate, own-account transfer versus support-expense handling, create/edit/delete and transaction history.
- SSS/PhilHealth/Pag-IBIG: provider-specific member types, profiles, monthly/quarterly settings, explicit period statuses, twelve-month history and YTD PHP totals; no entry remains "No record".
- MP2: multiple accounts, dividend option, monthly PHP savings target, deposits, estimated five-year maturity and year history.
- Device mode persists full Group 4 and Group 5 module arrays in the private SQLite tracker-state row; expense-linked remittance writes are atomic. Web mode preserves authenticated revision-checked whole-workspace saves.
- Remaining: consolidated overview, deep historical reports and exports, reminders, complete companion artwork, automated migration, offline write queue and executed emulator UI tests.

## Group 6 — Financial Overview, Reports and Reminders

- Reports are read-only, derived from the canonical workspace or the full local SQLite projection. No automatic API saves or transfers result from reading reports. All dated transactions are bounded by the earlier of today and the selected month's last day.
- AED and PHP metrics remain in separate lanes. A transfer to an own account is not treated as consumption; bills/debt payment expense links are already counted in `expenses` and are not added to spending twice. Unspent budget is not a bank deposit.
- Year history only considers elapsed months, shows under/over budget by category and month, and supports monthly/yearly CSV exports through Android's Storage Access Framework without external-storage permissions.
- In-app reminders derive due and pending statuses from existing records, include links to the corresponding trackers, and never silently mark an unrecorded contribution as missed. They do not generate OS alarms or recurring background notifications.
- Test fixtures cover historical and future transactions, AED/PHP isolation, CSV, reminder priorities and expected contribution periods. Instrumentation tests compile in CI; emulator execution remains outstanding.

Remaining scope: native device UI validation, accessibility refinements, robust offline import/merge, backfill/reconciliation audit, artwork, notification scheduling if desired, and final end-to-end parity review.

## Group 7 — Android UI/UX and reliability hardening

- Bounded, saveable navigation history; system Back closes navigation or returns to a previous screen. Stable test tags and heading semantics for native Compose screens.
- Inline expense and monthly budget validation, preventing invalid save attempts; saveable expense draft strings and selected category on activity recreation.
- Light/dark theme colors adjusted for readable 4.5:1+ contrast against app surfaces and primary-action text.
- Main dashboard now derives figures from the same historical reporting cutoff as Group 6.
- Android CI includes a separate headless emulator execution job (API 35, KVM, Node.js 24-compatible Android Emulator Runner v2) for UI and SQLite instrumentation, in addition to the standard compiled tests, lint and debug APK.
- Test matrix and manual gaps documented in [GROUP7_ANDROID_HARDENING.md](GROUP7_ANDROID_HARDENING.md). UI-test code is added; verify a successful emulator CI run before treating it as executed/passing.
- Remaining work: process-death recovery, full TalkBack/RTL/large-font audit, cross-device/backend E2E, automatic import/sync, companion artwork, advanced notifications and visual parity details.

## Group 8 — Final source-parity and local recovery audit

- New privacy-preserving on-device audit of source-shaped budgets, limits, UUIDs, dates, managed expense amount/category/date, bill payment periods, debts, savings, government contributions, remittances and MP2 links.
- Versioned, checksum-validated JSON **device-only** backup export and explicitly confirmed **transactional replacement** through the system document picker; exported files are unencrypted and not synchronized.
- Web-account data is never imported into or overwritten by this local backup workflow. Strict validation rejects inconsistent backups before database modification.
- JVM and Android emulator tests cover backups and SQLite rollback. See [GROUP8_PARITY_AUDIT.md](GROUP8_PARITY_AUDIT.md) for verified parity and release blockers.
