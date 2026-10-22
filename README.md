# Kotlin Budget Tracker

Kotlin/JVM and Ktor port of the existing Next.js budget tracker. This repository is a NEW implementation that preserves the accounting design; it does not include or run Next.js or TypeScript.

## Project status

**Foundation initialized.** Ktor server, browser navigation scaffold, typed AED/PHP finance domain, monthly budget calculations, initial linked-expense rules, tests, and CI are included. Full authentication, PostgreSQL data storage, real transaction entry, reports, and theme parity are NOT implemented yet. Do not connect real financial data to this bootstrap.

## Technology

- Kotlin 2.4.21, JDK 21, Gradle 9.8.1
- Ktor 3.6.0 (JVM, Netty)
- JUnit 5 and Ktor test host
- PostgreSQL planned for later persistence
- No Node.js, Vercel auto-deployment, or publishing

## Local start

Install JDK 21 and Gradle 9.8.1. Clone the repo, run gradle test and gradle run, and open http://localhost:8080/dashboard. Health check: http://localhost:8080/health. An official Gradle Wrapper still needs to be generated; use installed Gradle in this first commit.

## Source feature checklist

Original project: https://github.com/justinangeloperez327/next-budget-tracker

- AED: monthly budgets, category allocations, expenses, recurring bills, debt, savings goals, outgoing remittances
- PHP: SSS, PhilHealth, Pag-IBIG, MP2, received remittances
- Financial overview, reports/history, reminders, linked expenses, CSV exports
- Authentication, PostgreSQL, concurrency-safe workspace saving, character themes

## Accounting invariants

All amounts are integer minor units. AED and PHP must never be directly combined. Bill schedules and debt principal are not expenses; bill and debt payments create exactly one linked expense. Savings and MP2 deposits are transfers, not spending. Remittance principal can be spending or a transfer depending on destination; remittance fees are expenses. Missing contribution records are not automatically marked missed. The eventual server must validate ownership, IDs, links, limits, and optimistic revisions in one database transaction.

## Next milestones

1. PostgreSQL schema and migrations matching source; password hashing, secure sessions and rate limiting.
2. Authenticated dashboard, budgets, category allocations, expenses and revision-safe API.
3. Bills, debts, savings, remittances, government contributions and MP2.
4. Reports, history, reminders, responsive design/themes and browser E2E tests.
5. Security, migration, backup/recovery and manual deployment.

No existing financial records, passwords or production database settings have been copied.
