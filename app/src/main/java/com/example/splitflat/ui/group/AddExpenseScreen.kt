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
import com.example.splitflat.network.FrankfurterApi
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.util.Locale
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
    
    val categories = listOf("🍔 Food", "🚗 Transport", "🏠 Housing", "🛒 Groceries", "🎉 Fun", "📝 Other")
    
    // Split state maps User ID to the input value (exact amount or percentage)
    var splitValues by remember { mutableStateOf(mapOf<String, String>()) }
    
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    var group by remember { mutableStateOf<Group?>(null) }
    var members by remember { mutableStateOf<List<User>>(emptyList()) }
    val scope = rememberCoroutineScope()
    
    // Multi-Currency State
    val currencies = listOf("INR", "USD", "EUR", "GBP", "AUD", "CAD")
    var targetCurrency by remember { mutableStateOf("INR") }
    var exchangeRates by remember { mutableStateOf<Map<String, Double>?>(null) }
    var currencyDropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(groupId) {
        db.collection("groups").document(groupId).get().addOnSuccessListener { snapshot ->
            val fetchedGroup = snapshot.toObject(Group::class.java)
            group = fetchedGroup
            if (fetchedGroup != null && fetchedGroup.members.isNotEmpty()) {
                db.collection("users").whereIn("uid", fetchedGroup.members).get().addOnSuccessListener { usersSnapshot ->
                    members = usersSnapshot.documents.mapNotNull { it.toObject(User::class.java) }
                }
                
                if (fetchedGroup.enableMultiCurrency) {
                    scope.launch {
                        try {
                            val api = FrankfurterApi.create()
                            val response = api.getLatestRates("INR", "USD,EUR,GBP,AUD,CAD")
                            exchangeRates = response.rates
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
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
                    
                    if (expense.targetCurrency != null) {
                        targetCurrency = expense.targetCurrency
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
            
            if (group?.enableMultiCurrency == true) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExposedDropdownMenuBox(
                        expanded = currencyDropdownExpanded,
                        onExpandedChange = { currencyDropdownExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = targetCurrency,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Foreign Currency") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyDropdownExpanded)
                            },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier.menuAnchor(),
                            shape = RoundedCornerShape(16.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = currencyDropdownExpanded,
                            onDismissRequest = { currencyDropdownExpanded = false }
                        ) {
                            currencies.forEach { selectionOption ->
                                DropdownMenuItem(
                                    text = { Text(selectionOption) },
                                    onClick = {
                                        targetCurrency = selectionOption
                                        currencyDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    if (targetCurrency != "INR") {
                        val amount = amountText.toDoubleOrNull() ?: 0.0
                        val rate = exchangeRates?.get(targetCurrency) ?: 0.0
                        val equivalent = amount * rate
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Equivalent", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Text(
                                text = "${String.format(Locale.US, "%.2f", equivalent)} $targetCurrency", 
                                fontWeight = FontWeight.Bold, 
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
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

            // Dynamic Split UI
            AnimatedVisibility(visible = splitType == "EQUAL") {
                Text(
                    text = "Paid by you and split equally among all ${members.size} members.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.padding(16.dp)
                )
            }
            
            AnimatedVisibility(visible = splitType != "EQUAL") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    members.forEach { user ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(user.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
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
                    
                    val calculatedSplits = mutableMapOf<String, Double>()
                    
                    if (splitType == "EQUAL") {
                        val amountPerPerson = amount / members.size
                        members.forEach { calculatedSplits[it.uid] = amountPerPerson }
                    } else if (splitType == "EXACT") {
                        var sum = 0.0
                        members.forEach { user ->
                            val v = splitValues[user.uid]?.toDoubleOrNull() ?: 0.0
                            calculatedSplits[user.uid] = v
                            sum += v
                        }
                        // Validate exact sums
                        if (kotlin.math.abs(sum - amount) > 0.01) {
                            errorMessage = "Exact amounts must sum up to the total (₹$amount)"
                            return@Button
                        }
                    } else if (splitType == "PERCENTAGE") {
                        var sumPct = 0.0
                        members.forEach { user ->
                            val pct = splitValues[user.uid]?.toDoubleOrNull() ?: 0.0
                            calculatedSplits[user.uid] = (pct / 100.0) * amount
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
                    
                    val rate = if (targetCurrency != "INR") exchangeRates?.get(targetCurrency) else null
                    val tAmount = if (rate != null) amount * rate else null
                    val tCurr = if (rate != null) targetCurrency else null

                    val docId = expenseId ?: UUID.randomUUID().toString()
                    val newExpense = Expense(
                        id = docId,
                        groupId = groupId,
                        title = title.trim(),
                        amount = amount,
                        paidBy = currentUserUid,
                        category = selectedCategory,
                        splitType = splitType,
                        splitAmong = group!!.members, // Keep for legacy fields
                        splits = calculatedSplits,
                        timestamp = if (expenseId != null) System.currentTimeMillis() else System.currentTimeMillis(),
                        targetCurrency = tCurr,
                        targetAmount = tAmount
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
