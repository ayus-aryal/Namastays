package com.example.namastays.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.namastays.data.EmergencyContactEntity
import com.example.namastays.viewmodel.SafetyViewModel
import kotlinx.coroutines.launch

// ─── Palette ──────────────────────────────────────────────────────────────────
private val EcPageBg      = Color(0xFFF7F8FA)
private val EcCardBg      = Color(0xFFFFFFFF)
private val EcCardBorder  = Color(0xFFE5E7EB)
private val EcTextPri     = Color(0xFF111827)
private val EcTextSec     = Color(0xFF6B7280)
private val EcTextHint    = Color(0xFF9CA3AF)
private val EcGreen       = Color(0xFF1B6B4A)
private val EcRed         = Color(0xFFEF4444)
private val EcAccent      = Color(0xFF6366F1)
private val EcAccentBg    = Color(0xFFEEF2FF)
private val EcNavy        = Color(0xFF1E3A5F)
private val EcAvatarTints = listOf(
    Color(0xFF6366F1), Color(0xFF22C55E), Color(0xFFEF4444),
    Color(0xFFF97316), Color(0xFF0EA5E9), Color(0xFFEC4899),
)

private fun avatarTint(seed: String) = EcAvatarTints[
    seed.hashCode().mod(EcAvatarTints.size).let { if (it < 0) it + EcAvatarTints.size else it }
]

private const val MAX_CONTACTS = 5

// ─── Local bodies data ────────────────────────────────────────────────────────
data class LocalBody(
    val name: String,
    val number: String,
    val icon: ImageVector,
    val avatarColor: Color,
    val iconTint: Color,
)

val localBodies = listOf(
    LocalBody("Mountain Rescue",   "112",  Icons.Outlined.Landscape,           Color(0xFFFFE4E4), Color(0xFFE53935)),
    LocalBody("Tourist Police",    "114",  Icons.Outlined.LocalPolice,         Color(0xFFE8EEFF), Color(0xFF3B82F6)),
    LocalBody("Ambulance",         "108",  Icons.Outlined.LocalHospital,       Color(0xFFE6FAF0), Color(0xFF22C55E)),
    LocalBody("Fire Brigade",      "101",  Icons.Outlined.LocalFireDepartment, Color(0xFFFFF3E0), Color(0xFFF97316)),
    LocalBody("Disaster Helpline", "1077", Icons.Outlined.SupportAgent,        Color(0xFFF3E8FF), Color(0xFF9333EA)),
)

