package com.example.splitflat.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.splitflat.model.Group
import com.example.splitflat.model.User
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSignOut: () -> Unit,
    onNavigateToCreateGroup: () -> Unit,
    onNavigateToGroupDetail: (String) -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val currentUserUid = auth.currentUser?.uid ?: return
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var groups by remember { mutableStateOf<List<Group>>(emptyList()) }
    var userNamesMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }

    // Tabs
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Groups, 1 = Friends

    // Dialogs state
    var showAboutDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    
    // Add Friend Dialog State
    var showAddFriendDialog by remember { mutableStateOf(false) }
    var friendEmail by remember { mutableStateOf("") }
    var isAddingFriend by remember { mutableStateOf(false) }
    var addFriendError by remember { mutableStateOf<String?>(null) }

    fun loadData() {
        db.collection("groups")
            .whereArrayContains("members", currentUserUid)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot != null) {
                    val fetchedGroups = snapshot.documents.mapNotNull { it.toObject(Group::class.java) }
                    groups = fetchedGroups

                    // Gather other UIDs for 1-on-1 display names
                    val otherUids = fetchedGroups.flatMap { it.members }.distinct().filter { it != currentUserUid }
                    if (otherUids.isNotEmpty()) {
                        db.collection("users")
                            .whereIn("uid", otherUids)
                            .get()
                            .addOnSuccessListener { usersSnapshot ->
                                val newMap = usersSnapshot.documents.mapNotNull { it.toObject(User::class.java) }
                                    .associate { it.uid to it.name }
                                userNamesMap = newMap
                                isLoading = false
                            }
                            .addOnFailureListener {
                                isLoading = false
                            }
                    } else {
                        isLoading = false
                    }
                } else {
                    isLoading = false
                }
            }
            .addOnFailureListener {
                isLoading = false
            }
    }

    // Set up real-time listener
    LaunchedEffect(currentUserUid) {
        db.collection("groups")
            .whereArrayContains("members", currentUserUid)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    isLoading = false
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val fetchedGroups = snapshot.documents.mapNotNull { it.toObject(Group::class.java) }
                    groups = fetchedGroups

                    val otherUids = fetchedGroups.flatMap { it.members }.distinct().filter { it != currentUserUid }
                    if (otherUids.isNotEmpty()) {
                        db.collection("users")
                            .whereIn("uid", otherUids)
                            .get()
                            .addOnSuccessListener { usersSnapshot ->
                                val newMap = usersSnapshot.documents.mapNotNull { it.toObject(User::class.java) }
                                    .associate { it.uid to it.name }
                                userNamesMap = newMap
                                isLoading = false
                            }
                            .addOnFailureListener {
                                isLoading = false
                            }
                    } else {
                        isLoading = false
                    }
                }
            }
    }

    // Filter groups by tab selection
    val displayedGroups = remember(groups, selectedTab) {
        if (selectedTab == 0) {
            groups.filter { !it.isOneOnOne }
        } else {
            groups.filter { it.isOneOnOne }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SplitFlat") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    // QR Scanner button
                    IconButton(onClick = {
                        val scanner = GmsBarcodeScanning.getClient(context)
                        scanner.startScan()
                            .addOnSuccessListener { barcode: Barcode ->
                                val scannedGroupId = barcode.rawValue
                                if (!scannedGroupId.isNullOrBlank()) {
                                    isLoading = true
                                    db.collection("groups").document(scannedGroupId).get()
                                        .addOnSuccessListener { doc ->
                                            val grp = doc.toObject(Group::class.java)
                                            if (grp != null) {
                                                val currentMembers = grp.members
                                                if (!currentMembers.contains(currentUserUid)) {
                                                    val updatedMembers = currentMembers + currentUserUid
                                                    db.collection("groups").document(scannedGroupId)
                                                        .update("members", updatedMembers)
                                                        .addOnSuccessListener {
                                                            isLoading = false
                                                            onNavigateToGroupDetail(scannedGroupId)
                                                        }
                                                        .addOnFailureListener {
                                                            isLoading = false
                                                        }
                                                } else {
                                                    isLoading = false
                                                    onNavigateToGroupDetail(scannedGroupId)
                                                }
                                            } else {
                                                isLoading = false
                                            }
                                        }
                                        .addOnFailureListener {
                                            isLoading = false
                                        }
                                }
                            }
                    }) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan Group QR")
                    }

                    // Top Bar Menu Dropdown
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Options Menu")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("About App") },
                                onClick = {
                                    showMenu = false
                                    showAboutDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Sign Out", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    auth.signOut()
                                    onSignOut()
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (selectedTab == 0) {
                        onNavigateToCreateGroup()
                    } else {
                        showAddFriendDialog = true
                        friendEmail = ""
                        addFriendError = null
                        isAddingFriend = false
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Text("+", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Groups") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Friends") }
                )
            }

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    coroutineScope.launch {
                        isRefreshing = true
                        loadData()
                        delay(500)
                        isRefreshing = false
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (isLoading && !isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    } else if (displayedGroups.isEmpty()) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (selectedTab == 0) "You don't have any groups yet." else "You haven't added any friends yet.",
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (selectedTab == 0) "Tap the + button to create one!" else "Tap the + button to add a friend!",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(displayedGroups) { group ->
                                val displayName = if (group.isOneOnOne) {
                                    val otherMember = group.members.firstOrNull { it != currentUserUid }
                                    userNamesMap[otherMember] ?: "Friend Ledger"
                                } else {
                                    group.name
                                }

                                GroupCard(
                                    group = group,
                                    friendName = if (group.isOneOnOne) displayName else null,
                                    onClick = { onNavigateToGroupDetail(group.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // About Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About SplitFlat") },
            text = {
                Column {
                    Text("Developer: Anish Thakurta", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Made with: Native Kotlin, Jetpack Compose, Firebase")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Version: 1.0.0")
                }
            },
            confirmButton = {
                Button(onClick = { showAboutDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    // Add Friend Dialog
    if (showAddFriendDialog) {
        AlertDialog(
            onDismissRequest = { if (!isAddingFriend) showAddFriendDialog = false },
            title = { Text("Add Friend") },
            text = {
                Column {
                    Text("Enter your friend's registered email address to settle 1-on-1 balances.")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = friendEmail,
                        onValueChange = { friendEmail = it },
                        label = { Text("Email Address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (addFriendError != null) {
                        Text(
                            text = addFriendError!!,
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
                        val email = friendEmail.trim()
                        if (email.isBlank()) {
                            addFriendError = "Email cannot be empty"
                            return@Button
                        }
                        if (email == auth.currentUser?.email) {
                            addFriendError = "You cannot add yourself as a friend."
                            return@Button
                        }
                        
                        isAddingFriend = true
                        addFriendError = null

                        db.collection("users")
                            .whereEqualTo("email", email)
                            .get()
                            .addOnSuccessListener { snapshot ->
                                if (snapshot.isEmpty) {
                                    addFriendError = "User not found!"
                                    isAddingFriend = false
                                } else {
                                    val friendUser = snapshot.documents[0].toObject(User::class.java)
                                    if (friendUser != null) {
                                        // Check if already friends
                                        val isAlreadyFriend = groups.any { 
                                            it.isOneOnOne && it.members.contains(friendUser.uid) 
                                        }
                                        if (isAlreadyFriend) {
                                            addFriendError = "You are already friends!"
                                            isAddingFriend = false
                                        } else {
                                            val newGroupId = UUID.randomUUID().toString()
                                            val newGroup = Group(
                                                id = newGroupId,
                                                name = "Friendship",
                                                createdBy = currentUserUid,
                                                members = listOf(currentUserUid, friendUser.uid),
                                                simplifyDebts = false,
                                                isOneOnOne = true,
                                                timestamp = System.currentTimeMillis()
                                            )
                                            db.collection("groups").document(newGroupId).set(newGroup)
                                                .addOnSuccessListener {
                                                    isAddingFriend = false
                                                    showAddFriendDialog = false
                                                }
                                                .addOnFailureListener {
                                                    addFriendError = "Failed to add friend."
                                                    isAddingFriend = false
                                                }
                                        }
                                    } else {
                                        isAddingFriend = false
                                    }
                                }
                            }
                            .addOnFailureListener {
                                addFriendError = "Error searching for user."
                                isAddingFriend = false
                            }
                    },
                    enabled = !isAddingFriend
                ) {
                    if (isAddingFriend) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Add")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddFriendDialog = false },
                    enabled = !isAddingFriend
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun GroupCard(
    group: Group,
    friendName: String? = null,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = friendName ?: group.name,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (group.isOneOnOne) "1-on-1 Ledger" else "${group.members.size} members",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

