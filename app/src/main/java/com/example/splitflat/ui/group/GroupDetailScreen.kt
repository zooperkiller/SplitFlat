package com.example.splitflat.ui.group

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
    onNavigateToAddExpense: (String) -> Unit,
    onEditExpense: (String, String) -> Unit
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
    
    // Filtering state
    var selectedTimeFilter by remember { mutableStateOf("ALL") }
    var timeDropdownExpanded by remember { mutableStateOf(false) }
    var selectedPayerFilter by remember { mutableStateOf("ALL") }
    var payerDropdownExpanded by remember { mutableStateOf(false) }
    
    val filteredExpenses = remember(expenses, selectedTimeFilter, selectedPayerFilter) {
        expenses.filter { expense ->
            val passesTime = when (selectedTimeFilter) {
                "7_DAYS" -> {
                    val sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
                    expense.timestamp >= sevenDaysAgo
                }
                "30_DAYS" -> {
                    val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
                    expense.timestamp >= thirtyDaysAgo
                }
                "90_DAYS" -> {
                    val ninetyDaysAgo = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000)
                    expense.timestamp >= ninetyDaysAgo
                }
                "THIS_MONTH" -> {
                    val calendar = java.util.Calendar.getInstance()
                    val currentYear = calendar.get(java.util.Calendar.YEAR)
                    val currentMonth = calendar.get(java.util.Calendar.MONTH)
                    
                    val expenseCalendar = java.util.Calendar.getInstance().apply { timeInMillis = expense.timestamp }
                    expenseCalendar.get(java.util.Calendar.YEAR) == currentYear &&
                            expenseCalendar.get(java.util.Calendar.MONTH) == currentMonth
                }
                "LAST_MONTH" -> {
                    val calendar = java.util.Calendar.getInstance()
                    calendar.add(java.util.Calendar.MONTH, -1)
                    val lastYear = calendar.get(java.util.Calendar.YEAR)
                    val lastMonth = calendar.get(java.util.Calendar.MONTH)
                    
                    val expenseCalendar = java.util.Calendar.getInstance().apply { timeInMillis = expense.timestamp }
                    expenseCalendar.get(java.util.Calendar.YEAR) == lastYear &&
                            expenseCalendar.get(java.util.Calendar.MONTH) == lastMonth
                }
                else -> true
            }
            
            val passesPayer = when (selectedPayerFilter) {
                "ALL" -> true
                else -> expense.paidBy == selectedPayerFilter
            }
            
            passesTime && passesPayer
        }
    }
    
    // Invite Dialog State
    var showInviteDialog by remember { mutableStateOf(false) }
    var inviteEmail by remember { mutableStateOf("") }
    var inviteError by remember { mutableStateOf<String?>(null) }
    var isInviting by remember { mutableStateOf(false) }

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
                actions = {
                    IconButton(onClick = { showInviteDialog = true }) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = "Invite Member")
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
                
                // Analytics Section (Donut Chart)
                if (expenses.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    val categoryTotals = expenses.groupBy { it.category }.mapValues { entry -> entry.value.sumOf { it.amount } }
                    DonutChart(categoryTotals = categoryTotals)
                    Spacer(modifier = Modifier.height(16.dp))
                }

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
                                        "₹${String.format(Locale.US, "%.2f", tx.amount)}",
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
                                text = if (isOwed) "+₹${String.format(Locale.US, "%.2f", balance)}" else "-₹${String.format(Locale.US, "%.2f", -balance)}",
                        color = color,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                               Spacer(modifier = Modifier.height(24.dp))
                Text("Expenses", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(8.dp))

                if (expenses.isNotEmpty()) {
                    // Filter Row UI
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Time Range Filter Dropdown
                        Box {
                            FilterChip(
                                selected = selectedTimeFilter != "ALL",
                                onClick = { timeDropdownExpanded = true },
                                label = {
                                    Text(
                                        when (selectedTimeFilter) {
                                            "7_DAYS" -> "Time: Last 7 Days"
                                            "30_DAYS" -> "Time: Last 1 Month"
                                            "90_DAYS" -> "Time: Last 3 Months"
                                            "THIS_MONTH" -> "Time: This Month"
                                            "LAST_MONTH" -> "Time: Last Month"
                                            else -> "Time: All"
                                        }
                                    )
                                },
                                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
                            )
                            DropdownMenu(
                                expanded = timeDropdownExpanded,
                                onDismissRequest = { timeDropdownExpanded = false }
                            ) {
                                DropdownMenuItem(text = { Text("All Time") }, onClick = { selectedTimeFilter = "ALL"; timeDropdownExpanded = false })
                                DropdownMenuItem(text = { Text("Last 7 Days") }, onClick = { selectedTimeFilter = "7_DAYS"; timeDropdownExpanded = false })
                                DropdownMenuItem(text = { Text("Last 30 Days (1 Month)") }, onClick = { selectedTimeFilter = "30_DAYS"; timeDropdownExpanded = false })
                                DropdownMenuItem(text = { Text("Last 90 Days (3 Months)") }, onClick = { selectedTimeFilter = "90_DAYS"; timeDropdownExpanded = false })
                                DropdownMenuItem(text = { Text("This Month") }, onClick = { selectedTimeFilter = "THIS_MONTH"; timeDropdownExpanded = false })
                                DropdownMenuItem(text = { Text("Last Month") }, onClick = { selectedTimeFilter = "LAST_MONTH"; timeDropdownExpanded = false })
                            }
                        }

                        // Payer Filter Dropdown
                        Box {
                            val selectedPayerName = if (selectedPayerFilter == "ALL") "All" else members.find { it.uid == selectedPayerFilter }?.name ?: "Unknown"
                            FilterChip(
                                selected = selectedPayerFilter != "ALL",
                                onClick = { payerDropdownExpanded = true },
                                label = { Text("Paid By: $selectedPayerName") },
                                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
                            )
                            DropdownMenu(
                                expanded = payerDropdownExpanded,
                                onDismissRequest = { payerDropdownExpanded = false }
                            ) {
                                DropdownMenuItem(text = { Text("All Members") }, onClick = { selectedPayerFilter = "ALL"; payerDropdownExpanded = false })
                                members.forEach { member ->
                                    DropdownMenuItem(
                                        text = { Text(member.name) },
                                        onClick = { selectedPayerFilter = member.uid; payerDropdownExpanded = false }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (expenses.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No expenses yet.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                    }
                } else {
                    if (filteredExpenses.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("No matching expenses found.", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredExpenses) { expense ->
                                ExpenseCard(
                                    expense = expense, 
                                    members = members.associateBy { it.uid },
                                    onEdit = { onEditExpense(groupId, expense.id) },
                                    onDelete = {
                                        db.collection("expenses").document(expense.id).delete()
                                    }
                                )
                            }
                        }
                    }
                }     }
            }
        }
    }
    
    // Invite Dialog
    if (showInviteDialog) {
        AlertDialog(
            onDismissRequest = { 
                if (!isInviting) {
                    showInviteDialog = false 
                    inviteEmail = ""
                    inviteError = null
                }
            },
            title = { Text("Invite Member") },
            text = {
                Column {
                    Text("Enter the email address of the user you want to invite.")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inviteEmail,
                        onValueChange = { inviteEmail = it },
                        label = { Text("Email Address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (inviteError != null) {
                        Text(
                            text = inviteError!!,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val email = inviteEmail.trim()
                        if (email.isBlank()) {
                            inviteError = "Email cannot be empty."
                            return@Button
                        }
                        isInviting = true
                        inviteError = null
                        
                        db.collection("users").whereEqualTo("email", email).get()
                            .addOnSuccessListener { snapshot ->
                                if (snapshot.isEmpty) {
                                    inviteError = "User not found!"
                                    isInviting = false
                                } else {
                                    val user = snapshot.documents[0].toObject(User::class.java)
                                    if (user != null) {
                                        val currentMembers = group?.members ?: emptyList()
                                        if (currentMembers.contains(user.uid)) {
                                            inviteError = "User is already in this group!"
                                            isInviting = false
                                        } else {
                                            // Update Firestore
                                            val updatedMembers = currentMembers + user.uid
                                            db.collection("groups").document(groupId)
                                                .update("members", updatedMembers)
                                                .addOnSuccessListener {
                                                    isInviting = false
                                                    showInviteDialog = false
                                                    inviteEmail = ""
                                                }
                                                .addOnFailureListener {
                                                    inviteError = "Failed to add member."
                                                    isInviting = false
                                                }
                                        }
                                    }
                                }
                            }
                            .addOnFailureListener {
                                inviteError = "Error searching for user."
                                isInviting = false
                            }
                    },
                    enabled = !isInviting
                ) {
                    if (isInviting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Invite")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showInviteDialog = false 
                        inviteEmail = ""
                        inviteError = null
                    },
                    enabled = !isInviting
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ExpenseCard(
    expense: Expense, 
    members: Map<String, User>,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val paidByName = members[expense.paidBy]?.name ?: "Someone"
    var expanded by remember { mutableStateOf(false) }
    
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
            Column(modifier = Modifier.weight(1f)) {
                Text(text = expense.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(text = "Paid by $paidByName", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Text(
                text = "₹${String.format("%.2f", expense.amount)}",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 8.dp)
            )
            
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More Options")
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = { 
                            expanded = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = { 
                            expanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DonutChart(categoryTotals: Map<String, Double>) {
    val totalAmount = categoryTotals.values.sum()
    if (totalAmount <= 0.0) return

    // Define colors for categories
    val colors = listOf(
        Color(0xFFE76F51), Color(0xFFF4A261), Color(0xFFE9C46A),
        Color(0xFF2A9D8F), Color(0xFF264653), Color(0xFF8AB17D)
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(150.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                var currentAngle = -90f
                var colorIndex = 0

                categoryTotals.forEach { (_, amount) ->
                    val sweepAngle = (amount / totalAmount).toFloat() * 360f
                    drawArc(
                        color = colors[colorIndex % colors.size],
                        startAngle = currentAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = 40f, cap = StrokeCap.Butt)
                    )
                    currentAngle += sweepAngle
                    colorIndex++
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Total", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                Text("₹${String.format(Locale.US, "%.0f", totalAmount)}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            var colorIndex = 0
            categoryTotals.forEach { (category, amount) ->
                val percentage = (amount / totalAmount) * 100
                if (percentage > 5) { // Only show significant slices in legend
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Canvas(modifier = Modifier.size(8.dp)) {
                                drawCircle(color = colors[colorIndex % colors.size])
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(category.split(" ").lastOrNull() ?: category, fontSize = 10.sp)
                        }
                        Text("${percentage.toInt()}%", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }
                colorIndex++
            }
        }
    }
}