// ─── Emergency Contacts Screen ────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyContactsScreen(
    navController: NavController,
    vm: SafetyViewModel = viewModel(),
) {
    val context  = LocalContext.current
    val contacts by vm.contacts.collectAsStateWithLifecycle()
    val scope    = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showDeleteDialog    by remember { mutableStateOf<EmergencyContactEntity?>(null) }
    var localBodiesExpanded by remember { mutableStateOf(true) }

    // Chevron rotation animation
    val chevronRotation by animateFloatAsState(
        targetValue = if (localBodiesExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 300, easing = EaseInOutCubic),
        label = "chevron"
    )

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData    = data,
                    containerColor  = Color(0xFF1F2937),
                    contentColor    = Color.White,
                    shape           = RoundedCornerShape(12.dp),
                    modifier        = Modifier.padding(16.dp),
                )
            }
        },
        containerColor = EcPageBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                // consume only top padding (status bar); ignore bottom so no gap appears
        ) {

            // ── Custom top bar ────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EcCardBg)
                    .padding(top = innerPadding.calculateTopPadding())
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = EcTextPri)
                }
                Text(
                    "Emergency Contacts",
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 18.sp,
                    color      = EcTextPri,
                )
                Row(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            if (contacts.size >= MAX_CONTACTS) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "Maximum $MAX_CONTACTS contacts allowed"
                                    )
                                }
                            } else {
                                navController.navigate(SafetyRoutes.ADD_CONTACT)
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, tint = EcGreen, modifier = Modifier.size(17.dp))
                    Text(
                        "Add",
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 14.sp,
                        color      = EcGreen,
                    )
                }
            }

            // ── Thin divider under top bar ────────────────────────────────────
            HorizontalDivider(color = EcCardBorder, thickness = 0.5.dp)

            LazyColumn(
                modifier            = Modifier.fillMaxSize(),
                contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {

                // ── Dark navy SOS info banner ─────────────────────────────────
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(EcNavy)
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment     = Alignment.Top,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.Info, null,
                                tint     = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Text(
                            "SOS will send your GPS location and critical medical info to all contacts listed below simultaneously.",
                            fontFamily = PlusJakartaSans,
                            fontSize   = 14.sp,
                            color      = Color.White,
                            lineHeight = 21.sp,
                            modifier   = Modifier.weight(1f),
                        )
                    }
                }

                // ── MY CONTACTS header ────────────────────────────────────────
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
                            fontSize      = 12.sp,
                            color         = EcTextSec,
                            letterSpacing = 1.sp,
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(EcCardBg)
                                .border(1.dp, EcCardBorder, RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                "${contacts.size}/$MAX_CONTACTS Saved",
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.SemiBold,
                                fontSize   = 12.sp,
                                color      = if (contacts.size >= MAX_CONTACTS) EcRed else EcTextSec,
                            )
                        }
                    }
                }

                // ── Empty state ───────────────────────────────────────────────
                if (contacts.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(EcCardBg)
                                .border(1.dp, EcCardBorder, RoundedCornerShape(16.dp))
                                .padding(vertical = 44.dp, horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(EcAccentBg),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Outlined.ContactPhone, null,
                                    tint     = EcAccent,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "No emergency contacts yet",
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.Bold,
                                fontSize   = 15.sp,
                                color      = EcTextPri,
                            )
                            Text(
                                "Add trusted contacts so they're alerted instantly when you trigger SOS.",
                                fontFamily = PlusJakartaSans,
                                fontSize   = 13.sp,
                                color      = EcTextSec,
                                lineHeight = 19.sp,
                                textAlign  = TextAlign.Center,
                            )
                            Spacer(Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(EcGreen)
                                    .clickable { navController.navigate(SafetyRoutes.ADD_CONTACT) }
                                    .padding(horizontal = 28.dp, vertical = 13.dp),
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment     = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Outlined.PersonAdd, null, tint = Color.White, modifier = Modifier.size(17.dp))
                                    Text(
                                        "Add First Contact",
                                        fontFamily = PlusJakartaSans,
                                        fontWeight = FontWeight.Bold,
                                        fontSize   = 14.sp,
                                        color      = Color.White,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // ── Individual contact cards ───────────────────────────────
                    items(contacts, key = { it.id }) { contact ->
                        EcContactCard(
                            contact  = contact,
                            onCall   = { dialNumber(context, contact.phone) },
                            onDelete = { showDeleteDialog = contact },
                        )
                    }

                    // ── Add Another / Limit reached button ────────────────────
                    item {
                        if (contacts.size < MAX_CONTACTS) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(EcCardBg)
                                    .border(1.5.dp, EcGreen.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                                    .clickable { navController.navigate(SafetyRoutes.ADD_CONTACT) }
                                    .padding(vertical = 15.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment     = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Outlined.PersonAdd, null, tint = EcGreen, modifier = Modifier.size(18.dp))
                                    Text(
                                        "Add Another Contact",
                                        fontFamily = PlusJakartaSans,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize   = 14.sp,
                                        color      = EcGreen,
                                    )
                                }
                            }
                        } else {
                            // Max reached notice
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(EcRed.copy(alpha = 0.07f))
                                    .border(1.dp, EcRed.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment     = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.Block, null, tint = EcRed, modifier = Modifier.size(16.dp))
                                Text(
                                    "Maximum of $MAX_CONTACTS contacts reached. Remove one to add another.",
                                    fontFamily = PlusJakartaSans,
                                    fontSize   = 12.sp,
                                    color      = EcRed,
                                    lineHeight = 18.sp,
                                )
                            }
                        }
                    }
                }

                // ── LOCAL EMERGENCY SERVICES ──────────────────────────────────
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(EcCardBg)
                            .border(1.dp, EcCardBorder, RoundedCornerShape(16.dp)),
                    ) {
                        // Header row — animated chevron
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { localBodiesExpanded = !localBodiesExpanded }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment     = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(EcRed.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Outlined.LocalPolice, null, tint = EcRed, modifier = Modifier.size(17.dp))
                                }
                                Column {
                                    Text(
                                        "LOCAL EMERGENCY SERVICES",
                                        fontFamily    = PlusJakartaSans,
                                        fontWeight    = FontWeight.Bold,
                                        fontSize      = 11.sp,
                                        color         = EcTextSec,
                                        letterSpacing = 0.5.sp,
                                    )
                                    Text(
                                        "${localBodies.size} services available",
                                        fontFamily = PlusJakartaSans,
                                        fontSize   = 11.sp,
                                        color      = EcTextHint,
                                    )
                                }
                            }
                            Icon(
                                Icons.Outlined.KeyboardArrowDown, null,
                                tint     = EcTextHint,
                                modifier = Modifier
                                    .size(22.dp)
                                    .rotate(chevronRotation),
                            )
                        }

                        // Animated expand/collapse
                        AnimatedVisibility(
                            visible = localBodiesExpanded,
                            enter   = expandVertically(
                                animationSpec = tween(300, easing = EaseOutCubic)
                            ) + fadeIn(animationSpec = tween(250)),
                            exit    = shrinkVertically(
                                animationSpec = tween(250, easing = EaseInCubic)
                            ) + fadeOut(animationSpec = tween(200)),
                        ) {
                            Column {
                                HorizontalDivider(color = EcCardBorder, thickness = 0.5.dp)
                                localBodies.forEachIndexed { index, body ->
                                    if (index > 0) HorizontalDivider(
                                        modifier  = Modifier.padding(horizontal = 16.dp),
                                        color     = EcCardBorder,
                                        thickness = 0.5.dp,
                                    )
                                    LocalBodyRow(body = body, onCall = { dialNumber(context, body.number) })
                                }
                            }
                        }
                    }
                }

                item { Spacer(Modifier.navigationBarsPadding()) }            }
        }
    }

    // ── Delete dialog ─────────────────────────────────────────────────────────
    showDeleteDialog?.let { contact ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(EcRed.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.DeleteOutline, null, tint = EcRed, modifier = Modifier.size(22.dp))
                }
            },
            title = {
                Text("Remove Contact", fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = EcTextPri)
            },
            text = {
                Text(
                    "Remove ${contact.name} from your emergency contacts? They'll no longer receive SOS alerts.",
                    fontFamily = PlusJakartaSans, fontSize = 14.sp, color = EcTextSec, lineHeight = 20.sp,
                )
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(EcRed)
                        .clickable { vm.deleteContact(contact); showDeleteDialog = null }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Text("Remove", fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(EcCardBorder)
                        .clickable { showDeleteDialog = null }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Text("Cancel", fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = EcTextSec)
                }
            },
            containerColor = EcCardBg,
            shape          = RoundedCornerShape(20.dp),
        )
    }
}

