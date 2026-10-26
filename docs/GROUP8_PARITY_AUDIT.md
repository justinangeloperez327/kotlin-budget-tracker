# Group 8 — Android source-parity and recovery audit

Reference: https://github.com/justinangeloperez327/next-budget-tracker (main), including the canonical workspace-validation and linked-expense-normalization rules.

## Parity and safeguards

| Domain | Android implementation and source accounting rules |
| --- | --- |
| AED budget and expenses | Category allocations and history; integer fils; manual/linked expense protection |
| Bills | Monthly/quarterly/yearly schedule, one payment per period, linked AED expense |
| Debts | Repayments bounded by principal; one managed expense per payment |
| Savings | Targets and deposit history; transfer, not expense |
| Remittances | AED sent and fee, PHP received, support/own-account rules; linked expense for fees and eligible principal |
| Government accounts | SSS, PhilHealth and Pag-IBIG; explicit statuses, no automatic missed designation |
| MP2 | PHP deposit history, account metadata, dividend option and estimated five-year maturity |
| Reports | Monthly/yearly, category history, CSV, date-cutoff aware, never combine AED/PHP |
| Reminders | In-app only; does not create OS alerts or payments |
| Themes | Character color palettes and light/dark; original illustrations remain outstanding |
| Data durability | Device-only SQLite and separately authenticated web mode; no automatic sync or merge |

## Group 8 — Integrity audit and device-only backup

The **Data integrity & backup** screen runs source-shaped, privacy-preserving checks on budgets, UUIDs, amount bounds, payment/expense links, parent records, duplicate payment periods, contribution records, and MP2 relationships. It does not access bank accounts or contact a third-party service.

**Export:** In device-only mode choose **Export device-only backup** and select a location in Android's system file picker. The versioned JSON contains budgets, allocations, manual/linked expenses, and Group 4–5 financial records. Its SHA-256 checksum detects accidental damage, **not deliberate tampering**. The JSON is **plaintext, not encrypted**, and must be stored securely by the user.

**Restore:** Choose **Choose backup to restore**, review the creation date and counts, and explicitly confirm **Replace local data**. The app checks version, maximum bytes, checksum, source-shaped data, money/date fields and linked records before a single SQLite transaction replaces all device-only tables. A validation or SQL failure rolls back the transaction. Restore is a **full replacement**, not a merge.

Backup controls are disabled while a web account is signed in; a device backup is not an export of the server's PostgreSQL workspace.

## Verification

- JVM backup fixtures test roundtrip, checksum/format rejection, duplicate bill periods, broken managed expense links, oversized input, and cross-module records.
- API 35 emulator instrumentation verifies local database restore of a budget, manual/managed expenses, government records and MP2 deposit, plus failure rollback.
- Compose instrumentation checks the new section's accessibility and device-mode controls.
- Standard Android compilation, unit tests, lint and debug APK are separate from the emulator job; **both** must succeed.

## Unresolved blockers before claiming full production parity

1. Manual deployment of the updated Next.js mobile API and live cross-device login/conflict tests with test accounts.
2. Full TalkBack/RTL/large-font/process-death device acceptance and load/performance profiling.
3. Original source character companion illustrations and fine visual parity.
4. Background Android reminders/notifications, if required; only in-app reminders currently exist.
5. Automatic offline queue, cloud backup, and account/local data import or merge.
6. Production signing and Play Store track validation; CI produces a debug APK only.

No automatic deployment, PR or Play Store publication is configured.
