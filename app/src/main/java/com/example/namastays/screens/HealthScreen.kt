package com.example.namastays.screens

import androidx.compose.ui.draw.clip


import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.navigation.NavController

// ═════════════════════════════════════════════════════════════════════════════
//  AMS CHECKER — Yes/No symptom questionnaire
// ═════════════════════════════════════════════════════════════════════════════

data class AMSQuestion(val text: String, val serious: Boolean = false)

val amsQuestions = listOf(
    AMSQuestion("Do you have a headache?"),
    AMSQuestion("Do you feel nauseous or have vomited?"),
    AMSQuestion("Do you feel unusually fatigued or weak?"),
    AMSQuestion("Do you have dizziness or light-headedness?"),
    AMSQuestion("Do you have difficulty sleeping?"),
    AMSQuestion("Do you have shortness of breath at rest?", serious = true),
    AMSQuestion("Do you have chest tightness?", serious = true),
    AMSQuestion("Are you confused or disoriented?", serious = true),
    AMSQuestion("Do you have difficulty walking straight?", serious = true),
    AMSQuestion("Do you have facial or hand swelling?", serious = true)
)

@Composable
fun AMSCheckerScreen(navController: NavController) {
    val answers = remember { mutableStateListOf<Boolean?>(*arrayOfNulls(amsQuestions.size)) }
    var showResult by remember { mutableStateOf(false) }

    val bgColor = Color(0xFF1A1A2E)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenHeader("AMS Checker", onBack = { navController.popBackStack() })

        InfoBanner(
            icon = Icons.Default.Info,
            text = "Acute Mountain Sickness (AMS) can be life-threatening. Answer honestly.",
            color = Color(0xFF2196F3)
        )

        amsQuestions.forEachIndexed { index, question ->
            AMSQuestionCard(
                question = question,
                answer = answers[index],
                onAnswer = { answers[index] = it }
            )
        }

        // ── Check button ─────────────────────────────────────────────────────
        val allAnswered = answers.none { it == null }
        Button(
            onClick = { showResult = true },
            enabled = allAnswered,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Assess My Risk", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        // ── Result card ───────────────────────────────────────────────────────
        if (showResult && allAnswered) {
            AMSResultCard(answers = answers.filterNotNull())
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun AMSQuestionCard(
    question: AMSQuestion,
    answer: Boolean?,
    onAnswer: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF16213E))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (question.serious) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFFF5252),
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = question.text,
            color = Color.White,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
            lineHeight = 18.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AnswerButton(label = "Yes", selected = answer == true, color = Color(0xFFFF5252)) { onAnswer(true) }
            AnswerButton(label = "No",  selected = answer == false, color = Color(0xFF4CAF50)) { onAnswer(false) }
        }
    }
}

