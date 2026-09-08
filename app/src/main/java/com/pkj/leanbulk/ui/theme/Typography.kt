package com.pkj.leanbulk.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 본문 글꼴.
 *
 * 명령서는 Pretendard를 쓰라고 하지만 **`res/font/`에 폰트 파일이 아직 없다.** 여기만
 * `FontFamily(Font(R.font.pretendard_...))`로 바꾸면 전체가 따라 바뀐다 — 화면 코드는
 * 글꼴을 직접 안 잡으므로 이 한 줄이 전부다.
 */
private val ShaFontFamily = FontFamily.Default

/**
 * 숫자는 **등폭(tabular)** 으로 그린다.
 *
 * 안 그러면 `1`이 `8`보다 좁아서, 세트가 60kg→70kg으로 바뀔 때 숫자 폭이 흔들리고
 * 세로로 늘어놓은 값들의 자릿수가 안 맞는다. 표처럼 읽혀야 하는 화면에서 이게 제일 티난다.
 */
private const val TABULAR = "tnum"

@Immutable
data class ShaTypography(
    /** 스탯 타일의 큰 숫자. */
    val display: TextStyle,
    /** 화면 제목 ("오늘", "히스토리"). */
    val h1: TextStyle,
    /** 카드·섹션 제목 (종목명 등). */
    val h2: TextStyle,
    /** 목록 제목 (음식명, 필드 라벨). */
    val bodyStrong: TextStyle,
    val body: TextStyle,
    /** 보조 텍스트. 항상 muted 색과 함께 쓴다. */
    val caption: TextStyle,
    /** 무게·횟수·칼로리 등 **모든 숫자**. 오른쪽 정렬이 기본. */
    val data: TextStyle,
    val dataSmall: TextStyle
)

val ShaTypographySet = ShaTypography(
    display = TextStyle(
        fontFamily = ShaFontFamily, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold,
        fontFeatureSettings = TABULAR
    ),
    h1 = TextStyle(fontFamily = ShaFontFamily, fontSize = 22.sp, fontWeight = FontWeight.Bold),
    h2 = TextStyle(fontFamily = ShaFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Bold),
    bodyStrong = TextStyle(fontFamily = ShaFontFamily, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    body = TextStyle(fontFamily = ShaFontFamily, fontSize = 15.sp, fontWeight = FontWeight.Normal),
    caption = TextStyle(fontFamily = ShaFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Medium),
    data = TextStyle(
        fontFamily = ShaFontFamily, fontSize = 19.sp, fontWeight = FontWeight.Bold,
        fontFeatureSettings = TABULAR
    ),
    dataSmall = TextStyle(
        fontFamily = ShaFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Bold,
        fontFeatureSettings = TABULAR
    )
)

val LocalShaTypography = staticCompositionLocalOf { ShaTypographySet }

/** 아직 안 고친 화면이 쓰는 M3 기본 타이포. 새 화면은 [ShaTypography]만 쓴다. */
val ShaMaterialTypography = Typography()
