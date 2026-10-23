# Source parity: next-budget-tracker → native Android

Target reference: https://github.com/justinangeloperez327/next-budget-tracker (main).

The goal is a feature-complete Kotlin/Jetpack Compose Android adaptation preserving business logic, accounts, data, charts, forms, themes, and reports. Desktop browser layouts will become idiomatic touch/mobile layouts; exact CSS pixels are not a sensible Android requirement.

| Feature from Next.js | Android migration state |
| --- | --- |
| Login/register/logout/account/session | Implemented via HTTPS mobile API, Android Keystore token storage, manual backend deployment needed |
| Dashboard summary | Partial (local budgets and expenses only) |
| Financial overview separated AED/PHP | Not implemented |
| Monthly and category budgets | Group 3: whole-plan atomic save, monthly report, category variance charts in device-only and web-account modes |
| Expense CRUD, filters, CSV | Group 3: add/edit/delete, cross-month search and filters, filtered CSV export, managed linked-expense protection |
| Bills recurring and linked payment expense | Not implemented |
| Debt and repayment expense | Not implemented |
| Savings goals and deposit history | Not implemented |
| Remittances AED sent, fee, PHP received, rate | Not implemented |
| SSS/PhilHealth/Pag-IBIG and history | Not implemented |
| MP2 savings, maturity, history | Not implemented |
| Reports, CSV, historical analysis | Not implemented |
| In-app reminders | Not implemented |
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
