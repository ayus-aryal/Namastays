package com.example.namastays.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.namastays.ui.theme.PlusJakartaSans

// ─── Palette ──────────────────────────────────────────────────────────────────
// Private to this file — local bodies screen specific shades.
private val LbPageBg        = Color(0xFFF7F8FA)
private val LbCardBg        = Color(0xFFFFFFFF)
private val LbCardBorder    = Color(0xFFE5E7EB)
private val LbTextPrimary   = Color(0xFF111827)
private val LbTextSecondary = Color(0xFF6B7280)
private val LbTextHint      = Color(0xFF9CA3AF)
private val LbGreenCall     = Color(0xFF22C55E)
private val LbCopyBg        = Color(0xFFEEF2FF)
private val LbCopyIcon      = Color(0xFF6366F1)
private val LbBadgeGreen    = Color(0xFF16A34A)

// ─── Data Models ──────────────────────────────────────────────────────────────

/**
 * A single emergency telephone number entry (Nepal national services).
 * All data is static — there is no backend for this screen; numbers are
 * hardcoded as they are unlikely to change and must be available offline.
 */
data class EmergencyNumber(
    val label   : String,
    val number  : String,
    val icon    : ImageVector,
    val iconTint: Color,
)

/**
 * A rescue helicopter / air ambulance operator.
 * [available247] drives the "24/7" badge display.
 */
data class AirlineContact(
    val name        : String,
    val phone       : String,
    val region      : String,
    val available247: Boolean = true,
)

/**
 * A foreign embassy contact entry.
 * [hours] is free-form text and may include an emergency-line note.
 */
data class EmbassyContact(
    val name : String,
    val phone: String,
    val hours: String,
)

// ─── Static Data ──────────────────────────────────────────────────────────────

private val emergencyNumbers = listOf(
    EmergencyNumber("Nepal Police",    "100",  Icons.Outlined.LocalPolice,         Color(0xFF3B82F6)),
    EmergencyNumber("Ambulance",       "102",  Icons.Outlined.LocalHospital,       Color(0xFFEF4444)),
    EmergencyNumber("Tourist Police",  "1144", Icons.Outlined.SupervisorAccount,   Color(0xFF10B981)),
    EmergencyNumber("Fire Department", "101",  Icons.Outlined.LocalFireDepartment, Color(0xFFF97316)),
)

private val airlineContacts = listOf(
    AirlineContact("Simrik Air",         "+977-1-4155341", "All Nepal"),
    AirlineContact("Fishtail Air",       "+977-1-4111815", "All Nepal"),
    AirlineContact("Altitude Air",       "+977-1-4116665", "Everest, Annapurna"),
    AirlineContact("Manang Air",         "+977-1-4115986", "Annapurna, Manang"),
    AirlineContact("Sita Air",           "+977-1-4494160", "All Nepal"),
    AirlineContact("Tara Air",           "+977-1-5542494", "Mountain routes"),
    AirlineContact("Summit Air",         "+977-1-4465266", "Khumbu region"),
    AirlineContact("Shree Airlines",     "+977-1-4494560", "Mustang, Dolpa"),
    AirlineContact("Air Dynasty",        "+977-1-4004892", "Kathmandu Valley"),
    AirlineContact("Karnali Excursions", "+977-84-420058", "Karnali region"),
)

private val embassyContacts = listOf(
    EmbassyContact("US Embassy Kathmandu",         "+977-1-4234000", "Mon–Fri 8:00–17:00, Emergency line 24/7"),
    EmbassyContact("UK Embassy Kathmandu",         "+977-1-4237100", "Mon–Fri 8:30–17:00"),
    EmbassyContact("Indian Embassy Kathmandu",     "+977-1-4410900", "Mon–Fri 9:00–17:30"),
    EmbassyContact("Chinese Embassy Kathmandu",    "+977-1-4434792", "Mon–Fri 9:00–12:00, 15:00–17:00"),
    EmbassyContact("Australian Embassy Kathmandu", "+977-1-4371678", "Mon–Fri 8:30–17:00"),
    EmbassyContact("German Embassy Kathmandu",     "+977-1-4412786", "Mon–Fri 9:00–12:00"),
    EmbassyContact("French Embassy Kathmandu",     "+977-1-4412332", "Mon–Fri 9:00–12:30, 13:30–17:00"),
)