@Composable
fun AnswerButton(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) color else color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (selected) Color.White else color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AMSResultCard(answers: List<Boolean>) {
    val yesCount = answers.count { it }
    val seriousYes = amsQuestions.filterIndexed { i, q -> q.serious && answers.getOrElse(i) { false } }

    val (severity, color, message) = when {
        seriousYes.isNotEmpty() -> Triple("SEVERE — Descend Immediately", Color(0xFFD32F2F),
            "You have serious HACE/HAPE symptoms. Descend at least 300m immediately and seek medical help.")
        yesCount >= 3           -> Triple("MODERATE AMS", Color(0xFFFF9800),
            "You show moderate AMS. Rest, hydrate, do not ascend further. Descend if symptoms worsen.")
        yesCount >= 1           -> Triple("MILD AMS", Color(0xFFFFEB3B),
            "Mild symptoms detected. Rest at current altitude. Monitor closely and do not ascend.")
        else                    -> Triple("No AMS Detected", Color(0xFF4CAF50),
            "No significant AMS symptoms. Stay hydrated and continue to monitor.")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.1f))
            .border(2.dp, color, RoundedCornerShape(16.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(severity, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        Text(message, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, lineHeight = 19.sp)
        if (seriousYes.isNotEmpty()) {
            Text(
                "⚠ Seek medical attention immediately",
                color = Color(0xFFFF5252),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}


// ═════════════════════════════════════════════════════════════════════════════
//  LAKE LOUISE SCORE
// ═════════════════════════════════════════════════════════════════════════════

data class LLQuestion(val symptom: String, val options: List<String>)

val llQuestions = listOf(
    LLQuestion(
        "Headache",
        listOf("None", "Mild", "Moderate", "Severe, incapacitating")
    ),
    LLQuestion(
        "Gastrointestinal symptoms",
        listOf("None", "Poor appetite or nausea", "Moderate nausea/vomiting", "Severe nausea/vomiting")
    ),
    LLQuestion(
        "Fatigue and/or weakness",
        listOf("None", "Mild fatigue", "Moderate fatigue", "Severe, incapacitating")
    ),
    LLQuestion(
        "Dizziness / light-headedness",
        listOf("None", "Mild", "Moderate", "Severe, incapacitating")
    )
)

@Composable
fun LakeLouiseScreen(navController: NavController) {
    val scores = remember { mutableStateListOf(*IntArray(llQuestions.size) { -1 }.toTypedArray()) }
    var showResult by remember { mutableStateOf(false) }
    val totalScore = if (scores.none { it == -1 }) scores.sum() else -1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenHeader("Lake Louise AMS Score", onBack = { navController.popBackStack() })

        InfoBanner(
            icon = Icons.Default.Assignment,
            text = "Score each symptom based on the past hour at current altitude.",
            color = Color(0xFF9C27B0)
        )

        llQuestions.forEachIndexed { index, question ->
            LLQuestionCard(
                question = question,
                selectedIndex = scores[index],
                onSelect = { scores[index] = it }
            )
        }

        val allAnswered = scores.none { it == -1 }
        Button(
            onClick = { showResult = true },
            enabled = allAnswered,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Calculate Score", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        if (showResult && totalScore >= 0) {
            LakeLouiseResultCard(totalScore)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun LLQuestionCard(question: LLQuestion, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF16213E))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(question.symptom, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        question.options.forEachIndexed { idx, label ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selectedIndex == idx)
                            Color(0xFF9C27B0).copy(alpha = 0.3f)
                        else Color.Transparent
                    )
                    .clickable { onSelect(idx) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RadioButton(
                    selected = selectedIndex == idx,
                    onClick = { onSelect(idx) },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = Color(0xFF9C27B0),
                        unselectedColor = Color.White.copy(alpha = 0.4f)
                    )
                )
                Text("$idx – $label", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun LakeLouiseResultCard(score: Int) {
    val (label, color, advice) = when {
        score >= 5 -> Triple("Severe AMS (Score $score)", Color(0xFFD32F2F),
            "Immediate descent required. Do not continue ascent. Seek medical attention.")
        score >= 3 -> Triple("Moderate AMS (Score $score)", Color(0xFFFF9800),
            "Rest at current altitude. Do not ascend. Descend if no improvement in 24h.")
        score == 2 -> Triple("Mild AMS (Score $score)", Color(0xFFFFEB3B),
            "Monitor closely. Rest, hydrate, and do not ascend until fully recovered.")
        else       -> Triple("No AMS (Score $score)", Color(0xFF4CAF50),
            "Altitude acclimatization appears normal. Continue to hydrate and monitor.")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.1f))
            .border(2.dp, color, RoundedCornerShape(16.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "$score",
                color = color,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 40.sp
            )
            Column {
                Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("out of 12", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            }
        }
        HorizontalDivider(color = color.copy(alpha = 0.3f))
        Text(advice, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, lineHeight = 19.sp)
    }
}

// ─── Shared helper ────────────────────────────────────────────────────────────
@Composable
fun InfoBanner(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.1f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(text, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, lineHeight = 17.sp)
    }
}