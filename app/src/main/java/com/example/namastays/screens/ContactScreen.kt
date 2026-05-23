package com.example.namastays.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.namastays.data.EmergencyContactEntity
import com.example.namastays.viewmodel.SafetyViewModel

// ─────────────────────────────────────────────────────────────────────────────
//  Palette
// ─────────────────────────────────────────────────────────────────────────────

private val EcPageBg      = Color(0xFFF7F8FA)
private val EcCardBg      = Color(0xFFFFFFFF)
private val EcCardBorder  = Color(0xFFE5E7EB)
private val EcTextPri     = Color(0xFF111827)
private val EcTextSec     = Color(0xFF6B7280)
private val EcTextHint    = Color(0xFF9CA3AF)
private val EcGreen       = Color(0xFF22C55E)
private val EcRed         = Color(0xFFEF4444)
private val EcAccent      = Color(0xFF6366F1)
private val EcAccentBg    = Color(0xFFEEF2FF)
private val EcAvatarTints = listOf(
    Color(0xFF6366F1), Color(0xFF22C55E), Color(0xFFEF4444),
    Color(0xFFF97316), Color(0xFF0EA5E9), Color(0xFFEC4899),
)

private fun avatarTint(seed: String) = EcAvatarTints[
    seed.hashCode().mod(EcAvatarTints.size).let { if (it < 0) it + EcAvatarTints.size else it }
]

// ─────────────────────────────────────────────────────────────────────────────
//  Local bodies data
// ─────────────────────────────────────────────────────────────────────────────

data class LocalBody(val name: String, val number: String, val icon: ImageVector)

val localBodies = listOf(
    LocalBody("Mountain Rescue",   "1800-180-1234", Icons.Outlined.Landscape),
    LocalBody("Police",            "100",           Icons.Outlined.LocalPolice),
    LocalBody("Ambulance",         "108",           Icons.Outlined.LocalHospital),
    LocalBody("Fire Brigade",      "101",           Icons.Outlined.LocalFireDepartment),
    LocalBody("Disaster Helpline", "1077",          Icons.Outlined.Warning),
)

