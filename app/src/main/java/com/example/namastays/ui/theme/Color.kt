package com.example.namastays.ui.theme

import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
// FIX #4 (audit): The unused default Material starter palette (Purple80,
// PurpleGrey80, Pink80, Purple40, PurpleGrey40, Pink40) has been removed.
// It was kept previously "in case Theme.kt still referenced it," but it was
// confirmed unused by every screen in the Explore-by-City flow and is not
// referenced by any composable shown in this audit. If Theme.kt's
// MaterialTheme color scheme still points at these names, that will now
// fail to compile — which is the correct outcome: it means Theme.kt needs
// to be updated to use the real design tokens below instead of Compose's
// generated starter colors. Re-add only if Theme.kt is confirmed to need it.
// ─────────────────────────────────────────────────────────────────────────────

// ── Core palette (used across Home, Search, Property, Booking, Packing) ─────
val BackgroundColor   = Color(0xFFF7F8FA)
val CardWhite         = Color(0xFFFFFFFF)
val PrimaryText       = Color(0xFF111827)
val SecondaryText     = Color(0xFF6B7280)
val SubtleText        = Color(0xFF9CA3AF)
val AccentBlue        = Color(0xFF4F46E5)
val AccentGreen       = Color(0xFF22C55E)
val DestructiveRed    = Color(0xFFEF4444)
val BorderColor       = Color(0xFFE5E7EB)
val SkeletonBase      = Color(0xFFE5E7EB)
val SkeletonHighlight = Color(0xFFF3F4F6)
val BottomNavSelected = Color(0xFF111827)
val BottomNavUnsel    = Color(0xFF9CA3AF)
val OnlineGreen       = Color(0xFF22C55E)

// ── Category icon colors (HomeScreen's CategoriesRow) ───────────────────────
val HotelIconBg    = Color(0xFFEEF2FF)
val HotelIconColor = Color(0xFF4F46E5)
val HomeIconBg     = Color(0xFFDCFCE7)
val HomeIconColor  = Color(0xFF16A34A)
val ToursIconBg    = Color(0xFFFFF7ED)
val ToursIconColor = Color(0xFFEA580C)

// ── SearchResultsScreen-specific tokens (HTML-design-matched palette) ───────
// Note: these intentionally use slightly different hex values than the core
// palette above (e.g. PrimaryIndigo vs AccentBlue) because they were matched
// pixel-for-pixel against a separate HTML mockup. Left distinct rather than
// merged into the core palette, since changing them would visibly shift
// SearchResultsScreen's look away from its approved design.
val PageBackground   = Color(0xFFF7F8FA)
val PrimaryIndigo    = Color(0xFF4648D4)
val PrimaryContainer = Color(0xFF6063EE)
val SurfaceContainer = Color(0xFFEFECF8)
val SurfaceVariant   = Color(0xFFE4E1ED)
val OutlineVariant   = Color(0xFFC7C4D7)
val OnSurface        = Color(0xFF1B1B23)
val OnSurfaceVariant = Color(0xFF464554)
val OutlineColor     = Color(0xFF767586)
val StarAmber        = Color(0xFF703700)
val FilterBg         = Color(0xFFF3F4F6)
val FilterBorder     = Color(0xFFE5E7EB)
val ShimmerBase      = Color(0xFFF0F0F0)
val ShimmerHighlight = Color(0xFFF8F8F8)

// ── PropertyDetailsScreen-specific tokens ────────────────────────────────────
val Primary          = Color(0xFF4648D4)
val SecContainer     = Color(0xFFD9DFF5)
val OnSecContainer   = Color(0xFF5C6274)
val ErrorColor       = Color(0xFFBA1A1A)
val ErrorContainer   = Color(0xFFFFDAD6)
val OnErrorContainer = Color(0xFF93000A)
val SurfaceLow       = Color(0xFFF5F2FE)
val PageBg           = Color(0xFFF7F8FA)
val NavyDark         = Color(0xFF111827)
val SelectedRoomBg   = Color(0xFFEEF2FF)
val SkeletonHigh     = Color(0xFFF9FAFB)

// ── ConfirmBookingScreen-specific tokens (Cb prefix = "Confirm booking") ────
val CbPageBg        = Color(0xFFF4F5F9)
val CbCardBg        = Color(0xFFFFFFFF)
val CbPrimaryText   = Color(0xFF111827)
val CbSecondaryText = Color(0xFF6B7280)
val CbSubtleText    = Color(0xFF9CA3AF)
val CbBorderGrey    = Color(0xFFE5E7EB)
val CbAccentIndigo  = Color(0xFF4F46E5)
val CbNavyDark      = Color(0xFF1E1B4B)
val CbAmberBg       = Color(0xFFFFFBEB)
val CbAmberBorder   = Color(0xFFFDE68A)
val CbAmberText     = Color(0xFF92400E)
val CbAmberIcon     = Color(0xFFB45309)
val CbRedBg         = Color(0xFFFEE2E2)
val CbRedText       = Color(0xFFDC2626)
val CbTagBg         = Color(0xFFF3F4F6)
val CbIndigoBg      = Color(0xFFEEF2FF)
val CbSelectedBg    = Color(0xFFEEF2FF)
val CbSelectedText  = Color(0xFF4F46E5)
val CbDivider       = Color(0xFFF3F4F6)
val CbInputBorder   = Color(0xFFE5E7EB)
val CbInputBg       = Color(0xFFFAFAFA)

// ─────────────────────────────────────────────────────────────────────────────
// FIX #2 / #3 (audit) — Explore-by-City flow tokens.
//
// DECISION (flagging explicitly rather than presuming): the four
// Explore-by-City screens previously used THREE different ad-hoc accent
// blues across the flow (#2563EB in ExploreScreen, #4A80F0 in
// PlaceDetailScreen/PlaceListScreen's hero gradient, and #6366F1 in
// PlaceListScreen's pills), plus two different screen backgrounds
// (#F7F8FA vs #F2F2F7). None of these matched an existing named token
// exactly except AccentBlue (#4F46E5), which is already the indigo used by
// HomeScreen/ConfirmBookingScreen/etc.
//
// Rather than inventing a fourth "ExploreAccent" color, this flow is
// standardized onto the EXISTING core palette (BackgroundColor, AccentBlue,
// PrimaryText, SecondaryText, BorderColor, DestructiveRed) so it visually
// matches the rest of the app instead of looking like a separately-designed
// section. If there was a deliberate reason Explore was meant to look
// visually distinct from Home/Booking, this should be flagged back — this
// fix assumes that wasn't intentional, since the three blues used didn't
// even agree with each other.
// ─────────────────────────────────────────────────────────────────────────────