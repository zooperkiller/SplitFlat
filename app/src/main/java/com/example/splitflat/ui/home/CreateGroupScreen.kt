package com.example.splitflat.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.splitflat.model.Group
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(
    onGroupCreated: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    var inviteEmail by remember { mutableStateOf("") }
    var membersList by remember { mutableStateOf(listOf<String>()) } // Storing emails temporarily for UI
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val currentUserUid = auth.currentUser?.uid ?: return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create New Group") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it },
                label = { Text("Group Name") },
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))
            
            // Add Member Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inviteEmail,
                    onValueChange = { inviteEmail = it },
                    label = { Text("Invite by Email") },
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val email = inviteEmail.trim()
                        if (email.isNotEmpty() && !membersList.contains(email)) {
                            membersList = membersList + email
                            inviteEmail = ""
                        }
                    },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Add")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // Show Added Members
            if (membersList.isNotEmpty()) {
                Text("Members to invite:", fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
                membersList.forEach { email ->
                    Text(text = "- $email", modifier = Modifier.align(Alignment.Start).padding(vertical = 4.dp))
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (errorMessage != null) {
                Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
            }

            Button(
                onClick = {
                    if (groupName.isBlank()) {
                        errorMessage = "Group name cannot be empty"
                        return@Button
                    }
                    
                    isLoading = true
                    errorMessage = null
                    
                    // First, we need to convert the invited emails to UIDs.
                    // If they don't exist, they can't be invited in this basic MVP.
                    val groupUidList = mutableListOf(currentUserUid) // Creator is a member
                    val groupId = UUID.randomUUID().toString()

                    if (membersList.isEmpty()) {
                        // Create group with just the creator
                        val newGroup = Group(groupId, groupName, currentUserUid, groupUidList)
                        db.collection("groups").document(groupId).set(newGroup)
                            .addOnSuccessListener {
                                isLoading = false
                                onGroupCreated()
                            }
                            .addOnFailureListener {
                                isLoading = false
                                errorMessage = "Failed to create group"
                            }
                    } else {
                        // Query users by email to find their UIDs
                        db.collection("users")
                            .whereIn("email", membersList)
                            .get()
                            .addOnSuccessListener { documents ->
                                for (document in documents) {
                                    document.getString("uid")?.let { groupUidList.add(it) }
                                }
                                
                                val newGroup = Group(groupId, groupName, currentUserUid, groupUidList)
                                db.collection("groups").document(groupId).set(newGroup)
                                    .addOnSuccessListener {
                                        isLoading = false
                                        onGroupCreated()
                                    }
                                    .addOnFailureListener {
                                        isLoading = false
                                        errorMessage = "Failed to create group"
                                    }
                            }
                            .addOnFailureListener {
                                isLoading = false
                                errorMessage = "Failed to find invited users"
                            }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                } else {
                    Text("Create Group", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            TextButton(onClick = onNavigateBack, enabled = !isLoading) {
                Text("Cancel")
            }
        }
    }
}
