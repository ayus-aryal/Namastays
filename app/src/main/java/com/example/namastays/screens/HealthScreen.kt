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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

// ─── Palette ──────────────────────────────────────────────────────────────────
private val LlPageBg      = Color(0xFFF7F8FA)
private val LlCardBg      = Color(0xFFFFFFFF)
private val LlCardBorder  = Color(0xFFE5E7EB)
private val LlTextPrimary = Color(0xFF111827)
private val LlTextMuted   = Color(0xFF6B7280)
private val LlTextHint    = Color(0xFF9CA3AF)
private val LlSelectedBg  = Color(0xFF111827)

// ─── Data ─────────────────────────────────────────────────────────────────────
data class LLSQuestion(
    val label: String,
    val icon: ImageVector,
    val options: List<String>,
)

private val llsQuestions = listOf(
    LLSQuestion(
        label   = "Headache",
        icon    = Icons.Outlined.Psychology,
        options = listOf("None", "Mild", "Moderate", "Severe"),
    ),
    LLSQuestion(
        label   = "Gastrointestinal",
        icon    = Icons.Outlined.Restaurant,
        options = listOf("Appetite OK", "Poor Appetite", "Nausea", "Vomiting"),
    ),
    LLSQuestion(
        label   = "Fatigue & Weakness",
        icon    = Icons.Outlined.BatteryAlert,
        options = listOf("Normal", "Mild", "Moderate", "Severe"),
    ),
    LLSQuestion(
        label   = "Dizziness",
        icon    = Icons.Outlined.Autorenew,
        options = listOf("None", "Mild", "Moderate", "Severe"),
    ),
    LLSQuestion(
        label   = "Overall AMS",
        icon    = Icons.Outlined.MonitorHeart,
        options = listOf("Not ill", "Mildly ill", "Moderately ill", "Very ill"),
    ),
)

private const val MAX_SCORE = 15

// ─── Result model ──────────────────────────────────────────────────────────────
private data class LLSResult(
    val tag: String,
    val label: String,
    val advice: String,
    val accentColor: Color,
    val bgColor: Color,
    val borderColor: Color,
    val progressColor: Color,
)

private fun evaluateScore(score: Int): LLSResult = when {
    score >= 10 -> LLSResult(
        tag          = "Descend Immediately",
        label        = "Severe AMS — Score $score / $MAX_SCORE",
        advice       = "Immediate descent is required. Do not continue ascending. Seek emergency medical attention now — use your SOS feature if needed.",
        accentColor  = Color(0xFFD32F2F),
        bgColor      = Color(0xFFFFF5F5),
        borderColor  = Color(0xFFFFCDD2),
        progressColor = Color(0xFFD32F2F),
    )
    score >= 6 -> LLSResult(
        tag          = "Do Not Ascend",
        label        = "Moderate AMS — Score $score / $MAX_SCORE",
        advice       = "Rest at your current altitude. Do not go higher. If symptoms do not improve within 24 hours, descend immediately.",
        accentColor  = Color(0xFFE65100),
        bgColor      = Color(0xFFFFF8F0),
        borderColor  = Color(0xFFFFCCBC),
        progressColor = Color(0xFFE65100),
    )
    score >= 3 -> LLSResult(
        tag          = "Rest & Monitor",
        label        = "Mild AMS — Score $score / $MAX_SCORE",
        advice       = "Rest and hydrate at your current altitude. Monitor closely. Do not ascend until all symptoms fully resolve.",
        accentColor  = Color(0xFFF57F17),
        bgColor      = Color(0xFFFFFDE7),
        borderColor  = Color(0xFFFFF176),
        progressColor = Color(0xFFF57F17),
    )
    else -> LLSResult(
        tag          = "All Clear",
        label        = "No AMS — Score $score / $MAX_SCORE",
        advice       = "No significant AMS symptoms detected. Stay well hydrated, ascend gradually, and continue monitoring your condition.",
        accentColor  = Color(0xFF2E7D32),
        bgColor      = Color(0xFFF1F8E9),
        borderColor  = Color(0xFFC5E1A5),
        progressColor = Color(0xFF22C55E),
    )
}

