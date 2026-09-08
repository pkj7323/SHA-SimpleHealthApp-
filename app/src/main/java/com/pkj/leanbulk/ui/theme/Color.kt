package com.pkj.leanbulk.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

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

/**
 * 브랜드색. 런처 아이콘과 같은 값이고, 테마가 바뀌어도 이 값은 안 바뀐다 —
 * 브랜드색이 테마 따라 변하면 브랜드가 아니다.
 *
 * **아래의 초록 계열은 전부 이 값에서 파생된다.** 밝은 테마용 딥그린을 따로 박아두면
 * 브랜드색을 바꿔도 그것만 안 따라와서 화면 절반이 옛 색으로 남는다(실제로 그랬다).
 */
val BrandLime = Color(0xFF5CE3A2)
val BrandInk = Color(0xFF16150F)

/** 두 색의 명도 대비. 작은 글자는 4.5:1, 큰 글자·아이콘은 3:1이 최소다. */
fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    val hi = max(la, lb)
    val lo = min(la, lb)
    return (hi + 0.05f) / (lo + 0.05f)
}

/**
 * [background] 위에서 [minRatio]를 넘을 때까지 어둡게 내린 색.
 *
 * 라임은 밝아서 흰 바탕 위 글자로 못 쓴다(1.6:1). 그렇다고 딥그린을 따로 적어두면
 * 브랜드색과 따로 놀므로, **같은 색을 읽힐 때까지 내려서** 쓴다.
 */
private fun Color.darkenedUntil(background: Color, minRatio: Float): Color {
    var factor = 1f
    var result = this
    // 2%씩 내린다. 더 잘게 하면 눈에 안 보이는 차이로 반복만 늘고, 크게 하면 필요 이상 어두워진다.
    while (contrastRatio(result, background) < minRatio && factor > 0.1f) {
        factor -= 0.02f
        result = Color(red * factor, green * factor, blue * factor, alpha)
    }
    return result
}

/** [other] 쪽으로 [amount]만큼 섞은 색. 0이면 그대로, 1이면 [other]. */
private fun Color.blendTo(other: Color, amount: Float): Color = Color(
    red = red + (other.red - red) * amount,
    green = green + (other.green - green) * amount,
    blue = blue + (other.blue - blue) * amount,
    alpha = alpha
)

/** 밝은 테마의 화면 바탕. 카드(흰색)보다 어둡다 — 글자색은 **이 색 기준으로** 내려야 한다. */
private val LightSurface = Color(0xFFF4F5F2)

/**
 * 밝은 테마의 글자·아이콘용 초록.
 *
 * **흰색이 아니라 [LightSurface] 기준으로 내린다.** 같은 글자가 카드(흰색) 위에도,
 * 화면 바탕(회색) 위에도 앉는데 대비가 더 낮은 쪽은 회색이다. 흰색 기준으로 맞추면
 * 화면 바탕 위에서 4.2:1이 되어 기준에 미달한다(테스트가 잡았다).
 */
val BrandGreen: Color = BrandLime.darkenedUntil(LightSurface, 4.5f)

/** 연한 브랜드 바탕(선택된 탭 등). 밝은 테마는 흰색 쪽, 어두운 테마는 잉크 쪽으로 섞는다. */
private val LightWeakBg: Color = BrandLime.blendTo(Color.White, 0.86f)
private val DarkWeakBg: Color = BrandLime.blendTo(BrandInk, 0.85f)

val LightShaColors = ShaColors(
    canvas = Color(0xFFFFFFFF),
    // 중립색을 살짝 따뜻하게(초록 쪽으로) 민다. 파랑기 도는 회색은 라임과 같이 두면 서로 튄다.
    surface = LightSurface,
    surfaceRaised = Color(0xFFFFFFFF),
    border = Color(0xFFE6E8E3),
    borderStrong = Color(0xFFD2D5CD),
    muted = Color(0xFF6E7269),
    bodyText = Color(0xFF4E5348),
    ink = BrandInk,
    primary = BrandGreen,
    primaryFill = BrandInk,
    onPrimaryFill = BrandLime,
    primaryWeakBg = LightWeakBg,
    primaryWeakFg = BrandGreen,
    // 목표 도달 같은 "잘 됐다"는 브랜드색과 같은 갈래다. 굳이 다른 초록을 하나 더 두지 않는다.
    success = BrandGreen,
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
    primaryWeakBg = DarkWeakBg,
    primaryWeakFg = BrandLime,
    success = BrandLime,
    danger = Color(0xFFFF6B70)
)

val LocalShaColors = staticCompositionLocalOf { LightShaColors }
