package com.example.namastays.screens

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.namastays.NamastaysApp
import com.example.namastays.dto.PropertyDetailsResponse
import com.example.namastays.dto.RoomResponse
import com.example.namastays.viewmodel.PropertyDetailsUiState
import com.example.namastays.viewmodel.PropertyDetailsViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// ─────────────────────────────────────────────────────────────────────────────
// Colour tokens
// ─────────────────────────────────────────────────────────────────────────────
private val CbPageBg        = Color(0xFFF4F5F9)
private val CbCardBg        = Color(0xFFFFFFFF)
private val CbPrimaryText   = Color(0xFF111827)
private val CbSecondaryText = Color(0xFF6B7280)
private val CbSubtleText    = Color(0xFF9CA3AF)
private val CbBorderGrey    = Color(0xFFE5E7EB)
private val CbAccentIndigo  = Color(0xFF4F46E5)
private val CbNavyDark      = Color(0xFF1E1B4B)
private val CbAmberBg       = Color(0xFFFFFBEB)
private val CbAmberBorder   = Color(0xFFFDE68A)
private val CbAmberText     = Color(0xFF92400E)
private val CbAmberIcon     = Color(0xFFB45309)
private val CbRedBg         = Color(0xFFFEE2E2)
private val CbRedText       = Color(0xFFDC2626)
private val CbTagBg         = Color(0xFFF3F4F6)
private val CbIndigoBg      = Color(0xFFEEF2FF)
private val CbSelectedBg    = Color(0xFFEEF2FF)
private val CbSelectedText  = Color(0xFF4F46E5)
private val CbDivider       = Color(0xFFF3F4F6)
private val CbInputBorder   = Color(0xFFE5E7EB)
private val CbInputBg       = Color(0xFFFAFAFA)

// ─────────────────────────────────────────────────────────────────────────────
// Formatters — companion object avoids @RequiresApi on top-level delegates
// ─────────────────────────────────────────────────────────────────────────────
@RequiresApi(Build.VERSION_CODES.O)
private object Fmts {
    val DATE_RANGE: DateTimeFormatter by lazy { DateTimeFormatter.ofPattern("MMM d") }
}

private const val SERVICE_RATE = 0.06
private const val TAX_RATE     = 0.06

// Hoisted regex — avoid recompiling on every recomposition/keystroke.
private val PhoneStripRegex = Regex("[\\s\\-+()]")

// LocalDate is not Parcelable, so rememberSaveable needs an explicit Saver
// to survive rotation / process death. Stored as epoch-day Long.
@RequiresApi(Build.VERSION_CODES.O)
private val LocalDateSaver: Saver<LocalDate, Long> = Saver(
    save = { it.toEpochDay() },
    restore = { LocalDate.ofEpochDay(it) }
)

// Saver for the additional-guest-names list, so typed names survive rotation too.
private val StringListSaver = listSaver<SnapshotStateList<String>, String>(
    save = { it.toList() },
    restore = { it.toMutableStateList() }
)
// ─────────────────────────────────────────────────────────────────────────────
// Price data class — computed once, never inline in the composable body
// ─────────────────────────────────────────────────────────────────────────────
private data class PriceBreakdown(
    val nights: Long,
    val pricePerNight: Long,
    val roomSubtotal: Long,
    val serviceFee: Long,
    val taxAmount: Long,
    val total: Long
)

@RequiresApi(Build.VERSION_CODES.O)
private fun calcBreakdown(
    checkIn: LocalDate,
    checkOut: LocalDate,
    pricePerNight: Long
): PriceBreakdown {
    val nights       = ChronoUnit.DAYS.between(checkIn, checkOut).coerceAtLeast(1L)
    val roomSubtotal = pricePerNight * nights
    val serviceFee   = Math.round(roomSubtotal * SERVICE_RATE)
    val taxAmount    = Math.round(roomSubtotal * TAX_RATE)
    val total        = roomSubtotal + serviceFee + taxAmount
    return PriceBreakdown(nights, pricePerNight, roomSubtotal, serviceFee, taxAmount, total)
}

// ─────────────────────────────────────────────────────────────────────────────
// RoomResponse helper
// ─────────────────────────────────────────────────────────────────────────────
private fun RoomResponse.maxGuestsInt(): Int =
    maxGuests.toIntOrNull()?.coerceAtLeast(1) ?: 2

