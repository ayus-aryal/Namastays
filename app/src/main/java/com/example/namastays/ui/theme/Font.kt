package com.example.namastays.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.namastays.R

/**
 * App-wide font families, moved here from HomeScreen.kt so that every screen
 * importing PlusJakartaSans / PlusJakartaSansBold shares a single FontFamily
 * instance instead of each screen file declaring (and potentially
 * re-allocating) its own copy.
 *
 * Usage: import com.example.namastays.ui.theme.PlusJakartaSans
 */
val PlusJakartaSans = FontFamily(
    Font(R.font.plusjakartasans, FontWeight.Normal)
)

val PlusJakartaSansBold = FontFamily(
    Font(R.font.plusjakartasansbold, FontWeight.Bold)
)