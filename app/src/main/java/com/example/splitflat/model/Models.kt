package com.example.splitflat.model

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = ""
)

data class Group(
    val id: String = "",
    val name: String = "",
    val createdBy: String = "",
    val members: List<String> = emptyList(), // Store UIDs of members
    val simplifyDebts: Boolean = false, // If true, group uses the Smart Debt Simplification algorithm
    val timestamp: Long = 0L
)

data class Expense(
    val id: String = "",
    val groupId: String = "",
    val title: String = "",
    val amount: Double = 0.0,
    val paidBy: String = "",
    val splitType: String = "EQUAL", // "EQUAL", "EXACT", "PERCENTAGE"
    val category: String = "Other", // e.g. Food, Travel, Utilities
    val splitAmong: List<String> = emptyList(), // Keep for legacy EQUAL splits if needed
    val splits: Map<String, Double> = emptyMap(), // Maps User UID to exact amount owed
    val timestamp: Long = 0L
)