// ─── Screen ───────────────────────────────────────────────────────────────────

/**
 * "Local Bodies" screen — a directory of Nepal emergency phone numbers,
 * helicopter rescue operators, foreign embassies, and emergency instructions.
 *
 * All data is static and hardcoded. No ViewModel is required: there is no
 * network call, no database, and no state that needs to survive
 * configuration changes beyond the expand/collapse toggle booleans
 * (which are intentionally reset on each navigation to this screen).
 *
 * NOTE: The screen is named `EmergencySOSScreen` in code for historical
 * reasons. It navigates to under the `safety/local_bodies` route and is
 * titled "Local Bodies" in the top bar — the class name is misleading but
 * kept to avoid rename-induced refactoring risk across the nav graph.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencySOSScreen(navController: NavController) {
    val context = LocalContext.current

    var airlinesExpanded by remember { mutableStateOf(true) }
    var embassyExpanded  by remember { mutableStateOf(false) }
    var instructExpanded by remember { mutableStateOf(false) }
    var showAllAirlines  by remember { mutableStateOf(false) }

    // Only the first 4 airlines are shown until the user taps "Show more"
    val visibleAirlines = if (showAllAirlines) airlineContacts else airlineContacts.take(4)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Local Bodies",
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 18.sp,
                        color      = LbTextPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint               = LbTextPrimary
                        )
                    }
                },
                colors       = TopAppBarDefaults.topAppBarColors(containerColor = LbCardBg),
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
        },
        containerColor      = LbPageBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->

        LazyColumn(
            modifier            = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            // ── Hero banner ────────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFE53935))
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        modifier         = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.PhoneInTalk,
                            contentDescription = null,
                            tint               = Color.White,
                            modifier           = Modifier.size(26.dp),
                        )
                    }
                    Column {
                        Text(
                            "Emergency Contacts",
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize   = 18.sp,
                            color      = Color.White,
                        )
                        Text(
                            "Tap any number to call immediately",
                            fontFamily = PlusJakartaSans,
                            fontSize   = 13.sp,
                            color      = Color.White.copy(alpha = 0.85f),
                        )
                    }
                }
            }

            // ── Emergency numbers section ──────────────────────────────────────
            item {
                Text(
                    "EMERGENCY NUMBERS — NEPAL",
                    fontFamily    = PlusJakartaSans,
                    fontWeight    = FontWeight.Bold,
                    fontSize      = 11.sp,
                    color         = LbTextSecondary,
                    letterSpacing = 1.sp,
                )
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(LbCardBg)
                        .border(1.dp, LbCardBorder, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp),
                ) {
                    emergencyNumbers.forEachIndexed { index, item ->
                        if (index > 0) HorizontalDivider(color = LbCardBorder, thickness = 0.5.dp)
                        LbEmergencyNumberRow(item = item, context = context)
                    }
                }
            }

            // ── Helicopter rescue section ──────────────────────────────────────
            item {
                LbExpandableSection(
                    icon     = Icons.Outlined.Flight,
                    iconTint = Color(0xFFF59E0B),
                    title    = "Helicopter Rescue",
                    count    = airlineContacts.size,
                    expanded = airlinesExpanded,
                    onToggle = { airlinesExpanded = !airlinesExpanded },
                ) {
                    Column {
                        visibleAirlines.forEachIndexed { index, airline ->
                            if (index > 0) HorizontalDivider(color = LbCardBorder, thickness = 0.5.dp)
                            LbAirlineRow(contact = airline, context = context)
                        }
                        // Progressive disclosure: show first 4 then allow "Show more"
                        if (!showAllAirlines && airlineContacts.size > 4) {
                            HorizontalDivider(color = LbCardBorder, thickness = 0.5.dp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showAllAirlines = true }
                                    .padding(vertical = 14.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment     = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Show ${airlineContacts.size - 4} more airlines",
                                    fontFamily = PlusJakartaSans,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize   = 13.sp,
                                    color      = Color(0xFF3B82F6),
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    Icons.Outlined.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint               = Color(0xFF3B82F6),
                                    modifier           = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }

            // ── Embassy contacts section ───────────────────────────────────────
            item {
                LbExpandableSection(
                    icon     = Icons.Outlined.Language,
                    iconTint = Color(0xFF3B82F6),
                    title    = "Embassy Contacts",
                    count    = embassyContacts.size,
                    expanded = embassyExpanded,
                    onToggle = { embassyExpanded = !embassyExpanded },
                    subtitle = "Passport issues, legal aid & consular emergencies",
                ) {
                    Column {
                        embassyContacts.forEachIndexed { index, embassy ->
                            if (index > 0) HorizontalDivider(color = LbCardBorder, thickness = 0.5.dp)
                            LbEmbassyRow(contact = embassy, context = context)
                        }
                    }
                }
            }

            // ── Emergency instructions section ─────────────────────────────────
            item {
                LbExpandableSection(
                    icon     = Icons.Outlined.MenuBook,
                    iconTint = Color(0xFF6366F1),
                    title    = "What To Do In An Emergency",
                    expanded = instructExpanded,
                    onToggle = { instructExpanded = !instructExpanded },
                ) {
                    Column(
                        modifier            = Modifier.padding(horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        LbInstructionItem("1", "Stay calm and assess the situation",
                            "Do not panic. Assess injuries and immediate dangers before acting.")
                        LbInstructionItem("2", "Call for help",
                            "Use the emergency numbers above. Clearly state your location, name, and the nature of the emergency.")
                        LbInstructionItem("3", "If at altitude — stop ascending",
                            "Any worsening of symptoms requires immediate descent. Do not wait for morning.")
                        LbInstructionItem("4", "Request helicopter evacuation if needed",
                            "Call a helicopter rescue service directly. Have your GPS coordinates or a nearby landmark ready.")
                    }
                }
            }
        }
    }
}

// ─── Emergency Number Row ──────────────────────────────────────────────────────

@Composable
private fun LbEmergencyNumberRow(item: EmergencyNumber, context: Context) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier         = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(item.iconTint.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(item.icon, null, tint = item.iconTint, modifier = Modifier.size(20.dp))
        }
        Column(
            modifier            = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(item.label, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = LbTextPrimary)
            Text(item.number, fontFamily = PlusJakartaSans, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = item.iconTint)
        }
        LbCallCopyButtons(phone = item.number, context = context)
    }
}

// ─── Expandable Section ────────────────────────────────────────────────────────

/**
 * A card with a tappable header row and animated expand/collapse content.
 * Used for Helicopter Rescue, Embassy Contacts, and Emergency Instructions.
 *
 * [count] is optional — omit for sections without a numeric badge.
 * [subtitle] is optional italic text shown below the divider when expanded.
 */
