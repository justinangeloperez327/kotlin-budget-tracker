package dev.budgettracker.domain

import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

enum class Category(val label: String) {
    HOUSING("Housing"), FOOD("Food"), TRANSPORT("Transport"), SHOPPING("Shopping"),
    HEALTH("Health"), ENTERTAINMENT("Entertainment"), OTHER("Other"),
}

enum class ExpenseSource { MANUAL, BILL_PAYMENT, DEBT_PAYMENT, REMITTANCE }

data class Expense(
    val id: UUID, val description: String, val amount: Money,
    val category: Category, val date: LocalDate,
    val source: ExpenseSource = ExpenseSource.MANUAL,
) {
    init {
        require(description.isNotBlank() && description.length <= 120)
        require(amount.currency == Currency.AED && amount.minorUnits > 0L)
    }
}

data class MonthlyBudget(val month: YearMonth, val amount: Money) {
    init { require(amount.currency == Currency.AED) }
}

data class CategoryAllocation(val month: YearMonth, val category: Category, val amount: Money) {
    init { require(amount.currency == Currency.AED) }
}

data class RecurringBill(
    val id: UUID, val name: String, val category: Category, val amount: Money,
    val dueDay: Int, val startMonth: YearMonth, val endMonth: YearMonth? = null,
    val active: Boolean = true,
) {
    init { require(amount.currency == Currency.AED && dueDay in 1..31) }
}

data class BillPayment(
    val id: UUID, val billId: UUID, val period: YearMonth,
    val amount: Money, val paymentDate: LocalDate, val expenseId: UUID,
)

data class Debt(
    val id: UUID, val name: String, val originalAmount: Money,
    val startDate: LocalDate, val dueDate: LocalDate? = null,
)

data class DebtPayment(
    val id: UUID, val debtId: UUID, val amount: Money,
    val paymentDate: LocalDate, val expenseId: UUID,
)

data class SavingsGoal(
    val id: UUID, val name: String, val targetAmount: Money,
    val startDate: LocalDate, val targetDate: LocalDate? = null,
)

data class SavingsDeposit(
    val id: UUID, val goalId: UUID, val amount: Money, val depositDate: LocalDate,
)

data class Remittance(
    val id: UUID, val recipient: String, val sentAmount: Money,
    val feeAmount: Money, val receivedAmount: Money?,
    val transferDate: LocalDate, val principalAsExpense: Boolean,
    val expenseId: UUID? = null,
) {
    init {
        require(sentAmount.currency == Currency.AED && feeAmount.currency == Currency.AED)
        require(receivedAmount == null || receivedAmount.currency == Currency.PHP)
    }
}

enum class ContributionProvider { SSS, PHILHEALTH, PAGIBIG }
enum class ContributionStatus { PAID, PENDING, MISSED }

data class GovernmentAccount(
    val id: UUID, val provider: ContributionProvider,
    val memberType: String, val active: Boolean = true,
)

data class GovernmentContribution(
    val id: UUID, val accountId: UUID, val period: YearMonth,
    val amount: Money, val status: ContributionStatus, val paymentDate: LocalDate? = null,
) {
    init {
        require(amount.currency == Currency.PHP)
        require(status != ContributionStatus.PAID || paymentDate != null)
    }
}

data class Mp2Account(val id: UUID, val name: String, val active: Boolean = true)

data class Mp2Deposit(
    val id: UUID, val accountId: UUID, val paymentDate: LocalDate, val amount: Money,
) {
    init { require(amount.currency == Currency.PHP) }
}
