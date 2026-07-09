package com.example.splitflat.ui.group

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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
    var members by remember { mutableStateOf<Map<String, User>>(emptyMap()) }
    var expenses by remember { mutableStateOf<List<Expense>>(emptyList()) }
    var balances by remember { mutableStateOf<Map<String, Double>>(emptyMap()) } // Positive means they are owed, Negative means they owe
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(groupId) {
        // Fetch group details
        db.collection("groups").document(groupId).get().addOnSuccessListener { snapshot ->
            val fetchedGroup = snapshot.toObject(Group::class.java)
            group = fetchedGroup
            
            if (fetchedGroup != null) {
                // Fetch members
                db.collection("users").whereIn("uid", fetchedGroup.members).get().addOnSuccessListener { usersSnapshot ->
                    val usersMap = usersSnapshot.documents.mapNotNull { it.toObject(User::class.java) }.associateBy { it.uid }
                    members = usersMap
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
                    // The person who paid gets positive balance
                    userBalances[expense.paidBy] = (userBalances[expense.paidBy] ?: 0.0) + expense.amount
                    
                    if (expense.splits.isNotEmpty()) {
                        // New format: splits map contains the exact amount each person owes
                        expense.splits.forEach { (uid, amountOwed) ->
                            userBalances[uid] = (userBalances[uid] ?: 0.0) - amountOwed
                        }
                    } else if (expense.splitAmong.isNotEmpty()) {
                        // Legacy format support for EQUAL
                        val amountPerPerson = expense.amount / expense.splitAmong.size
                        expense.splitAmong.forEach { uid ->
                            userBalances[uid] = (userBalances[uid] ?: 0.0) - amountPerPerson
                        }
                    }
                }
                balances = userBalances
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
                // Balances Summary
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Balances", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val myBalance = balances[currentUserUid] ?: 0.0
                        val formattedBalance = ((myBalance * 100.0).roundToInt() / 100.0)
                        
                        if (formattedBalance > 0.01) {
                            Text("You are owed: $$formattedBalance", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                        } else if (formattedBalance < -0.01) {
                            Text("You owe: $${-formattedBalance}", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                        } else {
                            Text("You are settled up!", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
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
                            ExpenseCard(expense, members)
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