@Composable
private fun LbExpandableSection(
    icon    : ImageVector,
    iconTint: Color,
    title   : String,
    count   : Int? = null,
    expanded: Boolean,
    onToggle: () -> Unit,
    subtitle: String? = null,
    content : @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(LbCardBg)
            .border(1.dp, LbCardBorder, RoundedCornerShape(16.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp))
            }
            Text(
                title,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Bold,
                fontSize   = 15.sp,
                color      = LbTextPrimary,
                modifier   = Modifier.weight(1f),
            )
            if (count != null) {
                Box(
                    modifier         = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFFEEF2FF))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$count", fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = LbCopyIcon)
                }
            }
            Icon(
                imageVector        = if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint               = LbTextHint,
                modifier           = Modifier.size(20.dp),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter   = expandVertically(),
            exit    = shrinkVertically(),
        ) {
            Column {
                HorizontalDivider(color = LbCardBorder, thickness = 0.5.dp)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        fontFamily = PlusJakartaSans,
                        fontSize   = 12.sp,
                        color      = LbTextHint,
                        fontStyle  = androidx.compose.ui.text.font.FontStyle.Italic,
                        modifier   = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                Box(
                    modifier = Modifier.padding(
                        horizontal = 16.dp,
                        vertical   = if (subtitle != null) 0.dp else 4.dp
                    )
                ) {
                    content()
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ─── Airline Row ───────────────────────────────────────────────────────────────

@Composable
private fun LbAirlineRow(contact: AirlineContact, context: Context) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            modifier            = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(contact.name, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = LbTextPrimary)
                if (contact.available247) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(LbBadgeGreen)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text("24/7", fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color.White)
                    }
                }
            }
            Text(contact.phone, fontFamily = PlusJakartaSans, fontSize = 13.sp, color = LbTextSecondary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Outlined.LocationOn, null, tint = Color(0xFF3B82F6), modifier = Modifier.size(12.dp))
                Text(contact.region, fontFamily = PlusJakartaSans, fontSize = 11.sp, color = Color(0xFF3B82F6))
            }
        }
        LbCallCopyButtons(phone = contact.phone, context = context)
    }
}

