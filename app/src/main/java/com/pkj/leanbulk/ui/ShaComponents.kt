package com.pkj.leanbulk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pkj.leanbulk.ui.theme.ShaSpacing
import com.pkj.leanbulk.ui.theme.ShaTheme

/**
 * 화면 하나를 감싸는 바탕. 좌우 여백과 배경색을 여기서 한 번에 정한다.
 *
 * 화면마다 `padding(16.dp)`을 각자 적어두면 어느 화면은 16, 어느 화면은 20이 되어
 * 탭을 옮길 때 내용이 좌우로 흔들린다.
 */
@Composable
fun ShaScreen(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier
            .fillMaxWidth()
            .background(ShaTheme.colors.surface)
    ) { content() }
}

/** 화면 안의 카드 한 장. **테두리도 그림자도 없다** — 바탕과의 색 차이만으로 떠 보이게 한다. */
@Composable
fun ShaCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier
            .fillMaxWidth()
            .background(ShaTheme.colors.surfaceRaised, RoundedCornerShape(16.dp))
            .padding(ShaSpacing.lg)
    ) { content() }
}

/**
 * 큰 숫자 하나를 보여주는 타일. 화면 맨 위에 가로로 두세 개 늘어놓는다.
 *
 * [delta]는 "목표까지 얼마"처럼 **숫자를 해석해 주는 한 마디**다. 숫자만 있으면 많은 건지
 * 적은 건지 알 수 없어서, 이 자리가 비면 타일이 하는 일이 절반으로 준다.
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    unit: String,
    delta: String?,
    deltaTone: Color? = null,
    modifier: Modifier = Modifier
) {
    val colors = ShaTheme.colors
    val type = ShaTheme.type
    Column(
        modifier
            .background(colors.surfaceRaised, RoundedCornerShape(16.dp))
            .padding(horizontal = ShaSpacing.lg, vertical = ShaSpacing.lg)
    ) {
        Text(label, style = type.caption, color = colors.muted)
        Row(
            Modifier.padding(top = ShaSpacing.sm),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(value, style = type.display, color = colors.ink)
            if (unit.isNotBlank()) {
                Text(
                    unit,
                    style = type.caption,
                    color = colors.muted,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 2.dp, bottom = 3.dp)
                )
            }
        }
        // 한 줄로 못 박는다. 한 타일만 두 줄이 되면 세 타일의 높이가 어긋나 바닥선이 깨진다.
        Text(
            text = delta ?: " ",
            style = type.caption,
            color = deltaTone ?: colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = ShaSpacing.xs)
        )
    }
}

/**
 * 점선 테두리 "추가" 버튼.
 *
 * 채운 버튼으로 두면 목록의 마지막 항목보다 무거워져서, 이미 담은 것보다 담는 행위가
 * 더 중요해 보인다. 점선은 "여기 아직 빈자리"라는 뜻으로 읽힌다.
 */
@Composable
fun DashedAddButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ShaTheme.colors
    val stroke = Stroke(
        width = 1.5.dp.value * LocalDensityScale,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
    )
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .drawBehind {
                drawRoundRect(
                    color = colors.borderStrong,
                    size = Size(size.width, size.height),
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    style = stroke
                )
            }
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            tint = colors.muted,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text,
            style = ShaTheme.type.bodyStrong,
            color = colors.muted,
            modifier = Modifier.padding(start = ShaSpacing.sm)
        )
    }
}

/** `drawBehind`는 px를 받는데 stroke 폭은 dp로 적고 싶어서 쓰는 임시 배율. */
private const val LocalDensityScale = 2.75f

/** 식단/운동/신체를 고르는 알약 탭. M3 `TabRow`의 밑줄 대신 배경으로 선택을 표시한다. */
@Composable
fun SubTabBar(
    tabs: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ShaTheme.colors
    Row(
        modifier.padding(vertical = ShaSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(ShaSpacing.md)
    ) {
        tabs.forEachIndexed { index, label ->
            val on = index == selected
            Text(
                text = label,
                style = ShaTheme.type.bodyStrong,
                color = if (on) colors.canvas else colors.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(
                        if (on) colors.ink else Color.Transparent,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelect(index) }
                    .padding(horizontal = 14.dp, vertical = ShaSpacing.md)
            )
        }
    }
}