// ─────────────────────────────────────────────────────────────────────────────
// Shimmer skeleton
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp)
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue  = 0.3f,
        targetValue   = 0.7f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )
    Box(modifier = modifier.clip(shape).background(CbBorderGrey.copy(alpha = alpha)))
}

@Composable
private fun ConfirmBookingSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CbPageBg)
            .statusBarsPadding()
    ) {
        Surface(color = CbCardBg, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier          = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShimmerBox(modifier = Modifier.size(36.dp), shape = RoundedCornerShape(50.dp))
                Spacer(Modifier.width(12.dp))
                ShimmerBox(modifier = Modifier.height(20.dp).width(160.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Column(
            modifier            = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SkeletonCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShimmerBox(modifier = Modifier.size(80.dp), shape = RoundedCornerShape(12.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ShimmerBox(modifier = Modifier.height(18.dp).width(180.dp))
                        ShimmerBox(modifier = Modifier.height(13.dp).width(140.dp))
                    }
                }
            }
            SkeletonCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ShimmerBox(modifier = Modifier.height(18.dp).width(160.dp))
                    ShimmerBox(modifier = Modifier.height(13.dp).width(200.dp))
                    ShimmerBox(modifier = Modifier.height(40.dp).fillMaxWidth(), shape = RoundedCornerShape(10.dp))
                }
            }
            SkeletonCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ShimmerBox(modifier = Modifier.height(18.dp).width(120.dp))
                    repeat(3) {
                        ShimmerBox(modifier = Modifier.height(48.dp).fillMaxWidth(), shape = RoundedCornerShape(10.dp))
                    }
                }
            }
            SkeletonCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ShimmerBox(modifier = Modifier.height(18.dp).width(140.dp))
                    repeat(3) {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            ShimmerBox(modifier = Modifier.height(14.dp).width(140.dp))
                            ShimmerBox(modifier = Modifier.height(14.dp).width(70.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkeletonCard(content: @Composable () -> Unit) {
    Card(
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = CbCardBg),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier  = Modifier
            .fillMaxWidth()
            .border(1.dp, CbBorderGrey, RoundedCornerShape(16.dp))
    ) {
        Box(modifier = Modifier.padding(16.dp)) { content() }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section card
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier  = modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = CbCardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border    = androidx.compose.foundation.BorderStroke(1.dp, CbBorderGrey)
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Price row
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun BreakdownRow(
    label: String,
    value: String,
    labelColor: Color  = CbSecondaryText,
    valueColor: Color  = CbPrimaryText,
    valueBold: Boolean = false,
    labelBold: Boolean = false
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(
            text       = label,
            fontSize   = 14.sp,
            fontFamily = PlusJakartaSans,
            color      = labelColor,
            fontWeight = if (labelBold) FontWeight.SemiBold else FontWeight.Normal,
            modifier   = Modifier.weight(1f),
            overflow   = TextOverflow.Ellipsis,
            maxLines   = 1
        )
        Text(
            text       = value,
            fontSize   = 14.sp,
            fontFamily = PlusJakartaSans,
            color      = valueColor,
            fontWeight = if (valueBold) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Text field
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun CbTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    isError: Boolean = false
) {
    OutlinedTextField(
        value           = value,
        onValueChange   = onValueChange,
        placeholder     = {
            Text(placeholder, fontFamily = PlusJakartaSans, fontSize = 14.sp, color = CbSubtleText)
        },
        textStyle       = LocalTextStyle.current.copy(
            fontFamily = PlusJakartaSans,
            fontSize   = 14.sp,
            color      = CbPrimaryText
        ),
        singleLine      = true,
        isError         = isError,
        keyboardOptions = KeyboardOptions(
            keyboardType   = keyboardType,
            capitalization = if (keyboardType == KeyboardType.Text)
                KeyboardCapitalization.Words else KeyboardCapitalization.None
        ),
        shape  = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor      = CbAccentIndigo,
            unfocusedBorderColor    = CbInputBorder,
            errorBorderColor        = CbRedText,
            focusedContainerColor   = CbCardBg,
            unfocusedContainerColor = CbInputBg,
            errorContainerColor     = CbRedBg.copy(alpha = 0.4f)
        ),
        modifier = modifier.fillMaxWidth()
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Additional guest row
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AdditionalGuestRow(
    guestNumber: Int,
    name: String,
    onNameChange: (String) -> Unit
) {
    Column {
        Text(
            text       = "Guest $guestNumber Full Name",
            fontSize   = 13.sp,
            fontFamily = PlusJakartaSans,
            fontWeight = FontWeight.Medium,
            color      = CbSecondaryText,
            modifier   = Modifier.padding(bottom = 6.dp)
        )
        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value         = name,
                onValueChange = onNameChange,
                placeholder   = {
                    Text("Enter name", fontFamily = PlusJakartaSans, fontSize = 14.sp, color = CbSubtleText)
                },
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = PlusJakartaSans,
                    fontSize   = 14.sp,
                    color      = CbPrimaryText
                ),
                singleLine      = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType   = KeyboardType.Text,
                    capitalization = KeyboardCapitalization.Words
                ),
                shape  = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor      = CbAccentIndigo,
                    unfocusedBorderColor    = CbInputBorder,
                    focusedContainerColor   = CbCardBg,
                    unfocusedContainerColor = CbInputBg
                ),
                modifier = Modifier.weight(1f)
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, CbAccentIndigo.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .clickable { /* expand detail fields if needed */ }
                    .padding(horizontal = 10.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Add, null, tint = CbAccentIndigo, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    text       = "Details",
                    fontSize   = 13.sp,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.SemiBold,
                    color      = CbAccentIndigo
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reusable empty / error state
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun EmptyState(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    body: String,
    buttonLabel: String,
    onButton: () -> Unit
) {
    Column(
        modifier            = Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier         = Modifier.size(80.dp).clip(CircleShape).background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = PlusJakartaSans, color = CbPrimaryText, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, fontSize = 14.sp, fontFamily = PlusJakartaSans, color = CbSecondaryText, textAlign = TextAlign.Center, lineHeight = 22.sp)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onButton,
            shape   = RoundedCornerShape(50.dp),
            colors  = ButtonDefaults.buttonColors(containerColor = CbNavyDark)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(buttonLabel, fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Date picker dialog
// ─────────────────────────────────────────────────────────────────────────────
@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CbDatePickerDialog(
    initial: LocalDate,
    minDate: LocalDate,
    onDismiss: () -> Unit,
    onDateSelected: (LocalDate) -> Unit
) {
    val initialMillis = remember(initial) {
        initial.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val d = java.time.Instant.ofEpochMilli(utcTimeMillis)
                    .atZone(java.time.ZoneOffset.UTC).toLocalDate()
                return !d.isBefore(minDate)
            }
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    val d = java.time.Instant.ofEpochMilli(millis)
                        .atZone(java.time.ZoneOffset.UTC).toLocalDate()
                    onDateSelected(d)
                }
            }) {
                Text("Confirm", fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold, color = CbAccentIndigo)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontFamily = PlusJakartaSans, color = CbSecondaryText)
            }
        }
    ) {
        DatePicker(
            state  = state,
            colors = DatePickerDefaults.colors(
                selectedDayContainerColor = CbAccentIndigo,
                todayDateBorderColor      = CbAccentIndigo,
                selectedDayContentColor   = Color.White
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Property summary section — extracted so name/email/phone keystrokes
// (handled in GuestDetailsSection) don't force this back to recompose.
// ─────────────────────────────────────────────────────────────────────────────
@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun PropertySummarySection(
    propertyName: String,
    thumbnailUrl: String?,
    checkIn: LocalDate,
    checkOut: LocalDate,
    nights: Long
) {
    SectionCard {
        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier         = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CbIndigoBg),
                contentAlignment = Alignment.Center
            ) {
                if (!thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model              = thumbnailUrl,
                        contentDescription = propertyName,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Icon(Icons.Outlined.Apartment, null, tint = CbAccentIndigo, modifier = Modifier.size(32.dp))
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = propertyName,
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = PlusJakartaSans,
                    color      = CbPrimaryText,
                    maxLines   = 2,
                    overflow   = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CalendarMonth, null, tint = CbAccentIndigo, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text       = "${checkIn.format(Fmts.DATE_RANGE)} – ${checkOut.format(Fmts.DATE_RANGE)}  ·  $nights night${if (nights > 1L) "s" else ""}",
                        fontSize   = 12.sp,
                        fontFamily = PlusJakartaSans,
                        color      = CbSecondaryText
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Room summary section — owns its own date-picker dialog state so opening a
// picker doesn't ripple into guest-details or price-breakdown recomposition.
// ─────────────────────────────────────────────────────────────────────────────
@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun RoomSummarySection(
    room: RoomResponse,
    totalGuests: Int,
    breakfastIncluded: Boolean,
    checkInTime: String?,
    checkOutTime: String?,
    checkIn: LocalDate,
    checkOut: LocalDate,
    today: LocalDate,
    onCheckInSelected: (LocalDate) -> Unit,
    onCheckOutSelected: (LocalDate) -> Unit
) {
    var showCheckInPicker  by remember { mutableStateOf(false) }
    var showCheckOutPicker by remember { mutableStateOf(false) }

    if (showCheckInPicker) {
        CbDatePickerDialog(
            initial        = checkIn,
            minDate        = today.plusDays(1),
            onDismiss      = { showCheckInPicker = false },
            onDateSelected = { d -> onCheckInSelected(d); showCheckInPicker = false }
        )
    }
    if (showCheckOutPicker) {
        CbDatePickerDialog(
            initial        = checkOut,
            minDate        = checkIn.plusDays(1),
            onDismiss      = { showCheckOutPicker = false },
            onDateSelected = { d -> onCheckOutSelected(d); showCheckOutPicker = false }
        )
    }

    SectionCard {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text       = room.category,
                    fontSize   = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = PlusJakartaSans,
                    color      = CbPrimaryText,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text       = "$totalGuests guest${if (totalGuests > 1) "s" else ""}  ·  ${room.bedType}",
                    fontSize   = 13.sp,
                    fontFamily = PlusJakartaSans,
                    color      = CbSecondaryText
                )
            }
            Surface(shape = RoundedCornerShape(50.dp), color = CbSelectedBg) {
                Text(
                    text       = "Selected",
                    modifier   = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    fontSize   = 12.sp,
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.SemiBold,
                    color      = CbSelectedText
                )
            }
        }

        if (breakfastIncluded) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(CbTagBg)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.FreeBreakfast, null, tint = CbAccentIndigo, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Text("Complimentary Breakfast Included", fontSize = 13.sp, fontFamily = PlusJakartaSans, color = CbSecondaryText)
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = CbDivider)
        Spacer(Modifier.height(12.dp))

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, CbBorderGrey, RoundedCornerShape(10.dp))
                    .clickable { showCheckInPicker = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text("CHECK-IN", fontSize = 9.sp, color = CbSubtleText, fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
                Spacer(Modifier.height(2.dp))
                Text(checkIn.format(Fmts.DATE_RANGE), fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = PlusJakartaSans, color = CbPrimaryText)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, CbBorderGrey, RoundedCornerShape(10.dp))
                    .clickable { showCheckOutPicker = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text("CHECK-OUT", fontSize = 9.sp, color = CbSubtleText, fontFamily = PlusJakartaSans, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
                Spacer(Modifier.height(2.dp))
                Text(checkOut.format(Fmts.DATE_RANGE), fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = PlusJakartaSans, color = CbPrimaryText)
            }
        }

        if (checkInTime != null || checkOutTime != null) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                checkInTime?.let { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Schedule, null, tint = CbSubtleText, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("From $t", fontSize = 11.sp, fontFamily = PlusJakartaSans, color = CbSubtleText)
                    }
                }
                checkOutTime?.let { t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Schedule, null, tint = CbSubtleText, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("By $t", fontSize = 11.sp, fontFamily = PlusJakartaSans, color = CbSubtleText)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Guest details section — owns name/email/phone/guest-count state internally
// via hoisted params + lambdas. Extracted so its keystrokes don't recompose
// the property card, room card, or price breakdown above/below it.
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun GuestDetailsSection(
    primaryName: String,
    onPrimaryNameChange: (String) -> Unit,
    primaryEmail: String,
    onPrimaryEmailChange: (String) -> Unit,
    primaryPhone: String,
    onPrimaryPhoneChange: (String) -> Unit,
    nameError: Boolean,
    emailError: Boolean,
    phoneError: Boolean,
    additionalNames: List<String>,
    onAdditionalNameChange: (Int, String) -> Unit,
    totalGuests: Int,
    maxGuests: Int,
    onIncrementGuests: () -> Unit,
    onDecrementGuests: () -> Unit
) {
    SectionCard {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Text(
                text       = "Guest Details",
                fontSize   = 17.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = PlusJakartaSans,
                color      = CbPrimaryText
            )
            Box(
                modifier         = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(CbTagBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Person, null, tint = CbAccentIndigo, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text          = "PRIMARY GUEST",
            fontSize      = 10.sp,
            fontFamily    = PlusJakartaSans,
            fontWeight    = FontWeight.Bold,
            letterSpacing = 1.sp,
            color         = CbAccentIndigo
        )

        Spacer(Modifier.height(14.dp))

        Text("Full Name", fontSize = 13.sp, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Medium, color = CbSecondaryText)
        Spacer(Modifier.height(6.dp))
        CbTextField(
            value         = primaryName,
            onValueChange = onPrimaryNameChange,
            placeholder   = "Arjun Sharma",
            isError       = nameError
        )
        if (nameError) {
            Text("Name must be at least 2 characters", fontSize = 11.sp, color = CbRedText, fontFamily = PlusJakartaSans, modifier = Modifier.padding(top = 3.dp, start = 4.dp))
        }

        Spacer(Modifier.height(12.dp))

        Text("Email Address", fontSize = 13.sp, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Medium, color = CbSecondaryText)
        Spacer(Modifier.height(6.dp))
        CbTextField(
            value         = primaryEmail,
            onValueChange = onPrimaryEmailChange,
            placeholder   = "arjun.sharma@example.com",
            keyboardType  = KeyboardType.Email,
            isError       = emailError
        )
        if (emailError) {
            Text("Enter a valid email address", fontSize = 11.sp, color = CbRedText, fontFamily = PlusJakartaSans, modifier = Modifier.padding(top = 3.dp, start = 4.dp))
        }

        Spacer(Modifier.height(12.dp))

        Text("Phone Number", fontSize = 13.sp, fontFamily = PlusJakartaSans, fontWeight = FontWeight.Medium, color = CbSecondaryText)
        Spacer(Modifier.height(6.dp))
        CbTextField(
            value         = primaryPhone,
            onValueChange = onPrimaryPhoneChange,
            placeholder   = "+977 980-0000000",
            keyboardType  = KeyboardType.Phone,
            isError       = phoneError
        )
        if (phoneError) {
            Text("Enter a valid phone number", fontSize = 11.sp, color = CbRedText, fontFamily = PlusJakartaSans, modifier = Modifier.padding(top = 3.dp, start = 4.dp))
        }

        AnimatedVisibility(
            visible = additionalNames.isNotEmpty(),
            enter   = fadeIn() + expandVertically(),
            exit    = fadeOut() + shrinkVertically()
        ) {
            Column {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = CbDivider)
                Spacer(Modifier.height(16.dp))

                Text(
                    text          = "ADDITIONAL GUESTS (${additionalNames.size})",
                    fontSize      = 10.sp,
                    fontFamily    = PlusJakartaSans,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color         = CbSecondaryText
                )

                Spacer(Modifier.height(14.dp))

                additionalNames.forEachIndexed { idx, name ->
                    if (idx > 0) Spacer(Modifier.height(12.dp))
                    AdditionalGuestRow(
                        guestNumber  = idx + 2,
                        name         = name,
                        onNameChange = { onAdditionalNameChange(idx, it) }
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = CbDivider)
        Spacer(Modifier.height(12.dp))

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column {
                Text("Guests", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = PlusJakartaSans, color = CbPrimaryText)
                Text("Max $maxGuests allowed", fontSize = 11.sp, fontFamily = PlusJakartaSans, color = CbSubtleText)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val canDec = totalGuests > 1
                Box(
                    modifier         = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (canDec) CbTagBg else CbBorderGrey)
                        .clickable(enabled = canDec) { onDecrementGuests() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Remove, null, tint = if (canDec) CbPrimaryText else CbSubtleText, modifier = Modifier.size(16.dp))
                }
                Text(
                    text      = "$totalGuests",
                    fontSize  = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = PlusJakartaSans,
                    color     = CbPrimaryText,
                    modifier  = Modifier.width(36.dp),
                    textAlign = TextAlign.Center
                )
                val canInc = totalGuests < maxGuests
                Box(
                    modifier         = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (canInc) CbNavyDark else CbBorderGrey)
                        .clickable(enabled = canInc) { onIncrementGuests() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Add, null, tint = if (canInc) Color.White else CbSubtleText, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Price breakdown section — isolated so guest-details keystrokes don't
// recompute/redraw it on every character typed.
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PriceBreakdownSection(bd: PriceBreakdownDisplay) {
    SectionCard {
        Text(
            text       = "Price Breakdown",
            fontSize   = 17.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = PlusJakartaSans,
            color      = CbPrimaryText
        )

        Spacer(Modifier.height(16.dp))

        BreakdownRow(
            label = "NPR ${bd.pricePerNight} x ${bd.nights} night${if (bd.nights > 1L) "s" else ""}",
            value = "NPR ${bd.roomSubtotal}"
        )
        Spacer(Modifier.height(10.dp))
        BreakdownRow(label = "Service Fee", value = "NPR ${bd.serviceFee}")
        Spacer(Modifier.height(10.dp))
        BreakdownRow(label = "Taxes", value = "NPR ${bd.taxAmount}")

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = CbBorderGrey)
        Spacer(Modifier.height(14.dp))

        BreakdownRow(
            label      = "Total Amount",
            value      = "NPR ${bd.total}",
            labelColor = CbPrimaryText,
            valueColor = CbAccentIndigo,
            valueBold  = true,
            labelBold  = true
        )
    }
}

// Stable, minimal data carrier passed into PriceBreakdownSection — keeps the
// section's recomposition keyed only on the numbers it actually needs.
private data class PriceBreakdownDisplay(
    val nights: Long,
    val pricePerNight: Long,
    val roomSubtotal: Long,
    val serviceFee: Long,
    val taxAmount: Long,
    val total: Long
)

private fun PriceBreakdown.toDisplay() = PriceBreakdownDisplay(
    nights, pricePerNight, roomSubtotal, serviceFee, taxAmount, total
)

// ─────────────────────────────────────────────────────────────────────────────
// Main Screen
// ─────────────────────────────────────────────────────────────────────────────
@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmBookingScreen(
    propertyId: String,
    roomId: String,
    navController: NavController,
    viewModel: PropertyDetailsViewModel = run {
        val app = LocalContext.current.applicationContext as NamastaysApp
        viewModel(factory = PropertyDetailsViewModel.Factory(app.deps.propertyRepository))
    }) {
    // ── Fetch ─────────────────────────────────────────────────────────────────
    LaunchedEffect(propertyId) { viewModel.fetchPropertyDetails(propertyId) }

    // ── Observe uiState (lifecycle-aware — stops collecting when not STARTED,
    //    avoiding the leak/wasted-work risk of a raw collectAsState) ──────────
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val property: PropertyDetailsResponse? = (uiState as? PropertyDetailsUiState.Success)?.property
    val isLoading    = uiState is PropertyDetailsUiState.Loading
    val errorMessage = (uiState as? PropertyDetailsUiState.Error)?.message

    // ── Resolve room — memoised ───────────────────────────────────────────────
    val resolvedRoom: RoomResponse? = remember(property, roomId) {
        property?.rooms?.firstOrNull { it.id == roomId }
    }

    // ── Dates — saveable across rotation/process death via explicit Saver ────
    val today = remember { LocalDate.now() }
    var checkIn    by rememberSaveable(stateSaver = LocalDateSaver) { mutableStateOf(today.plusDays(1)) }
    var checkOutRaw by rememberSaveable(stateSaver = LocalDateSaver) { mutableStateOf(today.plusDays(3)) }

    // Derived, not corrected-in-place: checkOut can never be invalid, so there
    // is no mutate-during-composition step and no extra recomposition pass
    // needed to converge. Picker callbacks below only ever write checkOutRaw.
    val checkOut by remember(checkIn, checkOutRaw) {
        derivedStateOf { if (checkOutRaw.isAfter(checkIn)) checkOutRaw else checkIn.plusDays(1) }
    }

    // ── Guests ────────────────────────────────────────────────────────────────
    val maxGuests   = remember(resolvedRoom) { resolvedRoom?.maxGuestsInt() ?: 1 }
    var totalGuests by rememberSaveable { mutableIntStateOf(1) }

    LaunchedEffect(maxGuests) {
        if (totalGuests > maxGuests) totalGuests = maxGuests
    }

    // ── Additional guest names list — saveable, sized to (totalGuests - 1) ───
    val additionalCount = (totalGuests - 1).coerceAtLeast(0)
    val additionalNames = rememberSaveable(saver = StringListSaver) { mutableStateListOf<String>() }
    LaunchedEffect(additionalCount) {
        while (additionalNames.size < additionalCount) additionalNames.add("")
        while (additionalNames.size > additionalCount) additionalNames.removeLastOrNull()
    }

    // ── Primary guest fields ──────────────────────────────────────────────────
    var primaryName  by rememberSaveable { mutableStateOf("") }
    var primaryEmail by rememberSaveable { mutableStateOf("") }
    var primaryPhone by rememberSaveable { mutableStateOf("") }

    // ── Validation (hoisted regex — no per-keystroke allocation) ──────────────
    val nameError  = primaryName.isNotBlank()  && primaryName.trim().length < 2
    val emailError = primaryEmail.isNotBlank() &&
            !android.util.Patterns.EMAIL_ADDRESS.matcher(primaryEmail.trim()).matches()
    val phoneError = primaryPhone.isNotBlank() &&
            primaryPhone.replace(PhoneStripRegex, "").length < 7

    val canProceed = primaryName.trim().length >= 2 &&
            primaryEmail.isNotBlank() && !emailError &&
            primaryPhone.isNotBlank() && !phoneError &&
            totalGuests <= maxGuests &&
            (resolvedRoom?.pricePerNight ?: 0) > 0

    // ── Price — memoised, converted to a small stable carrier for the
    //    extracted PriceBreakdownSection ───────────────────────────────────────
    val pricePerNight = remember(resolvedRoom) {
        resolvedRoom?.pricePerNight?.toLong() ?: 0L
    }
    val bd = remember(checkIn, checkOut, pricePerNight) {
        calcBreakdown(checkIn, checkOut, pricePerNight).toDisplay()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Root
    // ─────────────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CbPageBg)
    ) {
        when {
            isLoading -> ConfirmBookingSkeleton()

            errorMessage != null -> EmptyState(
                icon        = Icons.Outlined.WifiOff,
                iconTint    = CbRedText,
                iconBg      = CbRedBg,
                title       = "Couldn't load booking details",
                body        = errorMessage,
                buttonLabel = "Go Back",
                onButton    = { navController.popBackStack() }
            )

            resolvedRoom == null && property != null -> EmptyState(
                icon        = Icons.Outlined.KingBed,
                iconTint    = CbSubtleText,
                iconBg      = CbTagBg,
                title       = "No room selected",
                body        = "Go back and select a room to continue.",
                buttonLabel = "Back to Property",
                onButton    = { navController.popBackStack() }
            )

            resolvedRoom?.totalRooms == 0 -> EmptyState(
                icon        = Icons.Outlined.Block,
                iconTint    = CbRedText,
                iconBg      = CbRedBg,
                title       = "Room no longer available",
                body        = "This room is fully booked. Choose a different one.",
                buttonLabel = "Choose Another Room",
                onButton    = { navController.popBackStack() }
            )

            property != null && resolvedRoom != null -> {
                val p    = property
                val room = resolvedRoom

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 110.dp)
                ) {

                    // ── TOP BAR ───────────────────────────────────────────
                    Surface(
                        modifier        = Modifier
                            .fillMaxWidth(),
                        color           = CbCardBg,
                        shadowElevation = 2.dp
                    ) {
                        Row(
                            modifier          = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier         = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(CbTagBg)
                                    .clickable { navController.popBackStack() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = CbPrimaryText, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text       = "Confirm Booking",
                                fontSize   = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = PlusJakartaSans,
                                color      = CbPrimaryText
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Column(
                        modifier            = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {

                        // ── 1. PROPERTY SUMMARY ───────────────────────────
                        PropertySummarySection(
                            propertyName = p.propertyName,
                            thumbnailUrl = p.imageUrls?.firstOrNull(),
                            checkIn      = checkIn,
                            checkOut     = checkOut,
                            nights       = bd.nights
                        )

                        // ── 2. ROOM SUMMARY ───────────────────────────────
                        RoomSummarySection(
                            room               = room,
                            totalGuests        = totalGuests,
                            breakfastIncluded  = p.breakfastIncluded == true,
                            checkInTime        = p.checkInTime,
                            checkOutTime       = p.checkOutTime,
                            checkIn            = checkIn,
                            checkOut           = checkOut,
                            today              = today,
                            onCheckInSelected  = { d ->
                                checkIn = d
                                if (!checkOutRaw.isAfter(d)) checkOutRaw = d.plusDays(1)
                            },
                            onCheckOutSelected = { d -> checkOutRaw = d }
                        )

                        // ── 3. GUEST DETAILS ──────────────────────────────
                        GuestDetailsSection(
                            primaryName            = primaryName,
                            onPrimaryNameChange     = { primaryName = it },
                            primaryEmail            = primaryEmail,
                            onPrimaryEmailChange    = { primaryEmail = it },
                            primaryPhone            = primaryPhone,
                            onPrimaryPhoneChange    = { primaryPhone = it },
                            nameError               = nameError,
                            emailError              = emailError,
                            phoneError              = phoneError,
                            additionalNames         = additionalNames,
                            onAdditionalNameChange  = { idx, value -> additionalNames[idx] = value },
                            totalGuests             = totalGuests,
                            maxGuests               = maxGuests,
                            onIncrementGuests       = { if (totalGuests < maxGuests) totalGuests++ },
                            onDecrementGuests       = { if (totalGuests > 1) totalGuests-- }
                        )

                        // ── 4. PRICE BREAKDOWN ────────────────────────────
                        PriceBreakdownSection(bd)

                        // ── 5. CANCELLATION POLICY ────────────────────────
                        if (!p.cancellationPolicy.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(CbAmberBg)
                                    .border(1.dp, CbAmberBorder, RoundedCornerShape(12.dp))
                                    .padding(14.dp)
                            ) {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Outlined.Info, null, tint = CbAmberIcon, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text          = "CANCELLATION POLICY",
                                            fontSize      = 10.sp,
                                            fontFamily    = PlusJakartaSans,
                                            fontWeight    = FontWeight.Bold,
                                            letterSpacing = 0.8.sp,
                                            color         = CbAmberIcon
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text       = p.cancellationPolicy,
                                        fontSize   = 13.sp,
                                        fontFamily = PlusJakartaSans,
                                        color      = CbAmberText,
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                        }

                    } // inner column
                    Spacer(Modifier.height(16.dp))
                } // scroll column

                // ── STICKY BOTTOM CTA ─────────────────────────────────────
                Surface(
                    modifier        = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    color           = CbCardBg,
                    shadowElevation = 16.dp,
                    shape           = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                ) {
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Total Payable", fontSize = 11.sp, color = CbSecondaryText, fontFamily = PlusJakartaSans)
                            Text(
                                text       = "NPR ${bd.total}",
                                fontSize   = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = PlusJakartaSans,
                                color      = CbAccentIndigo
                            )
                        }
                        Button(
                            onClick        = { /* navigate to payment */ },
                            enabled        = canProceed,
                            shape          = RoundedCornerShape(50.dp),
                            colors         = ButtonDefaults.buttonColors(
                                containerColor         = CbNavyDark,
                                disabledContainerColor = CbBorderGrey
                            ),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
                        ) {
                            Text(
                                text       = "Confirm & Pay",
                                fontSize   = 15.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = PlusJakartaSans,
                                color      = if (canProceed) Color.White else CbSubtleText
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward, null,
                                modifier = Modifier.size(16.dp),
                                tint     = if (canProceed) Color.White else CbSubtleText
                            )
                        }
                    }
                }
            } // happy path
        } // when
    } // root Box
}