// ─── Contact Card ─────────────────────────────────────────────────────────────
@Composable
private fun EcContactCard(
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
            .clip(RoundedCornerShape(16.dp))
            .background(EcCardBg)
            .border(1.dp, EcCardBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(initials, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = tint)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                contact.name,
                fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold,
                fontSize = 15.sp, color = EcTextPri,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Outlined.Phone, null, tint = EcTextHint, modifier = Modifier.size(12.dp))
                Text(
                    contact.phone,
                    fontFamily = PlusJakartaSans, fontSize = 13.sp, color = EcTextSec,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (contact.relation.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(tint.copy(alpha = 0.1f))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text(
                            contact.relation,
                            fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp, color = tint,
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(EcGreen)
                .clickable(onClick = onCall),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Call, "Call", tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(EcRed.copy(alpha = 0.1f))
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.DeleteOutline, "Remove", tint = EcRed, modifier = Modifier.size(18.dp))
        }
    }
}

// ─── Local Body Row ───────────────────────────────────────────────────────────
@Composable
private fun LocalBodyRow(body: LocalBody, onCall: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(body.avatarColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(body.icon, null, tint = body.iconTint, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(body.name, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = EcTextPri)
            Text(body.number, fontFamily = PlusJakartaSans, fontSize = 13.sp, color = EcTextSec)
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(EcGreen)
                .clickable(onClick = onCall)
                .padding(horizontal = 16.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Call, null, tint = Color.White, modifier = Modifier.size(13.dp))
                Text("Call", fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
            }
        }
    }
}