private fun progressColorForScore(score: Int): Color = when {
    score >= 10 -> Color(0xFFD32F2F)
    score >= 6  -> Color(0xFFE65100)
    score >= 3  -> Color(0xFFF57F17)
    else        -> AccentBlue
}

// ─── Screen ───────────────────────────────────────────────────────────────────
@Composable
fun LakeLouiseScreen(navController: NavController) {
    val scores     = remember { mutableStateListOf(*IntArray(llsQuestions.size) { -1 }.toTypedArray()) }
    var showResult by remember { mutableStateOf(false) }

    val allAnswered  = scores.none { it == -1 }
    val answeredCount = scores.count { it >= 0 }
    val currentScore  = scores.filter { it >= 0 }.sum()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LlPageBg)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp, top = 10.dp, bottom = 110.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Header ─────────────────────────────────────────────────────────
            item {
                ScreenHeader("AMS Checker", onBack = { navController.popBackStack() })
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Assess your symptoms using the Lake Louise Scoring System. Select the intensity that best describes your current state.",
                    fontFamily = PlusJakartaSans,
                    fontSize   = 13.sp,
                    color      = LlTextMuted,
                    lineHeight = 19.sp,
                )
                Spacer(Modifier.height(16.dp))
            }

            // ── Disclaimer ─────────────────────────────────────────────────────
            item {
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
                        text       = "The Lake Louise Score is a validated clinical standard for AMS assessment. It does not replace professional medical evaluation or diagnosis.",
                        fontFamily = PlusJakartaSans,
                        fontSize   = 12.sp,
                        color      = Color(0xFF4338CA),
                        lineHeight = 17.sp,
                    )
                }
            }

            // ── Progress card ──────────────────────────────────────────────────
            item {
                val animatedProgress by animateFloatAsState(
                    targetValue   = currentScore / MAX_SCORE.toFloat(),
                    animationSpec = tween(durationMillis = 400),
                    label         = "progress",
                )
                val progressColor = progressColorForScore(currentScore)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(LlCardBg)
                        .border(1.dp, LlCardBorder, RoundedCornerShape(14.dp))
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                "SCORE",
                                fontFamily    = PlusJakartaSans,
                                fontSize      = 10.sp,
                                fontWeight    = FontWeight.Bold,
                                color         = LlTextHint,
                                letterSpacing = 1.sp,
                            )
                            Text(
                                "$currentScore / $MAX_SCORE",
                                fontFamily = PlusJakartaSans,
                                fontSize   = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color      = if (currentScore == 0) LlTextPrimary else progressColor,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "QUESTIONS",
                                fontFamily    = PlusJakartaSans,
                                fontSize      = 10.sp,
                                fontWeight    = FontWeight.Bold,
                                color         = LlTextHint,
                                letterSpacing = 1.sp,
                            )
                            Text(
                                "$answeredCount / ${llsQuestions.size} answered",
                                fontFamily = PlusJakartaSans,
                                fontSize   = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color      = LlTextMuted,
                            )
                        }
                    }
                    LinearProgressIndicator(
                        progress      = { animatedProgress },
                        modifier      = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(99.dp)),
                        color         = progressColor,
                        trackColor    = Color(0xFFF3F4F6),
                        strokeCap     = StrokeCap.Round,
                    )
                    if (!allAnswered) {
                        Text(
                            "Answer all ${llsQuestions.size} questions to calculate your score",
                            fontFamily = PlusJakartaSans,
                            fontSize   = 11.sp,
                            color      = LlTextHint,
                        )
                    }
                }
            }

            // ── Questions ──────────────────────────────────────────────────────
            itemsIndexed(llsQuestions) { qIdx, question ->
                QuestionCard(
                    question      = question,
                    questionNumber = qIdx + 1,
                    selectedIndex = scores[qIdx],
                    onSelect      = { optIdx ->
                        scores[qIdx] = optIdx
                        showResult = false
                    },
                )
            }

            // ── Result ─────────────────────────────────────────────────────────
            if (showResult && allAnswered) {
                item {
                    val result = evaluateScore(currentScore)
                    ResultCard(
                        result = result,
                        score  = currentScore,
                        onReset = {
                            for (i in scores.indices) scores[i] = -1
                            showResult = false
                        }
                    )
                }
            }
        }

        // ── Floating CTA ──────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(LlPageBg.copy(alpha = 0f), LlPageBg)
                    )
                )
                .padding(bottom = 24.dp, start = 20.dp, end = 20.dp, top = 16.dp)
        ) {
            Button(
                onClick  = { if (allAnswered) showResult = true },
                enabled  = allAnswered,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape  = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor         = LlTextPrimary,
                    disabledContainerColor = Color(0xFFE5E7EB),
                    contentColor           = Color.White,
                    disabledContentColor   = LlTextHint,
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
            ) {
                Icon(imageVector = Icons.Outlined.Calculate, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text       = if (allAnswered) "Calculate Score" else "Answer all questions to continue",
                    fontFamily = PlusJakartaSans,
                    fontWeight = FontWeight.Bold,
                    fontSize   = if (allAnswered) 15.sp else 13.sp,
                )
            }
        }
    }
}

