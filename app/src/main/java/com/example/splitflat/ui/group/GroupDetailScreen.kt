package com.example.splitflat.ui.group

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.splitflat.model.Expense
import com.example.splitflat.model.Group
import com.example.splitflat.model.User
import com.example.splitflat.utils.DebtSimplifier
import com.example.splitflat.utils.QrCodeGenerator
import com.example.splitflat.utils.Transaction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
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
    val coroutineScope = rememberCoroutineScope()

    var group by remember { mutableStateOf<Group?>(null) }
    var members by remember { mutableStateOf<List<User>>(emptyList()) }
    var expenses by remember { mutableStateOf<List<Expense>>(emptyList()) }
    var balances by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var suggestedTransactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    
    // Invite Dialog State
    var showInviteDialog by remember { mutableStateOf(false) }
    var inviteEmail by remember { mutableStateOf("") }
    var inviteError by remember { mutableStateOf<String?>(null) }
    var isInviting by remember { mutableStateOf(false) }

    // QR Code Dialog State
    var showQrDialog by remember { mutableStateOf(false) }

    // Settle Up Dialog State
    var showSettleUpDialog by remember { mutableStateOf(false) }
    var selectedDebtToSettle by remember { mutableStateOf<Transaction?>(null) }
    var settlementAmountText by remember { mutableStateOf("") }
    var isSettling by remember { mutableStateOf(false) }
    var settleError by remember { mutableStateOf<String?>(null) }

    fun loadData() {
        db.collection("groups").document(groupId).get().addOnSuccessListener { snapshot ->
            val fetchedGroup = snapshot?.toObject(Group::class.java)
            group = fetchedGroup
            
            if (fetchedGroup != null) {
                db.collection("users").whereIn("uid", fetchedGroup.members).get().addOnSuccessListener { usersSnapshot ->
                    members = usersSnapshot.documents.mapNotNull { it.toObject(User::class.java) }
                }
            }
        }

        db.collection("expenses")
            .whereEqualTo("groupId", groupId)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot != null) {
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
                    
                    if (group?.simplifyDebts == true) {
                        suggestedTransactions = DebtSimplifier.simplifyDebts(userBalances)
                    } else {
                        suggestedTransactions = emptyList()
                    }
                }
            }
    }

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

    val displayTitle = remember(group, members) {
        if (group?.isOneOnOne == true) {
            val otherMember = group?.members?.firstOrNull { it != currentUserUid }
            members.find { it.uid == otherMember }?.name ?: "Friend Ledger"
        } else {
            group?.name ?: "Group Details"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(displayTitle) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Show QR Code display
                    IconButton(onClick = { showQrDialog = true }) {
                        Icon(Icons.Filled.QrCode, contentDescription = "Group QR Code")
                    }
                    
                    // Show Invite button only if it's a regular group (not a 1-on-1 friend ledger)
                    if (group?.isOneOnOne == false) {
                        IconButton(onClick = { showInviteDialog = true }) {
                            Icon(Icons.Filled.PersonAdd, contentDescription = "Invite Member")
                        }
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
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    coroutineScope.launch {
                        isRefreshing = true
                        loadData()
                        kotlinx.coroutines.delay(500)
                        isRefreshing = false
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Balances", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        TextButton(onClick = {
                            showSettleUpDialog = true
                            selectedDebtToSettle = null
                            settlementAmountText = ""
                            settleError = null
                        }) {
                            Text("Settle Up")
                        }
                    }
                    
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
                }
            }
        }
    }
    
    // QR Code dialog
    if (showQrDialog) {
        val qrBitmap = remember(groupId) { QrCodeGenerator.generateQrCode(groupId) }
        AlertDialog(
            onDismissRequest = { showQrDialog = false },
            title = { Text(if (group?.isOneOnOne == true) "Friend Ledger QR" else "Group QR Code") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (group?.isOneOnOne == true) "Let your friend scan this QR code to join this direct ledger." else "Let other members scan this QR code using the SplitFlat scanner to join this group instantly.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier.size(200.dp)
                        )
                    } else {
                        Text("Failed to generate QR code", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Group ID: $groupId",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showQrDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Settle Up Dialog
    if (showSettleUpDialog) {
        val activeDebts = remember(balances) { DebtSimplifier.simplifyDebts(balances) }
        AlertDialog(
            onDismissRequest = { if (!isSettling) showSettleUpDialog = false },
            title = { Text("Settle Up Balances") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Select a balance to settle up:")
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (activeDebts.isEmpty()) {
                        Text("All settled up! No outstanding balances to settle.", color = MaterialTheme.colorScheme.primary)
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                            items(activeDebts) { tx ->
                                val fromName = members.find { it.uid == tx.fromUid }?.name ?: "Unknown"
                                val toName = members.find { it.uid == tx.toUid }?.name ?: "Unknown"
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            selectedDebtToSettle = tx
                                            settlementAmountText = String.format(Locale.US, "%.2f", tx.amount)
                                            settleError = null
                                        },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("$fromName owes $toName", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                        Text("₹${String.format(Locale.US, "%.2f", tx.amount)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }

                    selectedDebtToSettle?.let { tx ->
                        val fromName = members.find { it.uid == tx.fromUid }?.name ?: "Unknown"
                        val toName = members.find { it.uid == tx.toUid }?.name ?: "Unknown"
                        Spacer(modifier = Modifier.height(16.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Record Payment:", fontWeight = FontWeight.Bold)
                        Text("$fromName paid $toName", modifier = Modifier.padding(vertical = 4.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = settlementAmountText,
                            onValueChange = { settlementAmountText = it },
                            label = { Text("Amount paid (₹)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        settleError?.let { err ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(err, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val tx = selectedDebtToSettle
                        if (tx == null) {
                            settleError = "Please select a balance to settle"
                            return@Button
                        }
                        val amt = settlementAmountText.toDoubleOrNull()
                        if (amt == null || amt <= 0) {
                            settleError = "Please enter a valid amount"
                            return@Button
                        }
                        
                        isSettling = true
                        settleError = null

                        val fromName = members.find { it.uid == tx.fromUid }?.name ?: "Someone"
                        val toName = members.find { it.uid == tx.toUid }?.name ?: "Someone"
                        
                        val settlementId = UUID.randomUUID().toString()
                        val newExpense = Expense(
                            id = settlementId,
                            groupId = groupId,
                            title = "Settle Up: $fromName -> $toName",
                            amount = amt,
                            paidBy = tx.fromUid, // Debtor paid
                            splitType = "EXACT",
                            category = "📝 Other",
                            splits = mapOf(tx.toUid to amt), // Recipient owes the payer back, offsetting original debt
                            timestamp = System.currentTimeMillis()
                        )

                        db.collection("expenses").document(settlementId).set(newExpense)
                            .addOnSuccessListener {
                                isSettling = false
                                showSettleUpDialog = false
                                selectedDebtToSettle = null
                            }
                            .addOnFailureListener {
                                isSettling = false
                                settleError = "Failed to record payment"
                            }
                    },
                    enabled = !isSettling && selectedDebtToSettle != null
                ) {
                    if (isSettling) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Record Payment")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showSettleUpDialog = false
                        selectedDebtToSettle = null
                    },
                    enabled = !isSettling
                ) {
                    Text("Cancel")
                }
            }
        )
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