// ─────────────────────────────────────────────────────────────────────────────
//  Emergency Contacts Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyContactsScreen(
    navController: NavController,
    vm: SafetyViewModel = viewModel(),
) {
    val context  = LocalContext.current
    val contacts by vm.contacts.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf<EmergencyContactEntity?>(null) }
    var localBodiesExpanded by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Emergency Contacts",
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 18.sp,
                        color      = EcTextPri,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = EcTextPri)
                    }
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(EcGreen)
                            .clickable { navController.navigate(SafetyRoutes.ADD_CONTACT) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Outlined.PersonAdd,
                                contentDescription = "Add Contact",
                                tint     = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                "Add",
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.Bold,
                                fontSize   = 13.sp,
                                color      = Color.White,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EcCardBg),
            )
        },
        containerColor = EcPageBg,
    ) { innerPadding ->

        LazyColumn(
            modifier       = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            // ── Saved contacts ───────────────────────────────────────────────
            item {
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text(
                        "MY CONTACTS",
                        fontFamily    = PlusJakartaSans,
                        fontWeight    = FontWeight.Bold,
                        fontSize      = 11.sp,
                        color         = EcTextSec,
                        letterSpacing = 1.sp,
                    )
                    if (contacts.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(EcAccentBg)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                "${contacts.size}",
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.Bold,
                                fontSize   = 12.sp,
                                color      = EcAccent,
                            )
                        }
                    }
                }
            }

            if (contacts.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(EcCardBg)
                            .border(1.dp, EcCardBorder, RoundedCornerShape(16.dp))
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(EcAccentBg),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.ContactPhone,
                                contentDescription = null,
                                tint     = EcAccent,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Text(
                            "No contacts saved yet",
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 14.sp,
                            color      = EcTextPri,
                        )
                        Text(
                            "Tap Add to save your first emergency contact",
                            fontFamily = PlusJakartaSans,
                            fontSize   = 12.sp,
                            color      = EcTextHint,
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(EcGreen)
                                .clickable { navController.navigate(SafetyRoutes.ADD_CONTACT) }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                        ) {
                            Text(
                                "Add Contact",
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.Bold,
                                fontSize   = 13.sp,
                                color      = Color.White,
                            )
                        }
                    }
                }
            } else {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(EcCardBg)
                            .border(1.dp, EcCardBorder, RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp),
                    ) {
                        contacts.forEachIndexed { index, contact ->
                            if (index > 0) HorizontalDivider(color = EcCardBorder, thickness = 0.5.dp)
                            EcContactRow(
                                contact  = contact,
                                onCall   = { dialNumber(context, contact.phone) },
                                onDelete = { showDeleteDialog = contact },
                            )
                        }
                    }
                }
            }

            // ── Local emergency bodies ────────────────────────────────────────
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(EcCardBg)
                        .border(1.dp, EcCardBorder, RoundedCornerShape(16.dp)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { localBodiesExpanded = !localBodiesExpanded }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint     = EcRed,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            "Local Emergency Bodies",
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 15.sp,
                            color      = EcTextPri,
                            modifier   = Modifier.weight(1f),
                        )
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(EcRed.copy(alpha = 0.1f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                "${localBodies.size}",
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.Bold,
                                fontSize   = 12.sp,
                                color      = EcRed,
                            )
                        }
                        Icon(
                            if (localBodiesExpanded) Icons.Outlined.KeyboardArrowUp
                            else Icons.Outlined.KeyboardArrowDown,
                            contentDescription = null,
                            tint     = EcTextHint,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    AnimatedVisibility(
                        visible = localBodiesExpanded,
                        enter   = expandVertically(),
                        exit    = shrinkVertically(),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp),
                        ) {
                            HorizontalDivider(color = EcCardBorder, thickness = 0.5.dp)
                            localBodies.forEachIndexed { index, body ->
                                if (index > 0) HorizontalDivider(color = EcCardBorder, thickness = 0.5.dp)
                                LocalBodyRow(
                                    body   = body,
                                    onCall = { dialNumber(context, body.number) },
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    // ── Delete confirmation dialog ────────────────────────────────────────────
    showDeleteDialog?.let { contact ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            icon = {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    tint = EcRed,
                )
            },
            title = {
                Text(
                    "Remove Contact",
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Bold,
                    color      = EcTextPri,
                )
            },
            text = {
                Text(
                    "Remove ${contact.name} from your emergency contacts?",
                    fontFamily = PlusJakartaSans,
                    fontSize   = 14.sp,
                    color      = EcTextSec,
                )
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(EcRed)
                        .clickable {
                            vm.deleteContact(contact)
                            showDeleteDialog = null
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(
                        "Remove",
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 14.sp,
                        color      = Color.White,
                    )
                }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(EcCardBorder)
                        .clickable { showDeleteDialog = null }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(
                        "Cancel",
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 14.sp,
                        color      = EcTextSec,
                    )
                }
            },
            containerColor    = EcCardBg,
            shape             = RoundedCornerShape(20.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Add Contact Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddContactScreen(
    navController: NavController,
    vm: SafetyViewModel = viewModel(),
) {
    var name      by remember { mutableStateOf("") }
    var phone     by remember { mutableStateOf("") }
    var relation  by remember { mutableStateOf("") }
    var nameError  by remember { mutableStateOf(false) }
    var phoneError by remember { mutableStateOf(false) }

    val avatarLetter = if (name.isNotBlank()) name.first().uppercaseChar().toString() else "?"
    val tint         = if (name.isNotBlank()) avatarTint(name) else EcAccent

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Add Contact",
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 18.sp,
                        color      = EcTextPri,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = EcTextPri)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EcCardBg),
            )
        },
        containerColor = EcPageBg,
    ) { innerPadding ->

        LazyColumn(
            modifier       = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            // ── Live preview card ────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(EcCardBg)
                        .border(1.dp, EcCardBorder, RoundedCornerShape(16.dp))
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(tint.copy(alpha = 0.12f))
                            .border(2.dp, tint.copy(alpha = 0.35f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text       = avatarLetter,
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize   = 24.sp,
                            color      = tint,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text       = name.ifBlank { "Full Name" },
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 16.sp,
                            color      = if (name.isBlank()) EcTextHint else EcTextPri,
                        )
                        Text(
                            text       = phone.ifBlank { "Phone number" },
                            fontFamily = PlusJakartaSans,
                            fontSize   = 13.sp,
                            color      = if (phone.isBlank()) EcTextHint else EcTextSec,
                        )
                        if (relation.isNotBlank()) {
                            Text(
                                text       = relation,
                                fontFamily = PlusJakartaSans,
                                fontSize   = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color      = tint,
                            )
                        }
                    }
                }
            }

            // ── Form card ────────────────────────────────────────────────────
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(EcCardBg)
                        .border(1.dp, EcCardBorder, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 4.dp),
                    ) {
                        Icon(
                            Icons.Outlined.PersonAdd,
                            contentDescription = null,
                            tint     = EcAccent,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            "Contact Details",
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 14.sp,
                            color      = EcTextPri,
                        )
                    }

                    EcTextField(
                        value         = name,
                        onValueChange = { name = it; nameError = false },
                        label         = "Full Name",
                        icon          = Icons.Outlined.Person,
                        isError       = nameError,
                        errorMessage  = "Name cannot be empty",
                    )
                    EcTextField(
                        value         = phone,
                        onValueChange = { phone = it; phoneError = false },
                        label         = "Phone Number",
                        icon          = Icons.Outlined.Phone,
                        keyboardType  = KeyboardType.Phone,
                        isError       = phoneError,
                        errorMessage  = "Enter a valid phone number",
                    )
                    EcTextField(
                        value         = relation,
                        onValueChange = { relation = it },
                        label         = "Relation (e.g. Father, Friend)",
                        icon          = Icons.Outlined.People,
                    )
                }
            }

            // ── Save button ──────────────────────────────────────────────────
            item {
                Button(
                    onClick = {
                        nameError  = name.isBlank()
                        phoneError = phone.length < 7
                        if (!nameError && !phoneError) {
                            vm.addContact(name, phone, relation)
                            navController.popBackStack()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = EcGreen),
                ) {
                    Icon(
                        Icons.Outlined.Save,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Save Contact",
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 16.sp,
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Contact row (inside white card)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EcContactRow(
    contact: EmergencyContactEntity,
    onCall: () -> Unit,
    onDelete: () -> Unit,
) {
    val tint     = avatarTint(contact.name)
    val initials = contact.name.split(" ")
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .take(2).joinToString("").ifEmpty { "?" }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text       = initials,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.ExtraBold,
                fontSize   = 15.sp,
                color      = tint,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text       = contact.name,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Bold,
                fontSize   = 14.sp,
                color      = EcTextPri,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
            Text(
                text       = contact.phone,
                fontFamily = PlusJakartaSans,
                fontSize   = 13.sp,
                color      = EcTextSec,
            )
            if (contact.relation.isNotBlank()) {
                Text(
                    text       = contact.relation,
                    fontFamily = PlusJakartaSans,
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = tint,
                )
            }
        }
        // Call
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(EcGreen)
                .clickable(onClick = onCall),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Call,
                contentDescription = "Call ${contact.name}",
                tint     = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
        // Delete
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(EcRed.copy(alpha = 0.1f))
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.DeleteOutline,
                contentDescription = "Remove ${contact.name}",
                tint     = EcRed,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Local body row (inside white card)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LocalBodyRow(body: LocalBody, onCall: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(EcRed.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                body.icon,
                contentDescription = null,
                tint     = EcRed,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text       = body.name,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Bold,
                fontSize   = 14.sp,
                color      = EcTextPri,
            )
            Text(
                text       = body.number,
                fontFamily = PlusJakartaSans,
                fontSize   = 13.sp,
                color      = EcGreen,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(EcGreen)
                .clickable(onClick = onCall),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Call,
                contentDescription = "Call ${body.name}",
                tint     = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Text field
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun EcTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false,
    errorMessage: String = "",
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        modifier      = Modifier.fillMaxWidth(),
        label = {
            Text(label, fontFamily = PlusJakartaSans, fontSize = 13.sp)
        },
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
                tint     = if (isError) EcRed else EcTextHint,
                modifier = Modifier.size(18.dp),
            )
        },
        supportingText = if (isError) {
            {
                Text(
                    errorMessage,
                    fontFamily = PlusJakartaSans,
                    fontSize   = 11.sp,
                    color      = EcRed,
                )
            }
        } else null,
        isError         = isError,
        singleLine      = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape           = RoundedCornerShape(12.dp),
        colors          = OutlinedTextFieldDefaults.colors(
            focusedBorderColor    = EcAccent,
            unfocusedBorderColor  = EcCardBorder,
            focusedLabelColor     = EcAccent,
            unfocusedLabelColor   = EcTextHint,
            cursorColor           = EcAccent,
            focusedTextColor      = EcTextPri,
            unfocusedTextColor    = EcTextPri,
            errorBorderColor      = EcRed,
            errorLabelColor       = EcRed,
            errorLeadingIconColor = EcRed,
        ),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Dial helper
// ─────────────────────────────────────────────────────────────────────────────

fun dialNumber(context: Context, number: String) {
    context.startActivity(
        Intent(Intent.ACTION_DIAL, Uri.parse("tel:${number.replace(" ", "").replace("-", "")}"))
    )
}