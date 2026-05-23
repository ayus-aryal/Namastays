package com.example.namastays.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.namastays.R

// ─────────────────────────────────────────────────────────────────────────────
//  Palette
// ─────────────────────────────────────────────────────────────────────────────

private val PageBg      = Color(0xFFF7F8FA)
private val CardBg      = Color(0xFFFFFFFF)
private val CardBorder  = Color(0xFFE5E7EB)
private val TextPrimary = Color(0xFF111827)
private val TextMuted   = Color(0xFF6B7280)
private val TextHint    = Color(0xFF9CA3AF)
private val SelectedBg  = Color(0xFF111827)

// ─────────────────────────────────────────────────────────────────────────────
//  Data
// ─────────────────────────────────────────────────────────────────────────────

data class LLSQuestion(
    val label: String,
    val icon: ImageVector,
    val options: List<String>,
)

private val llsQuestions = listOf(
    LLSQuestion(
        label = "Headache",
        icon  = Icons.Outlined.Psychology,
        options = listOf("None", "Mild", "Moderate", "Severe"),
    ),
    LLSQuestion(
        label = "Gastrointestinal",
        icon  = Icons.Outlined.Restaurant,
        options = listOf("Appetite OK", "Poor Appetite", "Nausea", "Vomiting"),
    ),
    LLSQuestion(
        label = "Fatigue & Weakness",
        icon  = Icons.Outlined.BatteryAlert,
        options = listOf("Normal", "Mild", "Moderate", "Severe"),
    ),
    LLSQuestion(
        label = "Dizziness",
        icon  = Icons.Outlined.Autorenew,
        options = listOf("None", "Mild", "Moderate", "Severe"),
    ),
    LLSQuestion(
        label = "Overall AMS",
        icon  = Icons.Outlined.MonitorHeart,
        options = listOf("Not ill", "Mildly ill", "Moderately ill", "Very ill"),
    ),
)

private const val MAX_SCORE = 15

// ─────────────────────────────────────────────────────────────────────────────
//  Result model
// ─────────────────────────────────────────────────────────────────────────────

private data class LLSResult(
    val tag: String,
    val label: String,
    val advice: String,
    val accentColor: Color,
    val bgColor: Color,
    val borderColor: Color,
)