// ─── Question card ─────────────────────────────────────────────────────────────
@Composable
private fun QuestionCard(
    question: LLSQuestion,
    questionNumber: Int,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val isAnswered = selectedIndex >= 0
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(LlCardBg)
            .border(
                width = if (isAnswered) 1.5.dp else 1.dp,
                color = if (isAnswered) Color(0xFF6366F1).copy(alpha = 0.4f) else LlCardBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isAnswered) Color(0xFFEEF2FF) else Color(0xFFF3F4F6)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector        = question.icon,
                    contentDescription = null,
                    tint               = if (isAnswered) Color(0xFF6366F1) else LlTextMuted,
                    modifier           = Modifier.size(18.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = question.label,
                    fontFamily = PlusJakartaSans,
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color      = LlTextPrimary,
                )
                Text(
                    text       = "Question $questionNumber of ${llsQuestions.size}",
                    fontFamily = PlusJakartaSans,
                    fontSize   = 11.sp,
                    color      = LlTextHint,
                )
            }
            if (isAnswered) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF22C55E)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = "Answered", tint = Color.White, modifier = Modifier.size(13.dp))
                }
            }
        }

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            question.options.forEachIndexed { idx, label ->
                OptionTile(
                    modifier = Modifier.weight(1f),
                    number   = idx,
                    label    = label,
                    selected = selectedIndex == idx,
                    onClick  = { onSelect(idx) },
                )
            }
        }
    }
}

// ─── Option tile ───────────────────────────────────────────────────────────────
@Composable
private fun OptionTile(
    modifier: Modifier,
    number: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg     = if (selected) LlSelectedBg else LlCardBg
    val border = if (selected) LlSelectedBg else LlCardBorder
    val numCol = if (selected) Color.White else LlTextPrimary
    val lblCol = if (selected) Color(0xFF9CA3AF) else LlTextHint

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(2.dp, border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
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

// ─── Result card ───────────────────────────────────────────────────────────────
@Composable
private fun ResultCard(result: LLSResult, score: Int, onReset: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(result.bgColor)
            .border(1.5.dp, result.borderColor, RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Tag pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .border(1.5.dp, result.accentColor, RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text(
                text          = result.tag,
                fontFamily    = PlusJakartaSans,
                fontSize      = 11.sp,
                fontWeight    = FontWeight.Bold,
                color         = result.accentColor,
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
                    color      = LlTextHint,
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

        HorizontalDivider(color = result.borderColor, thickness = 1.dp)

        // Reset button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(result.accentColor.copy(alpha = 0.08f))
                .border(1.dp, result.accentColor.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                .clickable(onClick = onReset)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Refresh, contentDescription = null, tint = result.accentColor, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "Re-assess",
                fontFamily = PlusJakartaSans,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 13.sp,
                color      = result.accentColor,
            )
        }
    }
}