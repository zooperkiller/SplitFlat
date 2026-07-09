package com.example.splitflat.utils

import kotlin.math.absoluteValue
import kotlin.math.min

data class Transaction(
    val fromUid: String,
    val toUid: String,
    val amount: Double
)

object DebtSimplifier {

    /**
     * Takes a map of raw balances (positive = owed money, negative = owes money)
     * and returns a minimal list of direct transactions to settle all debts.
     */
    fun simplifyDebts(balances: Map<String, Double>): List<Transaction> {
        // Filter out balances that are effectively zero
        val validBalances = balances.filterValues { it.absoluteValue > 0.01 }.toMutableMap()
        
        // Debtors are people with negative balance
        val debtors = validBalances.filterValues { it < 0 }.mapValues { it.value.absoluteValue }.toMutableMap()
        // Creditors are people with positive balance
        val creditors = validBalances.filterValues { it > 0 }.toMutableMap()
        
        val transactions = mutableListOf<Transaction>()
        
        // Greedy matching: match the largest debtor with the largest creditor
        while (debtors.isNotEmpty() && creditors.isNotEmpty()) {
            val maxDebtor = debtors.maxByOrNull { it.value } ?: break
            val maxCreditor = creditors.maxByOrNull { it.value } ?: break
            
            val amount = min(maxDebtor.value, maxCreditor.value)
            
            transactions.add(Transaction(maxDebtor.key, maxCreditor.key, amount))
            
            // Adjust balances
            val remainingDebt = maxDebtor.value - amount
            val remainingCredit = maxCreditor.value - amount
            
            if (remainingDebt < 0.01) {
                debtors.remove(maxDebtor.key)
            } else {
                debtors[maxDebtor.key] = remainingDebt
            }
            
            if (remainingCredit < 0.01) {
                creditors.remove(maxCreditor.key)
            } else {
                creditors[maxCreditor.key] = remainingCredit
            }
        }
        
        return transactions
    }
}
