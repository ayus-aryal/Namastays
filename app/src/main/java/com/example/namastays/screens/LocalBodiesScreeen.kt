package com.example.namastays.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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

// ─────────────────────────────────────────────────────────────────────────────
//  Palette — white theme
// ─────────────────────────────────────────────────────────────────────────────

private val PageBg        = Color(0xFFF7F8FA)
private val CardBg        = Color(0xFFFFFFFF)
private val CardBorder    = Color(0xFFE5E7EB)
private val TextPrimary   = Color(0xFF111827)
private val TextSecondary = Color(0xFF6B7280)
private val TextHint      = Color(0xFF9CA3AF)
private val GreenCall     = Color(0xFF22C55E)
private val CopyBg        = Color(0xFFEEF2FF)
private val CopyIcon      = Color(0xFF6366F1)
private val SectionBg     = Color(0xFFFFFFFF)
private val BadgeGreen    = Color(0xFF16A34A)

// ─────────────────────────────────────────────────────────────────────────────
//  Data
// ─────────────────────────────────────────────────────────────────────────────

data class EmergencyNumber(
    val label: String,
    val number: String,
    val icon: ImageVector,
    val iconTint: Color,
)

data class AirlineContact(
    val name: String,
    val phone: String,
    val region: String,
    val available247: Boolean = true,
)

data class EmbassyContact(
    val name: String,
    val phone: String,
    val hours: String,
)

private val emergencyNumbers = listOf(
    EmergencyNumber("Nepal Police",    "100",  Icons.Outlined.LocalPolice,          Color(0xFF3B82F6)),
    EmergencyNumber("Ambulance",       "102",  Icons.Outlined.LocalHospital,        Color(0xFFEF4444)),
    EmergencyNumber("Tourist Police",  "1144", Icons.Outlined.SupervisorAccount,    Color(0xFF10B981)),
    EmergencyNumber("Fire Department", "101",  Icons.Outlined.LocalFireDepartment,  Color(0xFFF97316)),
)

private val airlineContacts = listOf(
    AirlineContact("Simrik Air",   "+977-1-4155341", "All Nepal"),
    AirlineContact("Fishtail Air", "+977-1-4111815", "All Nepal"),
    AirlineContact("Altitude Air", "+977-1-4116665", "Everest, Annapurna"),
    AirlineContact("Manang Air",   "+977-1-4115986", "Annapurna, Manang"),
    AirlineContact("Sita Air",     "+977-1-4494160", "All Nepal"),
    AirlineContact("Tara Air",     "+977-1-5542494", "Mountain routes"),
    AirlineContact("Summit Air",   "+977-1-4465266", "Khumbu region"),
    AirlineContact("Shree Airlines","+977-1-4494560","Mustang, Dolpa"),
    AirlineContact("Air Dynasty",  "+977-1-4004892", "Kathmandu Valley"),
    AirlineContact("Karnali Excursions","+977-84-420058","Karnali region"),
)

private val embassyContacts = listOf(
    EmbassyContact("US Embassy Kathmandu",        "+977-1-4234000", "Mon–Fri 8:00–17:00, Emergency line 24/7"),
    EmbassyContact("UK Embassy Kathmandu",        "+977-1-4237100", "Mon–Fri 8:30–17:00"),
    EmbassyContact("Indian Embassy Kathmandu",    "+977-1-4410900", "Mon–Fri 9:00–17:30"),
    EmbassyContact("Chinese Embassy Kathmandu",   "+977-1-4434792", "Mon–Fri 9:00–12:00, 15:00–17:00"),
    EmbassyContact("Australian Embassy Kathmandu","+977-1-4371678", "Mon–Fri 8:30–17:00"),
    EmbassyContact("German Embassy Kathmandu",    "+977-1-4412786", "Mon–Fri 9:00–12:00"),
    EmbassyContact("French Embassy Kathmandu",    "+977-1-4412332", "Mon–Fri 9:00–12:30, 13:30–17:00"),
)

