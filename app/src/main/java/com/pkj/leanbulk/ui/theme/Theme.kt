package com.pkj.leanbulk.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

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
            // 하단 탭·시트가 이 색을 쓴다. 안 정하면 M3가 제 보라 컨테이너를 깐다.
            surfaceContainer = sha.surfaceRaised,
            surfaceContainerLow = sha.surfaceRaised,
            surfaceContainerHigh = sha.surface,
            secondaryContainer = sha.primaryWeakBg,
            onSecondaryContainer = sha.primaryWeakFg
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
            surfaceContainer = sha.surfaceRaised,
            surfaceContainerLow = sha.surfaceRaised,
            surfaceContainerHigh = sha.surface,
            secondaryContainer = sha.primaryWeakBg,
            onSecondaryContainer = sha.primaryWeakFg
        )
    }

    CompositionLocalProvider(
        LocalShaColors provides sha,
        LocalShaTypography provides ShaTypographySet
    ) {
        MaterialTheme(colorScheme = material, shapes = ShaShapes, typography = ShaMaterialTypography, content = content)
    }
}
