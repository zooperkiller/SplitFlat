package com.example.splitflat.ui.group

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.splitflat.model.Expense
import com.example.splitflat.model.Group
import com.example.splitflat.model.User
import com.example.splitflat.utils.DebtSimplifier
import com.example.splitflat.utils.Transaction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    groupId: String,
    onNavigateBack: () -> Unit,
    onNavigateToAddExpense: (String) -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val currentUserUid = auth.currentUser?.uid ?: return

    var group by remember { mutableStateOf<Group?>(null) }
    var members by remember { mutableStateOf<List<User>>(emptyList()) }
    var expenses by remember { mutableStateOf<List<Expense>>(emptyList()) }
    var balances by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var suggestedTransactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(groupId) {
        // Fetch group details
        db.collection("groups").document(groupId).addSnapshotListener { snapshot, _ ->
            val fetchedGroup = snapshot?.toObject(Group::class.java)
            group = fetchedGroup
            
            if (fetchedGroup != null) {
                // Fetch members
                db.collection("users").whereIn("uid", fetchedGroup.members).get().addOnSuccessListener { usersSnapshot ->
                    members = usersSnapshot.documents.mapNotNull { it.toObject(User::class.java) }
                }
            }
        }

        // Listen for expenses
        db.collection("expenses")
            .whereEqualTo("groupId", groupId)
            .addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null) {
                    isLoading = false
                    return@addSnapshotListener
                }
                
                val fetchedExpenses = snapshot.documents.mapNotNull { it.toObject(Expense::class.java) }
                    .sortedByDescending { it.timestamp }
                expenses = fetchedExpenses
                
                // Calculate balances
                val userBalances = mutableMapOf<String, Double>()
                fetchedExpenses.forEach { expense ->
                    userBalances[expense.paidBy] = (userBalances[expense.paidBy] ?: 0.0) + expense.amount
                    
                    if (expense.splits.isNotEmpty()) {
                        expense.splits.forEach { (uid, amountOwed) ->
                            userBalances[uid] = (userBalances[uid] ?: 0.0) - amountOwed
                        }
                    } else if (expense.splitAmong.isNotEmpty()) {
                        val amountPerPerson = expense.amount / expense.splitAmong.size
                        expense.splitAmong.forEach { uid ->
                            userBalances[uid] = (userBalances[uid] ?: 0.0) - amountPerPerson
                        }
                    }
                }
                balances = userBalances
                
                // If the group has simplification enabled, calculate it
                if (group?.simplifyDebts == true) {
                    suggestedTransactions = DebtSimplifier.simplifyDebts(userBalances)
                } else {
                    suggestedTransactions = emptyList()
                }
                isLoading = false
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group?.name ?: "Group Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToAddExpense(groupId) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Text("+", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
            ) {
                Text("Balances", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Smart Debt Simplification", fontSize = 14.sp)
                    Switch(
                        checked = group?.simplifyDebts == true,
                        onCheckedChange = { isChecked ->
                            db.collection("groups").document(groupId)
                                .update("simplifyDebts", isChecked)
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))

                if (group?.simplifyDebts == true) {
                    if (suggestedTransactions.isEmpty()) {
                        Text("All settled up!", color = MaterialTheme.colorScheme.primary)
                    } else {
                        suggestedTransactions.forEach { tx ->
                            val fromName = members.find { it.uid == tx.fromUid }?.name ?: "Unknown"
                            val toName = members.find { it.uid == tx.toUid }?.name ?: "Unknown"
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            ) {
                                Row(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        "$fromName pays $toName",
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "$${String.format(Locale.US, "%.2f", tx.amount)}",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                } else {
                    balances.forEach { (uid, balance) ->
                        val userName = members.find { it.uid == uid }?.name ?: "Unknown"
                        val isOwed = balance > 0
                        val color = if (isOwed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(userName, modifier = Modifier.weight(1f))
                            Text(
                                text = if (isOwed) "+$${String.format(Locale.US, "%.2f", balance)}" else "-$${String.format(Locale.US, "%.2f", -balance)}",
                                color = color,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text("Expenses", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(8.dp))

                if (expenses.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No expenses yet.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(expenses) { expense ->
                            ExpenseCard(expense, members.associateBy { it.uid })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExpenseCard(expense: Expense, members: Map<String, User>) {
    val paidByName = members[expense.paidBy]?.name ?: "Someone"
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = expense.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(text = "Paid by $paidByName", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Text(
                text = "$${String.format("%.2f", expense.amount)}",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
