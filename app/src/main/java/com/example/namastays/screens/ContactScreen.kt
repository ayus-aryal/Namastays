package com.example.namastays.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.namastays.data.EmergencyContactEntity
import com.example.namastays.viewmodel.SafetyViewModel

// ─── Emergency Contacts Screen ────────────────────────────────────────────────
@Composable
fun EmergencyContactsScreen(
    navController: NavController,
    vm: SafetyViewModel = viewModel()
) {
    val context = LocalContext.current
    val contacts by vm.contacts.collectAsState()
    var showDeleteDialog by remember { mutableStateOf<EmergencyContactEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                "Emergency Contacts",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { navController.navigate(SafetyRoutes.ADD_CONTACT) }) {
                Icon(Icons.Default.PersonAdd, contentDescription = "Add", tint = Color(0xFF4CAF50))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (contacts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.PersonOff,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "No emergency contacts added",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 14.sp
                    )
                    TextButton(onClick = { navController.navigate(SafetyRoutes.ADD_CONTACT) }) {
                        Text("+ Add Contact", color = Color(0xFF4CAF50))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(contacts, key = { it.id }) { contact ->
                    ContactCard(
                        contact = contact,
                        onCall = { dialNumber(context, contact.phone) },
                        onDelete = { showDeleteDialog = contact }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Local Emergency Bodies",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            localBodies.forEach { body ->
                LocalBodyRow(body = body, onCall = { dialNumber(context, body.number) })
            }
        }
    }

    showDeleteDialog?.let { contact ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Remove Contact") },
            text = { Text("Remove ${contact.name} from emergency contacts?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteContact(contact)
                    showDeleteDialog = null
                }) { Text("Remove", color = Color(0xFFFF5252)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("Cancel") }
            },
            containerColor = Color(0xFF16213E),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.8f)
        )
    }
}

// ─── Contact Card ─────────────────────────────────────────────────────────────
@Composable
fun ContactCard(
    contact: EmergencyContactEntity,
    onCall: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF16213E))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color(0xFFFF5722).copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = contact.name.first().uppercaseChar().toString(),
                color = Color(0xFFFF5722),
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(contact.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(contact.phone, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            Text(contact.relation, color = Color(0xFFFF5722), fontSize = 11.sp)
        }
        IconButton(onClick = onCall, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.Call, contentDescription = "Call", tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
        }
    }
}

// ─── Add Contact Screen ───────────────────────────────────────────────────────
@Composable
fun AddContactScreen(
    navController: NavController,
    vm: SafetyViewModel = viewModel()
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }
    var phoneError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenHeader("Add Emergency Contact", onBack = { navController.popBackStack() })

        Box(
            modifier = Modifier.fillMaxWidth().height(90.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF5722).copy(alpha = 0.2f))
                    .border(2.dp, Color(0xFFFF5722).copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (name.isNotBlank()) name.first().uppercaseChar().toString() else "?",
                    color = Color(0xFFFF5722),
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp
                )
            }
        }

        SafetyTextField(
            value = name,
            onValueChange = { name = it; nameError = false },
            label = "Full Name",
            icon = Icons.Default.Person,
            isError = nameError,
            errorMessage = "Name cannot be empty"
        )
        SafetyTextField(
            value = phone,
            onValueChange = { phone = it; phoneError = false },
            label = "Phone Number",
            icon = Icons.Default.Phone,
            keyboardType = KeyboardType.Phone,
            isError = phoneError,
            errorMessage = "Enter a valid phone number"
        )
        SafetyTextField(
            value = relation,
            onValueChange = { relation = it },
            label = "Relation (e.g. Father, Friend)",
            icon = Icons.Default.People
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                nameError = name.isBlank()
                phoneError = phone.length < 7
                if (!nameError && !phoneError) {
                    vm.addContact(name, phone, relation)
                    navController.popBackStack()
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save Contact", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

// ─── Text Field ───────────────────────────────────────────────────────────────
@Composable
fun SafetyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false,
    errorMessage: String = ""
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        isError = isError,
        supportingText = if (isError) { { Text(errorMessage, color = Color(0xFFFF5252)) } } else null,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF4CAF50),
            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
            focusedLabelColor = Color(0xFF4CAF50),
            unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
            cursorColor = Color(0xFF4CAF50),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            errorBorderColor = Color(0xFFFF5252)
        )
    )
}

// ─── Local Bodies ─────────────────────────────────────────────────────────────
data class LocalBody(val name: String, val number: String, val icon: String)

val localBodies = listOf(
    LocalBody("Mountain Rescue",   "1800-180-1234", "🏔"),
    LocalBody("Police",            "100",           "🚔"),
    LocalBody("Ambulance",         "108",           "🚑"),
    LocalBody("Fire Brigade",      "101",           "🔥"),
    LocalBody("Disaster Helpline", "1077",          "⚠")
)

@Composable
fun LocalBodyRow(body: LocalBody, onCall: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF16213E))
            .clickable(onClick = onCall)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(body.icon, fontSize = 22.sp)
        Column(modifier = Modifier.weight(1f)) {
            Text(body.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(body.number, color = Color(0xFF4CAF50), fontSize = 12.sp)
        }
        Icon(Icons.Default.Call, contentDescription = "Call", tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
    }
}

// ─── Dial helper ──────────────────────────────────────────────────────────────
fun dialNumber(context: Context, number: String) {
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
    context.startActivity(intent)
}