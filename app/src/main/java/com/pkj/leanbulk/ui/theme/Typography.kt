package com.pkj.leanbulk.ui.theme

import androidx.compose.material3.Typography
import com.pkj.leanbulk.R
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 본문 글꼴 — Pretendard.
 *
 * 시스템 글꼴을 쓰면 **기기마다 다르게 보인다**(삼성은 One UI Sans, 픽셀은 Noto Sans KR).
 * 글꼴을 앱에 넣으면 어디서나 같은 화면이 된다.
 *
 * 굵기를 다섯 벌 다 넣는다. 셋만 넣고 나머지를 근사시키면 [ShaTypography]의 단계가
 * 뭉개져서, 값(Bold)과 큰 숫자(ExtraBold)가 구분이 안 된다.
 *
 * 라이선스는 SIL OFL이고 전문은 `assets/licenses/pretendard-ofl.txt`에 같이 넣어 배포한다.
 */
private val ShaFontFamily = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
    Font(R.font.pretendard_extrabold, FontWeight.ExtraBold)
)

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
