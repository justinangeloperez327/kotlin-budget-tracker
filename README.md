# Kotlin Budget Tracker

**Your everyday money tracker for life between the UAE and the Philippines.**

Kotlin Budget Tracker is a native Android personal finance app for managing monthly budgets, daily expenses, recurring bills, debts, savings, remittances, and Philippine contribution records—all in one place.

Designed around **AED and PHP**, it lets you see where your money goes without mixing currencies or treating savings transfers as spending.

**Android 8.0+** · **Kotlin & Jetpack Compose** · **Device-only mode available**

## Download for Android

**[Download the Android test APK](https://github.com/justinangeloperez327/kotlin-budget-tracker/actions/runs/40657323503/artifacts/13749864973)** · [GitHub Releases](https://github.com/justinangeloperez327/kotlin-budget-tracker/releases)

The current download is a **debug test build (v0.9.0-rc.1)**. GitHub provides it as a ZIP file: download the archive, extract `app-debug.apk`, and open the APK on your Android phone to install it. You may need to sign in to GitHub to access the build artifact. The artifact is temporary; published releases will appear on the Releases page.

> **Preview software:** This is not a Google Play or production release. Try it with sample data first. If you already have an older installation with local financial data, export a backup before considering any uninstall—debug builds can have different signing certificates.

## What can you do with it?

| Feature | What it does |
| --- | --- |
| **Financial dashboard** | See your monthly budget, spending, balances, and activity at a glance, with AED and PHP totals kept separate. |
| **Monthly budgets** | Set an overall AED budget, assign category limits, and compare planned vs. actual spending. |
| **Expense tracker** | Record manual expenses, edit or delete them, search and filter transaction history, and export results to CSV. |
| **Recurring bills** | Track monthly, quarterly, or yearly bills, due periods, payment status, and linked expenses. |
| **Debt / Utang tracker** | Record debts, repayments, remaining balances, and payment history. |
| **Savings goals** | Set targets and track deposits and progress without counting deposits as expenses. |
| **Remittance tracker** | Record AED amounts sent, transfer fees, PHP amounts received, recipients, and transfer status. |
| **Philippine contributions** | Organize SSS, PhilHealth, and Pag-IBIG payment records and period statuses. |
| **Pag-IBIG MP2** | Track MP2 accounts, contributions, progress, and estimated maturity dates. |
| **Reports and reminders** | Review monthly/yearly financial summaries and see upcoming or outstanding items in the app. |
| **Data backup** | Check financial record integrity, export a device-only backup, and restore it after explicit confirmation. |

### Keep spending and transfers distinct

The app is built around a few useful accounting rules:

- **AED and PHP are separate currencies.** Totals and reports do not combine them into a misleading single figure.
- **Bill and debt payments are linked to their expenses** to help prevent duplicate spending records.
- **Savings and MP2 deposits are transfers into savings**, not consumption expenses.
- **Remittances distinguish transfer amounts and fees**, including transfers to your own accounts.

These are tracking features: the app **does not send money, pay bills, connect to a bank, or submit payments to government agencies**.

## How your data works

| Mode | Best for | Storage and behavior |
| --- | --- | --- |
| **Device-only** | Personal use without an account | Records stay in the app's private SQLite database on your Android device. You can use core tracking features without a backend connection. |
| **Web account** | Using a compatible Budget Tracker web workspace | Connects securely over HTTPS to a separately deployed [Next.js Budget Tracker](https://github.com/justinangeloperez327/next-budget-tracker) backend. Saves use revision checks to help prevent overwriting concurrent changes. |

**Important:** Device-only and web-account records are independent. Signing in does **not** automatically upload or merge local records with a web account. The compatible backend must be deployed separately before web-account sign-in will work.

### Privacy and recovery

- Local records are held in Android's private app storage; Android automatic backup is disabled.
- Web-account session tokens are encrypted using Android Keystore.
- A manual device-only backup can include your financial records. **Exported JSON backups are not encrypted**—store them securely.
- Restoring a backup **replaces** device-only data; it does not merge records.
- If a web-account save cannot be confirmed, editing is blocked until you refresh the server workspace.

## A personal finance app that fits your style

Use light or dark appearance and choose from several character-inspired color themes. The interface is built with Jetpack Compose and includes native Android navigation, forms, reports, and document export.

## Current availability and limitations

Kotlin Budget Tracker is in **public testing / pre-release development**, not yet a fully verified production app.

- In-app reminders are available, but **background Android notifications are not implemented**.
- Web-account mode requires a compatible, manually deployed backend; automatic offline write queues and local-to-web merging are not supported.
- Manual JSON backup files are plaintext, and local SQLite is not additionally encrypted at the application level.
- Some companion artwork and fine visual details from the web app have not been ported.
- Real-device acceptance, live backend end-to-end testing, and production release approval are still in progress.

Financial and contribution records are for personal tracking only—not official bank statements, remittance confirmations, or government payment receipts. MP2 maturity is an estimate.

## Built with

**Kotlin** · **Jetpack Compose / Material 3** · **SQLite** · **Android Keystore** · **Gradle**

The app is the native Android companion to [next-budget-tracker](https://github.com/justinangeloperez327/next-budget-tracker). Android builds and tests run through GitHub Actions, including emulator coverage for Android 15 and Android 16.

## Project documentation

- [Feature coverage and known gaps](docs/PARITY_CHECKLIST.md)
- [Data integrity, backup, and recovery](docs/GROUP8_PARITY_AUDIT.md)
- [Release safety and acceptance checklist](docs/GROUP10_PRODUCTION_ACCEPTANCE.md)
- [Latest Android builds](https://github.com/justinangeloperez327/kotlin-budget-tracker/actions)

---

**Made for clearer budgeting, more organized financial records, and better visibility across UAE and Philippine finances.**