// ─── Add Contact Screen — Full-screen with large avatar header ────────────────
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
    val focusManager = LocalFocusManager.current

    val avatarLetter = if (name.isNotBlank()) name.first().uppercaseChar().toString() else "?"
    val tint         = if (name.isNotBlank()) avatarTint(name) else Color(0xFF9CA3AF)
    val canSave      = name.isNotBlank() && phone.length >= 7

    Scaffold(containerColor = EcPageBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {

            // ── Custom top bar ────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Transparent)
                    .padding(top = innerPadding.calculateTopPadding())
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Outlined.ArrowBack, "Back", tint = EcTextPri)
                }
                Text(
                    "New Contact",
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 18.sp,
                    color      = EcTextPri,
                )
                // Save text button top-right
                Box(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (canSave) EcGreen else Color.Transparent)
                        .clickable(enabled = canSave) {
                            nameError  = name.isBlank()
                            phoneError = phone.length < 7
                            if (!nameError && !phoneError) {
                                vm.addContact(name, phone, relation)
                                navController.popBackStack()
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(
                        "Save",
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 14.sp,
                        color      = if (canSave) Color.White else EcTextHint,
                    )
                }
            }

            LazyColumn(
                modifier            = Modifier.fillMaxSize(),
                contentPadding      = PaddingValues(    bottom = 32.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {

                // ── Large avatar header ───────────────────────────────────────
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        tint.copy(alpha = 0.12f),
                                        EcPageBg,
                                    )
                                )
                            )
                            .padding(vertical = 36.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            // Large avatar circle
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(CircleShape)
                                    .background(tint.copy(alpha = 0.15f))
                                    .border(3.dp, tint.copy(alpha = 0.3f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    avatarLetter,
                                    fontFamily = PlusJakartaSans,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize   = 40.sp,
                                    color      = tint,
                                )
                            }
                            // Live name + relation preview
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text       = name.ifBlank { "Full Name" },
                                    fontFamily = PlusJakartaSans,
                                    fontWeight = FontWeight.Bold,
                                    fontSize   = 20.sp,
                                    color      = if (name.isBlank()) EcTextHint else EcTextPri,
                                )
                                if (phone.isNotBlank() || relation.isNotBlank()) {
                                    Text(
                                        text       = listOf(phone, relation).filter { it.isNotBlank() }.joinToString("  ·  "),
                                        fontFamily = PlusJakartaSans,
                                        fontSize   = 13.sp,
                                        color      = EcTextSec,
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Form fields card ──────────────────────────────────────────
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(EcCardBg)
                            .border(1.dp, EcCardBorder, RoundedCornerShape(18.dp))
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Name field
                        EcFormField(
                            value         = name,
                            onValueChange = { name = it; nameError = false },
                            label         = "Full Name",
                            placeholder   = "e.g. Sarah Jenkins",
                            icon          = Icons.Outlined.Person,
                            isError       = nameError,
                            errorMessage  = "Name is required",
                            imeAction     = ImeAction.Next,
                            onNext        = { focusManager.moveFocus(FocusDirection.Down) },
                        )

                        HorizontalDivider(color = EcCardBorder, thickness = 0.5.dp)

                        // Phone field
                        EcFormField(
                            value         = phone,
                            onValueChange = { phone = it; phoneError = false },
                            label         = "Phone Number",
                            placeholder   = "e.g. +977 98XXXXXXXX",
                            icon          = Icons.Outlined.Phone,
                            keyboardType  = KeyboardType.Phone,
                            isError       = phoneError,
                            errorMessage  = "Enter a valid number (min 7 digits)",
                            imeAction     = ImeAction.Next,
                            onNext        = { focusManager.moveFocus(FocusDirection.Down) },
                        )

                        HorizontalDivider(color = EcCardBorder, thickness = 0.5.dp)

                        // Relation field
                        EcFormField(
                            value         = relation,
                            onValueChange = { relation = it },
                            label         = "Relation",
                            placeholder   = "e.g. Father, Spouse, Guide",
                            icon          = Icons.Outlined.People,
                            imeAction     = ImeAction.Done,
                            onNext        = { focusManager.clearFocus() },
                        )
                    }
                }

                // ── Required note ─────────────────────────────────────────────
                item {
                    Text(
                        "Name and phone number are required.",
                        fontFamily = PlusJakartaSans,
                        fontSize   = 12.sp,
                        color      = EcTextHint,
                        modifier   = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }

                // ── Save button ───────────────────────────────────────────────
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (canSave) EcGreen else EcCardBorder)
                            .clickable(enabled = canSave) {
                                nameError  = name.isBlank()
                                phoneError = phone.length < 7
                                if (!nameError && !phoneError) {
                                    vm.addContact(name, phone, relation)
                                    navController.popBackStack()
                                }
                            }
                            .padding(vertical = 17.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.PersonAdd, null,
                                tint     = if (canSave) Color.White else EcTextHint,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                "Save Contact",
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.Bold,
                                fontSize   = 16.sp,
                                color      = if (canSave) Color.White else EcTextHint,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Flat form field (no OutlinedTextField border box) ────────────────────────
@Composable
private fun EcFormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    icon: ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false,
    errorMessage: String = "",
    imeAction: ImeAction = ImeAction.Next,
    onNext: () -> Unit = {},
) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (isError) EcRed.copy(alpha = 0.1f) else EcAccentBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon, null,
                tint     = if (isError) EcRed else EcAccent,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                label,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 11.sp,
                color      = if (isError) EcRed else EcTextHint,
                letterSpacing = 0.3.sp,
            )
            TextField(
                value         = value,
                onValueChange = onValueChange,
                modifier      = Modifier.fillMaxWidth(),
                placeholder   = {
                    Text(placeholder, fontFamily = PlusJakartaSans, fontSize = 14.sp, color = EcTextHint)
                },
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = PlusJakartaSans,
                    fontSize   = 15.sp,
                    color      = EcTextPri,
                    fontWeight = FontWeight.Medium,
                ),
                singleLine      = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                keyboardActions = KeyboardActions(
                    onNext = { onNext() },
                    onDone = { onNext() },
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor   = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor  = Color.Transparent,
                    focusedIndicatorColor   = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor             = EcAccent,
                ),
            )
            if (isError) {
                Text(
                    errorMessage,
                    fontFamily = PlusJakartaSans,
                    fontSize   = 11.sp,
                    color      = EcRed,
                )
            }
        }
    }
}

// ─── Dial helper ──────────────────────────────────────────────────────────────
fun dialNumber(context: Context, number: String) {
    context.startActivity(
        Intent(Intent.ACTION_DIAL, Uri.parse("tel:${number.replace(" ", "").replace("-", "")}"))
    )
}