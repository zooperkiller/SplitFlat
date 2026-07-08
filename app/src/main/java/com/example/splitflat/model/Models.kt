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
    val members: List<String> = emptyList() // List of User UIDs
)

data class Expense(
    val id: String = "",
    val groupId: String = "",
    val title: String = "",
    val amount: Double = 0.0,
    val paidBy: String = "", // UID of user who paid
    val splitAmong: List<String> = emptyList(), // UIDs of users who owe
    val timestamp: Long = 0L
)
