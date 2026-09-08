package com.pkj.leanbulk.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 화면이 쓰는 색 한 벌.
 *
 * **화면 코드에서 `Color(0x...)`를 직접 쓰지 않는다.** 라이트/다크가 갈리는 순간
 * 하드코딩한 색만 안 따라 바뀌어서, 다크모드에서 흰 카드에 흰 글씨가 나온다.
 * 항상 `ShaTheme.colors.xxx`로 가져다 쓴다.
 *
 * ## 라임은 왜 두 갈래로 쓰나
 *
 * 브랜드색은 런처 아이콘의 라임 `#6FE26C`이다. 그런데 이 색은 **밝아서 글자 색으로 못 쓴다** —
 * 흰 바탕 위 라임 글자는 대비가 1.6:1이라(기준 4.5:1) 사실상 안 보인다. 흰 글자를 라임 위에
 * 얹는 것도 똑같이 1.6:1이다.
 *
 * 그래서 역할을 갈랐다:
 *
 * | 쓰임 | 밝은 테마 | 어두운 테마 | 대비 |
 * |---|---|---|---|
 * | 채우는 색 ([primaryFill]) | 잉크 + 라임 글자 | 라임 + 잉크 글자 | 11.1:1 |
 * | 글자·아이콘 ([primary]) | 딥그린 `#2E7D32` | 라임 | 5.1 / 11.1 |
 *
 * **채우는 색은 늘 배경의 반대다.** 어두운 테마는 바탕 자체가 잉크라, 잉크로 채운 버튼이
 * 배경에 묻힌다. 뒤집으면 같은 색 한 쌍(잉크·라임)으로 양쪽 테마가 다 해결된다.
 *
 * 글자용은 다르다 — 라임은 밝아서 흰 바탕 위에서 1.6:1이라 안 보이므로 밝은 테마에서만
 * 딥그린으로 내려 쓴다. 어두운 테마는 라임이 바탕과 11:1이라 그대로 쓴다.
 *
 * 이 규칙을 어기면(라임을 링크 글자에 쓰면) 화면은 예뻐 보이는데 밝은 데서 안 읽힌다.
 */
@Immutable
data class ShaColors(
    val canvas: Color,
    /** 화면 바탕. 카드보다 **어둡다** — 카드가 떠 보이는 건 그림자가 아니라 이 차이다. */
    val surface: Color,
    val surfaceRaised: Color,
    val border: Color,
    val borderStrong: Color,
    /** 보조 글자. 캡션도 작은 글자라 4.5:1을 넘겨 잡았다. */
    val muted: Color,
    val bodyText: Color,
    val ink: Color,
    /** **글자·아이콘용** 브랜드색. 채우는 데 쓰지 않는다. */
    val primary: Color,
    /** **채우는 용** 브랜드색(버튼·진행막대·선택칩). 위에 얹는 글자는 [onPrimaryFill]. */
    val primaryFill: Color,
    val onPrimaryFill: Color,
    val primaryWeakBg: Color,
    val primaryWeakFg: Color,
    val success: Color,
    val danger: Color
)

/** 런처 아이콘과 같은 값. 테마가 바뀌어도 이 값은 안 바뀐다 — 브랜드색이 변하면 브랜드가 아니다. */
val BrandLime = Color(0xFF6FE26C)
val BrandInk = Color(0xFF16150F)

val LightShaColors = ShaColors(
    canvas = Color(0xFFFFFFFF),
    // 중립색을 살짝 따뜻하게(초록 쪽으로) 민다. 파랑기 도는 회색은 라임과 같이 두면 서로 튄다.
    surface = Color(0xFFF4F5F2),
    surfaceRaised = Color(0xFFFFFFFF),
    border = Color(0xFFE6E8E3),
    borderStrong = Color(0xFFD2D5CD),
    muted = Color(0xFF6E7269),
    bodyText = Color(0xFF4E5348),
    ink = BrandInk,
    primary = Color(0xFF2E7D32),
    primaryFill = BrandInk,
    onPrimaryFill = BrandLime,
    primaryWeakBg = Color(0xFFE8F8E7),
    primaryWeakFg = Color(0xFF2E7D32),
    // 목표 도달 같은 "잘 됐다"는 브랜드색과 같은 갈래다. 굳이 다른 초록을 하나 더 두지 않는다.
    success = Color(0xFF2E7D32),
    danger = Color(0xFFD32F2F)
)

val DarkShaColors = ShaColors(
    canvas = Color(0xFF16150F),
    surface = Color(0xFF16150F),
    surfaceRaised = Color(0xFF22211B),
    border = Color(0xFF2E2D26),
    borderStrong = Color(0xFF3D3C33),
    muted = Color(0xFF8E938A),
    bodyText = Color(0xFFB9BDB3),
    ink = Color(0xFFF4F5F2),
    // 어두운 바탕에서는 라임이 11:1이라 글자로도 쓸 수 있다.
    primary = BrandLime,
    primaryFill = BrandLime,
    onPrimaryFill = BrandInk,
    primaryWeakBg = Color(0xFF1D2A1C),
    primaryWeakFg = BrandLime,
    success = BrandLime,
    danger = Color(0xFFFF6B70)
)

val LocalShaColors = staticCompositionLocalOf { LightShaColors }