// ─── Embassy Row ───────────────────────────────────────────────────────────────

@Composable
private fun LbEmbassyRow(contact: EmbassyContact, context: Context) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            modifier            = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(contact.name, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = LbTextPrimary)
            Text(contact.phone, fontFamily = PlusJakartaSans, fontSize = 13.sp, color = LbTextSecondary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Outlined.AccessTime, null, tint = LbTextHint, modifier = Modifier.size(11.dp))
                Text(contact.hours, fontFamily = PlusJakartaSans, fontSize = 11.sp, color = LbTextHint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        LbCallCopyButtons(phone = contact.phone, context = context)
    }
}

// ─── Call + Copy Buttons ──────────────────────────────────────────────────────

/**
 * Reusable pair of call and copy-to-clipboard buttons, used on every row.
 * [dialNumber] is defined in ContactScreen.kt (same package) and reused here.
 */
@Composable
private fun LbCallCopyButtons(phone: String, context: Context) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(LbGreenCall)
                .clickable { dialNumber(context, phone) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Call, contentDescription = "Call $phone", tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(LbCopyBg)
                .clickable { copyToClipboard(context, phone) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy $phone", tint = LbCopyIcon, modifier = Modifier.size(18.dp))
        }
    }
}

// ─── Instruction Item ──────────────────────────────────────────────────────────

@Composable
private fun LbInstructionItem(number: String, title: String, body: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment     = Alignment.Top,
    ) {
        Box(
            modifier         = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(0xFFEEF2FF))
                .border(1.dp, Color(0xFFC7D2FE), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(number, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = LbCopyIcon)
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = LbTextPrimary)
            Text(body, fontFamily = PlusJakartaSans, fontSize = 12.sp, color = LbTextSecondary, lineHeight = 17.sp)
        }
    }
}

// ─── Clipboard Helper ─────────────────────────────────────────────────────────

/**
 * Copies [text] to the system clipboard and shows a brief Toast confirmation.
 * Named [copyToClipboard] and private to this file — [dialNumber] is
 * defined in ContactScreen.kt and referenced here via the shared package
 * scope (both files are `package com.example.namastays.screens`).
 */
private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Phone number", text))
    Toast.makeText(context, "Number copied", Toast.LENGTH_SHORT).show()
}