// ─────────────────────────────────────────────────────────────────────────────
//  Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencySOSScreen(navController: NavController) {
    val context = LocalContext.current
    var airlinesExpanded  by remember { mutableStateOf(true) }
    var embassyExpanded   by remember { mutableStateOf(false) }
    var instructExpanded  by remember { mutableStateOf(false) }
    var showAllAirlines   by remember { mutableStateOf(false) }

    val visibleAirlines = if (showAllAirlines) airlineContacts else airlineContacts.take(4)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Emergency SOS",
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 18.sp,
                        color      = TextPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CardBg),
            )
        },
        containerColor = PageBg,
    ) { innerPadding ->

        LazyColumn(
            modifier        = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding  = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            // ── Hero banner ──────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFE53935))
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
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
                            tint     = Color.White,
                            modifier = Modifier.size(26.dp),
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

            // ── Emergency numbers as rows ─────────────────────────────────────
            item {
                Text(
                    "EMERGENCY NUMBERS (NEPAL)",
                    fontFamily    = PlusJakartaSans,
                    fontWeight    = FontWeight.Bold,
                    fontSize      = 11.sp,
                    color         = TextSecondary,
                    letterSpacing = 1.sp,
                )
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardBg)
                        .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp),
                ) {
                    emergencyNumbers.forEachIndexed { index, item ->
                        if (index > 0) HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                        EmergencyNumberRow(item = item, context = context)
                    }
                }
            }

            // ── Helicopter Rescue ────────────────────────────────────────────
            item {
                ExpandableSection(
                    icon      = Icons.Outlined.Flight,
                    iconTint  = Color(0xFFF59E0B),
                    title     = "Helicopter Rescue",
                    count     = airlineContacts.size,
                    expanded  = airlinesExpanded,
                    onToggle  = { airlinesExpanded = !airlinesExpanded },
                ) {
                    Column {
                        visibleAirlines.forEachIndexed { index, airline ->
                            if (index > 0) HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                            AirlineRow(contact = airline, context = context)
                        }
                        if (!showAllAirlines && airlineContacts.size > 4) {
                            HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showAllAirlines = true }
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Show ${airlineContacts.size - 4} more",
                                    fontFamily = PlusJakartaSans,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize   = 13.sp,
                                    color      = Color(0xFF3B82F6),
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    Icons.Outlined.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint     = Color(0xFF3B82F6),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }

            // ── Embassy Contacts ─────────────────────────────────────────────
            item {
                ExpandableSection(
                    icon      = Icons.Outlined.Language,
                    iconTint  = Color(0xFF3B82F6),
                    title     = "Embassy Contacts",
                    count     = embassyContacts.size,
                    expanded  = embassyExpanded,
                    onToggle  = { embassyExpanded = !embassyExpanded },
                    subtitle  = "Passport issues, legal aid & consular emergencies",
                ) {
                    Column {
                        embassyContacts.forEachIndexed { index, embassy ->
                            if (index > 0) HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                            EmbassyRow(contact = embassy, context = context)
                        }
                    }
                }
            }

            // ── Emergency Instructions ───────────────────────────────────────
            item {
                ExpandableSection(
                    icon      = Icons.Outlined.MenuBook,
                    iconTint  = Color(0xFF6366F1),
                    title     = "Emergency Instructions",
                    expanded  = instructExpanded,
                    onToggle  = { instructExpanded = !instructExpanded },
                ) {
                    Column(
                        modifier            = Modifier.padding(horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        InstructionItem(
                            number = "1",
                            title  = "Stay calm and assess the situation",
                            body   = "Do not panic. Assess injuries and immediate dangers before acting.",
                        )
                        InstructionItem(
                            number = "2",
                            title  = "Call for help",
                            body   = "Use the emergency numbers above. Clearly state your location, name, and the nature of the emergency.",
                        )
                        InstructionItem(
                            number = "3",
                            title  = "If at altitude — stop ascending",
                            body   = "Any worsening of symptoms requires immediate descent. Do not wait for morning.",
                        )
                        InstructionItem(
                            number = "4",
                            title  = "Request helicopter evacuation if needed",
                            body   = "Call a helicopter rescue service directly. Have your GPS coordinates or landmark ready.",
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Emergency number row (same style as airline / embassy rows)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmergencyNumberRow(item: EmergencyNumber, context: Context) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier         = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(item.iconTint.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = item.icon,
                contentDescription = null,
                tint               = item.iconTint,
                modifier           = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text       = item.label,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Bold,
                fontSize   = 14.sp,
                color      = TextPrimary,
            )
            Text(
                text       = item.number,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.ExtraBold,
                fontSize   = 18.sp,
                color      = item.iconTint,
            )
        }
        CallCopyButtons(phone = item.number, context = context)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Expandable section wrapper
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ExpandableSection(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    count: Int? = null,
    expanded: Boolean,
    onToggle: () -> Unit,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SectionBg)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp)),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            Text(
                text       = title,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Bold,
                fontSize   = 15.sp,
                color      = TextPrimary,
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
                    Text(
                        text       = "$count",
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 12.sp,
                        color      = CopyIcon,
                    )
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = TextHint,
                modifier = Modifier.size(20.dp),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter   = expandVertically(),
            exit    = shrinkVertically(),
        ) {
            Column {
                HorizontalDivider(color = CardBorder, thickness = 0.5.dp)
                if (subtitle != null) {
                    Text(
                        text       = subtitle,
                        fontFamily = PlusJakartaSans,
                        fontSize   = 12.sp,
                        color      = TextHint,
                        fontStyle  = androidx.compose.ui.text.font.FontStyle.Italic,
                        modifier   = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = if (subtitle != null) 0.dp else 4.dp)) {
                    content()
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Airline row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AirlineRow(contact: AirlineContact, context: Context) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text       = contact.name,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 14.sp,
                    color      = TextPrimary,
                )
                if (contact.available247) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(BadgeGreen)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            "24/7",
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 10.sp,
                            color      = Color.White,
                        )
                    }
                }
            }
            Text(
                text       = contact.phone,
                fontFamily = PlusJakartaSans,
                fontSize   = 13.sp,
                color      = TextSecondary,
            )
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    Icons.Outlined.LocationOn,
                    contentDescription = null,
                    tint     = Color(0xFF3B82F6),
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    text       = contact.region,
                    fontFamily = PlusJakartaSans,
                    fontSize   = 11.sp,
                    color      = Color(0xFF3B82F6),
                )
            }
        }
        CallCopyButtons(phone = contact.phone, context = context)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Embassy row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmbassyRow(contact: EmbassyContact, context: Context) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text       = contact.name,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Bold,
                fontSize   = 14.sp,
                color      = TextPrimary,
            )
            Text(
                text       = contact.phone,
                fontFamily = PlusJakartaSans,
                fontSize   = 13.sp,
                color      = TextSecondary,
            )
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    Icons.Outlined.AccessTime,
                    contentDescription = null,
                    tint     = TextHint,
                    modifier = Modifier.size(11.dp),
                )
                Text(
                    text       = contact.hours,
                    fontFamily = PlusJakartaSans,
                    fontSize   = 11.sp,
                    color      = TextHint,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                )
            }
        }
        CallCopyButtons(phone = contact.phone, context = context)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Call + Copy button pair
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CallCopyButtons(phone: String, context: Context) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Call
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(GreenCall)
                .clickable { dialNumber(context, phone) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Call,
                contentDescription = "Call $phone",
                tint     = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
        // Copy
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(CopyBg)
                .clickable { copyToClipboard(context, phone) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.ContentCopy,
                contentDescription = "Copy $phone",
                tint     = CopyIcon,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Instruction item
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun InstructionItem(number: String, title: String, body: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment     = Alignment.Top,
    ) {
        Box(
            modifier         = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(Color(0xFFEEF2FF)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text       = number,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Bold,
                fontSize   = 12.sp,
                color      = CopyIcon,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text       = title,
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Bold,
                fontSize   = 13.sp,
                color      = TextPrimary,
            )
            Text(
                text       = body,
                fontFamily = PlusJakartaSans,
                fontSize   = 12.sp,
                color      = TextSecondary,
                lineHeight = 17.sp,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Phone number", text))
    Toast.makeText(context, "Number copied", Toast.LENGTH_SHORT).show()
}