private fun evaluateScore(score: Int): LLSResult = when {
    score >= 10 -> LLSResult(
        tag = "Descend Immediately",
        label = "Severe AMS — Score $score / $MAX_SCORE",
        advice = "Immediate descent required. Do not continue ascent. Seek emergency medical attention now.",
        accentColor = Color(0xFFD32F2F),
        bgColor     = Color(0xFFFFF5F5),
        borderColor = Color(0xFFFFCDD2),
    )
    score >= 6 -> LLSResult(
        tag = "Do Not Ascend",
        label = "Moderate AMS — Score $score / $MAX_SCORE",
        advice = "Rest at current altitude. Do not ascend further. Descend if symptoms do not improve within 24 hours.",
        accentColor = Color(0xFFE65100),
        bgColor     = Color(0xFFFFF8F0),
        borderColor = Color(0xFFFFCCBC),
    )
    score >= 3 -> LLSResult(
        tag = "Rest & Monitor",
        label = "Mild AMS — Score $score / $MAX_SCORE",
        advice = "Monitor closely. Rest and hydrate at current altitude. Do not ascend until symptoms fully resolve.",
        accentColor = Color(0xFFF57F17),
        bgColor     = Color(0xFFFFFDE7),
        borderColor = Color(0xFFFFF176),
    )
    else -> LLSResult(
        tag = "All Clear",
        label = "No AMS — Score $score / $MAX_SCORE",
        advice = "No significant AMS symptoms detected. Stay well hydrated and continue to monitor your condition.",
        accentColor = Color(0xFF2E7D32),
        bgColor     = Color(0xFFF1F8E9),
        borderColor = Color(0xFFC5E1A5),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LakeLouiseScreen(navController: NavController) {
    val scores      = remember { mutableStateListOf(*IntArray(llsQuestions.size) { -1 }.toTypedArray()) }
    var showResult  by remember { mutableStateOf(false) }

    val allAnswered  = scores.none { it == -1 }
    val currentScore = if (allAnswered) scores.sum() else scores.filter { it >= 0 }.sum()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp, top = 28.dp, bottom = 100.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Back + Title ─────────────────────────────────────────────────
            item {
                ScreenHeader("AMS Checker", onBack = { navController.popBackStack() })
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Assess your symptoms using the Lake Louise Scoring System. " +
                            "Select the intensity that best describes your current state.",
                    fontFamily = PlusJakartaSans,
                    fontSize   = 13.sp,
                    color      = TextMuted,
                    lineHeight = 19.sp,
                )
                Spacer(Modifier.height(16.dp))
            }

            // ── Disclaimer ───────────────────────────────────────────────────
            item { DisclaimerBanner() }

            // ── Progress ─────────────────────────────────────────────────────
            item { ProgressCard(score = currentScore) }

            // ── Questions ────────────────────────────────────────────────────
            itemsIndexed(llsQuestions) { qIdx, question ->
                QuestionCard(
                    question      = question,
                    selectedIndex = scores[qIdx],
                    onSelect      = { optIdx ->
                        scores[qIdx] = optIdx
                        showResult = false
                    },
                )
            }

            // ── Result ───────────────────────────────────────────────────────
            if (showResult && allAnswered) {
                item {
                    val result = evaluateScore(currentScore)
                    ResultCard(result = result, score = currentScore)
                }
            }
        }

        // ── Floating button ──────────────────────────────────────────────────
        Button(
            onClick  = { if (allAnswered) showResult = true },
            enabled  = allAnswered,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp, start = 20.dp, end = 20.dp)
                .fillMaxWidth()
                .height(54.dp),
            shape  = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor         = TextPrimary,
                disabledContainerColor = Color(0xFFD1D5DB),
                contentColor           = Color.White,
                disabledContentColor   = Color(0xFF9CA3AF),
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Calculate,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text       = "Calculate Score",
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.Bold,
                fontSize   = 15.sp,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Disclaimer banner
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DisclaimerBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFEEF2FF))
            .border(1.dp, Color(0xFFC7D2FE), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment     = Alignment.Top,
    ) {
        Icon(
            imageVector        = Icons.Outlined.Info,
            contentDescription = null,
            tint               = Color(0xFF4338CA),
            modifier           = Modifier.size(16.dp).padding(top = 1.dp),
        )
        Text(
            text       = "The Lake Louise Score is a validated clinical standard for AMS assessment. " +
                    "It does not replace professional medical evaluation or diagnosis.",
            fontFamily = PlusJakartaSans,
            fontSize   = 12.sp,
            color      = Color(0xFF4338CA),
            lineHeight = 17.sp,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Progress card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProgressCard(score: Int) {
    val animatedProgress by animateFloatAsState(
        targetValue    = score / MAX_SCORE.toFloat(),
        animationSpec  = tween(durationMillis = 400),
        label          = "progress",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .border(1.dp, CardBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(
                text       = "VISUAL ASSESSMENT PROGRESS",
                fontFamily = PlusJakartaSans,
                fontSize   = 10.sp,
                fontWeight = FontWeight.Bold,
                color      = TextHint,
                letterSpacing = 1.sp,
            )
            Text(
                text       = "$score / $MAX_SCORE",
                fontFamily = PlusJakartaSans,
                fontSize   = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color      = TextPrimary,
            )
        }
        LinearProgressIndicator(
            progress      = { animatedProgress },
            modifier      = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(99.dp)),
            color         = AccentBlue,
            trackColor    = Color(0xFFF3F4F6),
            strokeCap     = StrokeCap.Round,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Question card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun QuestionCard(
    question: LLSQuestion,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Header row
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier          = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF3F4F6)),
                contentAlignment  = Alignment.Center,
            ) {
                Icon(
                    imageVector        = question.icon,
                    contentDescription = null,
                    tint               = TextMuted,
                    modifier           = Modifier.size(18.dp),
                )
            }
            Text(
                text       = question.label,
                fontFamily = PlusJakartaSans,
                fontSize   = 15.sp,
                fontWeight = FontWeight.Bold,
                color      = TextPrimary,
            )
        }

        // Option tiles
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            question.options.forEachIndexed { idx, label ->
                OptionTile(
                    modifier  = Modifier.weight(1f),
                    number    = idx,
                    label     = label,
                    selected  = selectedIndex == idx,
                    onClick   = { onSelect(idx) },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Option tile (0 / 1 / 2 / 3)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OptionTile(
    modifier: Modifier,
    number: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg     = if (selected) SelectedBg else CardBg
    val border = if (selected) SelectedBg else CardBorder
    val numCol = if (selected) Color.White else TextPrimary
    val lblCol = if (selected) Color(0xFF9CA3AF) else TextHint

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(2.dp, border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        horizontalAlignment     = Alignment.CenterHorizontally,
        verticalArrangement     = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text       = "$number",
            fontFamily = PlusJakartaSans,
            fontSize   = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color      = numCol,
            lineHeight = 20.sp,
        )
        Text(
            text       = label,
            fontFamily = PlusJakartaSans,
            fontSize   = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color      = lblCol,
            textAlign  = TextAlign.Center,
            lineHeight = 12.sp,
            maxLines   = 2,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Result card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ResultCard(result: LLSResult, score: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(result.bgColor)
            .border(1.5.dp, result.borderColor, RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Tag pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .border(1.5.dp, result.accentColor, RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text(
                text       = result.tag,
                fontFamily = PlusJakartaSans,
                fontSize   = 11.sp,
                fontWeight = FontWeight.Bold,
                color      = result.accentColor,
                letterSpacing = 0.5.sp,
            )
        }

        // Score + label
        Row(
            verticalAlignment     = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text       = "$score",
                fontFamily = PlusJakartaSans,
                fontSize   = 48.sp,
                fontWeight = FontWeight.ExtraBold,
                color      = result.accentColor,
                lineHeight = 48.sp,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text       = result.label,
                    fontFamily = PlusJakartaSans,
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color      = result.accentColor,
                )
                Text(
                    text       = "out of $MAX_SCORE",
                    fontFamily = PlusJakartaSans,
                    fontSize   = 12.sp,
                    color      = TextHint,
                )
            }
        }

        HorizontalDivider(color = result.borderColor, thickness = 1.dp)

        Text(
            text       = result.advice,
            fontFamily = PlusJakartaSans,
            fontSize   = 13.sp,
            color      = Color(0xFF374151),
            lineHeight = 20.sp,
        )
    }
}