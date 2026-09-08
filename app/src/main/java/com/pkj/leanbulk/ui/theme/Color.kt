package com.pkj.leanbulk.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 화면이 쓰는 색 한 벌 (UI-DESIGN-SPEC.md §1 / §7).
 *
 * **화면 코드에서 `Color(0x...)`를 직접 쓰지 않는다.** 라이트/다크가 갈리는 순간
 * 하드코딩한 색만 안 따라 바뀌어서, 다크모드에서 흰 카드에 흰 글씨가 나온다.
 * 항상 `ShaTheme.colors.xxx`로 가져다 쓴다.
 */
@Immutable
data class ShaColors(
    val canvas: Color,
    /** 화면 바탕. 카드보다 **어둡다** — 카드가 떠 보이는 건 그림자가 아니라 이 차이다. */
    val surface: Color,
    val surfaceRaised: Color,
    val border: Color,
    val borderStrong: Color,
    val muted: Color,
    val bodyText: Color,
    val ink: Color,
    val primary: Color,
    val primaryWeakBg: Color,
    val primaryWeakFg: Color,
    val success: Color,
    val danger: Color
)

val LightShaColors = ShaColors(
    canvas = Color(0xFFFFFFFF),
    surface = Color(0xFFF2F4F6),
    surfaceRaised = Color(0xFFFFFFFF),
    border = Color(0xFFE5E8EB),
    borderStrong = Color(0xFFD1D6DB),
    muted = Color(0xFF8B95A1),
    bodyText = Color(0xFF4E5968),
    ink = Color(0xFF191F28),
    primary = Color(0xFF3182F6),
    primaryWeakBg = Color(0xFFE8F3FF),
    primaryWeakFg = Color(0xFF1B64DA),
    success = Color(0xFF06B26B),
    danger = Color(0xFFE42939)
)

val DarkShaColors = ShaColors(
    canvas = Color(0xFF111418),
    surface = Color(0xFF1A1E24),
    surfaceRaised = Color(0xFF20252C),
    border = Color(0xFF2B3138),
    borderStrong = Color(0xFF3A414A),
    muted = Color(0xFF7B8794),
    bodyText = Color(0xFFB8C0CC),
    ink = Color(0xFFF2F4F6),
    primary = Color(0xFF5B9DFF),
    primaryWeakBg = Color(0xFF16273F),
    primaryWeakFg = Color(0xFF8FC0FF),
    success = Color(0xFF33D698),
    danger = Color(0xFFFF6B70)
)

val LocalShaColors = staticCompositionLocalOf { LightShaColors }
