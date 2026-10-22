package dev.budgettracker.domain

import java.util.UUID

/** Recheck all invariants atomically when persistence is implemented. */
object AccountingRules {
    fun validateLinkedExpenses(
        expenses: List<Expense>,
        billPayments: List<BillPayment>,
        debtPayments: List<DebtPayment>,
    ) {
        val byId = expenses.associateBy { it.id }
        require(byId.size == expenses.size) { "Duplicate expense ID" }
        val links = mutableSetOf<UUID>()
        billPayments.forEach { payment ->
            require(links.add(payment.expenseId)) { "Expense linked more than once" }
            val expense = requireNotNull(byId[payment.expenseId]) { "Missing bill expense" }
            require(expense.source == ExpenseSource.BILL_PAYMENT && expense.amount == payment.amount)
        }
        debtPayments.forEach { payment ->
            require(links.add(payment.expenseId)) { "Expense linked more than once" }
            val expense = requireNotNull(byId[payment.expenseId]) { "Missing debt expense" }
            require(expense.source == ExpenseSource.DEBT_PAYMENT && expense.amount == payment.amount)
        }
        expenses.filter { it.source == ExpenseSource.BILL_PAYMENT || it.source == ExpenseSource.DEBT_PAYMENT }
            .forEach { require(it.id in links) { "Orphaned linked expense" } }
    }

    fun remittancePrincipalExpense(remittance: Remittance): Money =
        if (remittance.principalAsExpense) remittance.sentAmount else Money.zero(Currency.AED)
}
