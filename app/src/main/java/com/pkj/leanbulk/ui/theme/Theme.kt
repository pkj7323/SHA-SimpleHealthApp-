package com.pkj.leanbulk.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.pkj.leanbulk.data.ThemeMode

/**
 * 새 디자인 토큰을 꺼내 쓰는 자리. `MaterialTheme.colorScheme` 대신 이걸 쓴다.
 *
 * M3 색도 같이 채워두는 이유는 **아직 안 고친 화면과 기본 부품(다이얼로그·스위치 등)이
 * 그걸 보고 그리기 때문**이다. 안 채우면 M3가 제 기본 보라 틴트를 깔아서, 고친 화면과
 * 안 고친 화면의 바탕색이 서로 다르게 나온다.
 */
object ShaTheme {
    val colors: ShaColors
        @Composable @ReadOnlyComposable get() = LocalShaColors.current

    val type: ShaTypography
        @Composable @ReadOnlyComposable get() = LocalShaTypography.current
}

/** [ThemeMode.SYSTEM]일 때만 폰 설정을 본다. 나머지는 사용자가 고른 대로. */
@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

@Composable
fun SimpleHealthTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val sha = if (darkTheme) DarkShaColors else LightShaColors

    val material = if (darkTheme) {
        darkColorScheme(
            // 채워지는 버튼은 라임 + 잉크 글자다. `onPrimary`에 흰색을 두면 대비 1.6:1이 된다.
            primary = sha.primaryFill,
            onPrimary = sha.onPrimaryFill,
            secondary = sha.primaryWeakFg,
            background = sha.surface,
            onBackground = sha.ink,
            surface = sha.surfaceRaised,
            onSurface = sha.ink,
            surfaceVariant = sha.surface,
            onSurfaceVariant = sha.bodyText,
            outline = sha.borderStrong,
            outlineVariant = sha.border,
            error = sha.danger,
            // 카드·하단탭·시트가 이 계열을 쓴다. **하나라도 빼먹으면 그 부품만 보라로 남는다** —
            // M3는 안 채운 토큰에 제 기본 보라 틴트를 그대로 쓴다.
            surfaceContainerLowest = sha.surfaceRaised,
            surfaceContainerLow = sha.surfaceRaised,
            surfaceContainer = sha.surfaceRaised,
            surfaceContainerHigh = sha.surface,
            surfaceContainerHighest = sha.surface,
            surfaceDim = sha.surface,
            surfaceBright = sha.surfaceRaised,
            inverseSurface = sha.ink,
            inverseOnSurface = sha.canvas,
            secondaryContainer = sha.primaryWeakBg,
            onSecondaryContainer = sha.primaryWeakFg,
            tertiary = sha.primary,
            onTertiary = sha.canvas,
            tertiaryContainer = sha.primaryWeakBg,
            onTertiaryContainer = sha.primaryWeakFg,
            onSecondary = sha.canvas,
            // **다른 컨테이너와 같은 색을 주면 안 된다.** M3는 배경색으로 글자색을 역추적하는데
            // (`contentColorFor`), 값이 겹치면 엉뚱한 쪽이 먼저 걸려서 제목이 빨갛게 나온다.
            errorContainer = Color(0xFFFDEBEC),
            onErrorContainer = Color(0xFF8C1D18),
            onError = sha.canvas,
            scrim = sha.ink
        )
    } else {
        lightColorScheme(
            // 채워지는 버튼은 라임 + 잉크 글자다. `onPrimary`에 흰색을 두면 대비 1.6:1이 된다.
            primary = sha.primaryFill,
            onPrimary = sha.onPrimaryFill,
            secondary = sha.primaryWeakFg,
            background = sha.surface,
            onBackground = sha.ink,
            surface = sha.surfaceRaised,
            onSurface = sha.ink,
            surfaceVariant = sha.surface,
            onSurfaceVariant = sha.bodyText,
            outline = sha.borderStrong,
            outlineVariant = sha.border,
            error = sha.danger,
            surfaceContainerLowest = sha.canvas,
            surfaceContainerLow = sha.surfaceRaised,
            surfaceContainer = sha.surfaceRaised,
            surfaceContainerHigh = sha.surfaceRaised,
            surfaceContainerHighest = sha.border,
            surfaceDim = sha.surface,
            surfaceBright = sha.surfaceRaised,
            inverseSurface = sha.ink,
            inverseOnSurface = sha.canvas,
            secondaryContainer = sha.primaryWeakBg,
            onSecondaryContainer = sha.primaryWeakFg,
            tertiary = sha.primary,
            onTertiary = sha.canvas,
            tertiaryContainer = sha.primaryWeakBg,
            onTertiaryContainer = sha.primaryWeakFg,
            onSecondary = sha.canvas,
            errorContainer = Color(0xFF3B1F20),
            onErrorContainer = Color(0xFFFFB4AB),
            onError = sha.canvas,
            scrim = Color.Black
        )
    }

    CompositionLocalProvider(
        LocalShaColors provides sha,
        LocalShaTypography provides ShaTypographySet
    ) {
        MaterialTheme(colorScheme = material, shapes = ShaShapes, typography = ShaMaterialTypography, content = content)
    }
}
