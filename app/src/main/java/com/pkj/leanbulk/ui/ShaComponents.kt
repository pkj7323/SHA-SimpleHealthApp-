package com.pkj.leanbulk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.Dialog
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
                color = if (on) colors.onPrimaryFill else colors.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(
                        if (on) colors.primaryFill else Color.Transparent,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelect(index) }
                    .padding(horizontal = 14.dp, vertical = ShaSpacing.md)
            )
        }
    }
}

/**
 * 앱의 모든 다이얼로그가 쓰는 껍데기 (UI-DESIGN-SPEC.md §2-1).
 *
 * M3 `AlertDialog`를 직접 쓰지 않는 이유는 **기본 모양이 제 색과 제 여백을 들고 오고,
 * 버튼이 오른쪽 아래 글자 버튼 두 개로 고정되기 때문**이다. 그 배치는 누를 곳이 작고
 * 확인과 취소의 무게가 같아서, 다이얼로그마다 "무엇이 주된 동작인지"를 말해주지 못한다.
 *
 * [destructive]는 버튼 줄 **위에** 따로 둔다. 확인 옆에 두면 저장하려다 지우게 된다.
 */
@Composable
fun ShaDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    dismissText: String = "취소",
    destructiveText: String? = null,
    onDestructive: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = ShaTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.surfaceRaised, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Text(title, style = ShaTheme.type.h1, color = colors.ink)
            Box(Modifier.padding(top = ShaSpacing.lg))
            content()

            destructiveText?.let { label ->
                Text(
                    label,
                    style = ShaTheme.type.bodyStrong,
                    color = colors.danger,
                    modifier = Modifier
                        .padding(top = ShaSpacing.lg)
                        .clickable { onDestructive?.invoke() }
                        .padding(vertical = ShaSpacing.md)
                        .testTag("dialog_destructive")
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(top = ShaSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(ShaSpacing.md)
            ) {
                ShaSecondaryButton(dismissText, onDismiss, Modifier.weight(1f))
                if (confirmText != null && onConfirm != null) {
                    ShaPrimaryButton(confirmText, onConfirm, Modifier.weight(1f), confirmEnabled)
                }
            }
        }
    }
}

/** 화면에서 제일 하고 싶은 일 하나. 채우는 색은 늘 배경의 반대다([ShaColors]). */
@Composable
fun ShaPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = ShaTheme.colors
    Box(
        modifier
            .height(48.dp)
            .background(
                if (enabled) colors.primaryFill else colors.border,
                RoundedCornerShape(14.dp)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = ShaTheme.type.bodyStrong,
            color = if (enabled) colors.onPrimaryFill else colors.muted,
            maxLines = 1
        )
    }
}

/** 취소·닫기처럼 "안 하는" 쪽. 테두리 대신 옅은 바탕으로 무게를 낮춘다. */
@Composable
fun ShaSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ShaTheme.colors
    Box(
        modifier
            .height(48.dp)
            .background(colors.surface, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = ShaTheme.type.bodyStrong, color = colors.ink, maxLines = 1)
    }
}

/**
 * 다이얼로그 안 글자 입력.
 *
 * `OutlinedTextField`를 안 쓰는 이유는 라벨이 테두리를 파고드는 모양이 이 디자인의
 * "테두리를 안 쓴다"와 안 맞아서다. 라벨을 위에 따로 적고 칸은 옅은 바탕으로 둔다.
 */
@Composable
fun ShaTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    /**
     * 글자를 받는 칸 자체에 붙는 modifier.
     *
     * [modifier]는 라벨까지 감싼 바깥 칸에 붙으므로 **거기에 testTag를 달면 그 노드가
     * 글자를 못 받는다.** 화면 테스트가 쓰는 태그는 반드시 이쪽으로 준다.
     */
    fieldModifier: Modifier = Modifier
) {
    val colors = ShaTheme.colors
    Column(modifier) {
        if (label.isNotBlank()) {
            Text(
                label,
                style = ShaTheme.type.caption,
                color = colors.muted,
                modifier = Modifier.padding(bottom = ShaSpacing.sm)
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = ShaTheme.type.body.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = fieldModifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 14.dp),
            decorationBox = { field ->
                if (value.isEmpty() && placeholder.isNotBlank()) {
                    Text(placeholder, style = ShaTheme.type.body, color = colors.muted)
                }
                field()
            }
        )
    }
}
