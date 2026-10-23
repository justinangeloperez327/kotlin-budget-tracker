# Source parity: next-budget-tracker → native Android

Target reference: https://github.com/justinangeloperez327/next-budget-tracker (main).

The goal is a feature-complete Kotlin/Jetpack Compose Android adaptation preserving business logic, accounts, data, charts, forms, themes, and reports. Desktop browser layouts will become idiomatic touch/mobile layouts; exact CSS pixels are not a sensible Android requirement.

| Feature from Next.js | Android migration state |
| --- | --- |
| Login/register/logout/account/session | Not implemented |
| Dashboard summary | Partial (local budgets and expenses only) |
| Financial overview separated AED/PHP | Not implemented |
| Monthly and category budgets | Initial device-local editor |
| Expense CRUD, filters, CSV | Device-local add/list/delete only |
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
| Workspace save revisions and recovery | Not implemented |
| Secure backend/data sync with existing web account | Not implemented |

Source invariants: two currency lanes, integer minor-unit accounting, a scheduled bill is not yet an expense, exactly one linked expense per bill/debt payment, savings and MP2 are transfers rather than expenses, remittance fee is expense while own-account principal is transfer, no record is not automatically missed government contribution, ownership/validation/revision checks on all saves, no fake seeded transactions.

**Current data is device-only**. The web project authenticates using browser cookie sessions and same-origin writes. Android needs a separate secure mobile API/token protocol on the server; it must not bypass CSRF/origin checks or directly connect to PostgreSQL.

Port order: (1) native Android foundation + local budgets/expenses, (2) secure mobile auth/API and safely reconciled web data, (3) complete budgets/expenses, (4) bills/debts/savings, (5) remittances/government/MP2, (6) reports/history/reminders, all artwork and release testing. Financial fixtures from the original TypeScript tests must be used for calculation parity.

No automatic deployment, Play Store publishing or destructive migration.
