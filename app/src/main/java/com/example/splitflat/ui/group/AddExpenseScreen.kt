package com.example.splitflat.ui.group

import androidx.compose.animation.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.splitflat.model.Expense
import com.example.splitflat.model.Group
import com.example.splitflat.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    groupId: String,
    expenseId: String? = null,
    onNavigateBack: () -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val currentUserUid = auth.currentUser?.uid ?: return
    
    var title by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("🍔 Food") }
    var splitType by remember { mutableStateOf("EQUAL") } // EQUAL, EXACT, PERCENTAGE
    
    // Custom Payer & Selective Splitting State
    var selectedPayerUid by remember { mutableStateOf(currentUserUid) }
    var includedMembers by remember { mutableStateOf(setOf<String>()) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    
    val categories = listOf("🍔 Food", "🚗 Transport", "🏠 Housing", "🛒 Groceries", "🎉 Fun", "📝 Other")
    
    // Split state maps User ID to the input value (exact amount or percentage)
    var splitValues by remember { mutableStateOf(mapOf<String, String>()) }
    
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    var group by remember { mutableStateOf<Group?>(null) }
    var members by remember { mutableStateOf<List<User>>(emptyList()) }
    
    LaunchedEffect(groupId) {
        db.collection("groups").document(groupId).get().addOnSuccessListener { snapshot ->
            val fetchedGroup = snapshot.toObject(Group::class.java)
            group = fetchedGroup
            if (fetchedGroup != null && fetchedGroup.members.isNotEmpty()) {
                db.collection("users").whereIn("uid", fetchedGroup.members).get().addOnSuccessListener { usersSnapshot ->
                    members = usersSnapshot.documents.mapNotNull { it.toObject(User::class.java) }
                    // Default includedMembers to all members if we are creating a new expense
                    if (expenseId == null) {
                        includedMembers = members.map { it.uid }.toSet()
                    }
                }
            }
        }
        
        // If in Edit Mode, fetch expense
        if (expenseId != null) {
            db.collection("expenses").document(expenseId).get().addOnSuccessListener { snapshot ->
                val expense = snapshot.toObject(Expense::class.java)
                if (expense != null) {
                    title = expense.title
                    amountText = if (expense.amount % 1.0 == 0.0) {
                        expense.amount.toInt().toString()
                    } else {
                        expense.amount.toString()
                    }
                    selectedCategory = expense.category
                    splitType = expense.splitType
                    selectedPayerUid = expense.paidBy
                    
                    includedMembers = if (expense.splits.isNotEmpty()) {
                        expense.splits.keys.toSet()
                    } else {
                        expense.splitAmong.toSet()
                    }
                    
                    if (splitType == "EXACT") {
                        val exactSplits = mutableMapOf<String, String>()
                        expense.splits.forEach { (uid, amount) ->
                            exactSplits[uid] = if (amount % 1.0 == 0.0) amount.toInt().toString() else amount.toString()
                        }
                        splitValues = exactSplits
                    } else if (splitType == "PERCENTAGE") {
                        val pctSplits = mutableMapOf<String, String>()
                        expense.splits.forEach { (uid, amount) ->
                            val pct = (amount / expense.amount) * 100
                            pctSplits[uid] = if (pct % 1.0 == 0.0) pct.toInt().toString() else pct.toString()
                        }
                        splitValues = pctSplits
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (expenseId != null) "Edit Expense" else "Add Expense") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("What was this for?") },
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Amount (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            // Paid By Selection Dropdown
            Text("Paid By", fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Start))
            Spacer(modifier = Modifier.height(8.dp))
            val selectedPayer = members.find { it.uid == selectedPayerUid }
            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = !dropdownExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = if (selectedPayerUid == currentUserUid) "You (${selectedPayer?.name ?: ""})" else selectedPayer?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    members.forEach { user ->
                        DropdownMenuItem(
                            text = { 
                                Text(if (user.uid == currentUserUid) "You (${user.name})" else user.name) 
                            },
                            onClick = {
                                selectedPayerUid = user.uid
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Category Selection
            Text("Category", fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Start))
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category) }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Split Type Selection
            TabRow(
                selectedTabIndex = when(splitType) {
                    "EQUAL" -> 0
                    "EXACT" -> 1
                    "PERCENTAGE" -> 2
                    else -> 0
                },
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Tab(selected = splitType == "EQUAL", onClick = { splitType = "EQUAL" }) {
                    Text("Equally", modifier = Modifier.padding(12.dp))
                }
                Tab(selected = splitType == "EXACT", onClick = { splitType = "EXACT" }) {
                    Text("Exact", modifier = Modifier.padding(12.dp))
                }
                Tab(selected = splitType == "PERCENTAGE", onClick = { splitType = "PERCENTAGE" }) {
                    Text("Percent", modifier = Modifier.padding(12.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Split Between Checkbox List
            Text("Split Between", fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Start))
            Spacer(modifier = Modifier.height(8.dp))
            
            val totalAmount = amountText.toDoubleOrNull() ?: 0.0
            
            Column(modifier = Modifier.fillMaxWidth()) {
                members.forEach { user ->
                    val isIncluded = includedMembers.contains(user.uid)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isIncluded,
                            onCheckedChange = { checked ->
                                includedMembers = if (checked) {
                                    includedMembers + user.uid
                                } else {
                                    includedMembers - user.uid
                                }
                            }
                        )
                        
                        Text(
                            text = if (user.uid == currentUserUid) "You (${user.name})" else user.name,
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Medium,
                            color = if (isIncluded) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                        
                        if (isIncluded) {
                            when (splitType) {
                                "EQUAL" -> {
                                    val count = includedMembers.size
                                    val share = if (count > 0) totalAmount / count else 0.0
                                    Text(
                                        text = String.format("₹%.2f", share),
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                "EXACT", "PERCENTAGE" -> {
                                    OutlinedTextField(
                                        value = splitValues[user.uid] ?: "",
                                        onValueChange = { newValue -> 
                                            splitValues = splitValues.toMutableMap().apply { put(user.uid, newValue) }
                                        },
                                        label = { Text(if (splitType == "EXACT") "₹" else "%") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.width(100.dp)
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "Excluded",
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f, fill = false))
            Spacer(modifier = Modifier.height(24.dp))

            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Text(
                    text = errorMessage ?: "", 
                    color = MaterialTheme.colorScheme.error, 
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull()
                    if (title.isBlank() || amount == null || amount <= 0) {
                        errorMessage = "Please enter a valid title and amount"
                        return@Button
                    }
                    if (group == null || members.isEmpty()) {
                        errorMessage = "Group data not fully loaded"
                        return@Button
                    }
                    if (includedMembers.isEmpty()) {
                        errorMessage = "Please select at least one member to split with"
                        return@Button
                    }
                    
                    val calculatedSplits = mutableMapOf<String, Double>()
                    
                    if (splitType == "EQUAL") {
                        val amountPerPerson = amount / includedMembers.size
                        includedMembers.forEach { uid -> calculatedSplits[uid] = amountPerPerson }
                    } else if (splitType == "EXACT") {
                        var sum = 0.0
                        includedMembers.forEach { uid ->
                            val v = splitValues[uid]?.toDoubleOrNull() ?: 0.0
                            calculatedSplits[uid] = v
                            sum += v
                        }
                        // Validate exact sums
                        if (kotlin.math.abs(sum - amount) > 0.01) {
                            errorMessage = "Exact amounts must sum up to the total (₹$amount)"
                            return@Button
                        }
                    } else if (splitType == "PERCENTAGE") {
                        var sumPct = 0.0
                        includedMembers.forEach { uid ->
                            val pct = splitValues[uid]?.toDoubleOrNull() ?: 0.0
                            calculatedSplits[uid] = (pct / 100.0) * amount
                            sumPct += pct
                        }
                        // Validate percentages sum to 100
                        if (kotlin.math.abs(sumPct - 100.0) > 0.01) {
                            errorMessage = "Percentages must sum to 100%"
                            return@Button
                        }
                    }
                    
                    isLoading = true
                    errorMessage = null
                    
                    val docId = expenseId ?: UUID.randomUUID().toString()
                    val newExpense = Expense(
                        id = docId,
                        groupId = groupId,
                        title = title.trim(),
                        amount = amount,
                        paidBy = selectedPayerUid,
                        category = selectedCategory,
                        splitType = splitType,
                        splitAmong = includedMembers.toList(),
                        splits = calculatedSplits,
                        timestamp = if (expenseId != null) System.currentTimeMillis() else System.currentTimeMillis()
                    )
                    
                    db.collection("expenses").document(docId).set(newExpense)
                        .addOnSuccessListener {
                            isLoading = false
                            onNavigateBack()
                        }
                        .addOnFailureListener {
                            isLoading = false
                            errorMessage = "Failed to save expense"
                        }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                } else {
                    Text("Save Expense", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
