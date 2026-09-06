package com.pkj.leanbulk.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 숫자 입력 + 좌우 증감 버튼. **세트 한 줄에 두 개가 들어가야 하므로 최대한 납작하다.**
 *
 * 무게·횟수는 세트마다 한두 칸씩만 움직이는 값이라, 그때마다 키보드를 올려 지우고 다시 치는 게
 * 제일 번거로웠다. 버튼은 **입력 칸을 대체하지 않고 옆에 붙는다** — 60 → 62.5처럼 크게 뛸 땐
 * 여전히 직접 치는 게 빠르기 때문이다.
 *
 * 칸 이름(`무게(kg)` 등)은 여기 없다. 세트마다 붙이면 같은 글자가 네다섯 번 반복되면서
 * `OutlinedTextField`의 라벨 높이까지 먹어 카드가 두 배로 길어졌다. 이름은 종목 카드에
 * **머리글로 한 번만** 적고, 여기서는 값만 보여준다. [description]은 화면에 안 보이지만
 * 스크린리더가 읽을 수 있게 남긴다.
 *
 * 음수는 만들지 않는다. 무게든 횟수든 시간이든 음수가 의미를 갖는 값이 없다.
 */
@Composable
fun NumberStepper(
    description: String,
    value: Double,
    step: Double,
    modifier: Modifier = Modifier,
    onValueChange: (Double) -> Unit
) {
    var text by remember(value) { mutableStateOf(formatNumber(value)) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        StepButton(Icons.Filled.Remove, "$description 줄이기") {
            onValueChange((value - step).coerceAtLeast(0.0))
        }
        CompactNumberInput(
            text = text,
            description = description,
            modifier = Modifier.weight(1f)
        ) { input ->
            text = input
            input.toDoubleOrNull()?.let(onValueChange)
        }
        StepButton(Icons.Filled.Add, "$description 늘리기") { onValueChange(value + step) }
    }
}

/** [NumberStepper]의 정수판. 빈 칸이면 0부터 센다. */
@Composable
fun IntStepper(
    description: String,
    value: Int,
    step: Int,
    modifier: Modifier = Modifier,
    onValueChange: (Int) -> Unit
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        StepButton(Icons.Filled.Remove, "$description 줄이기") {
            onValueChange((value - step).coerceAtLeast(0))
        }
        CompactNumberInput(
            text = text,
            description = description,
            modifier = Modifier.weight(1f)
        ) { input ->
            text = input
            // 빈 칸은 0으로 본다 — 세트 안의 횟수·시간은 "모름"이 아니라 아직 안 채운 값이다.
            if (input.isBlank()) onValueChange(0) else input.toIntOrNull()?.let(onValueChange)
        }
        StepButton(Icons.Filled.Add, "$description 늘리기") { onValueChange(value + step) }
    }
}

/**
 * 테두리만 두른 납작한 숫자 칸.
 *
 * `OutlinedTextField`를 안 쓰는 이유는 **높이를 못 줄여서다** — 라벨 자리를 비워둬도 최소 56dp를
 * 차지한다. 세트가 다섯 줄이면 그 차이가 화면 하나만큼 벌어진다.
 */
@Composable
private fun CompactNumberInput(
    text: String,
    description: String,
    modifier: Modifier = Modifier,
    onTextChange: (String) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = text,
        onValueChange = onTextChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = colors.onSurface,
            textAlign = TextAlign.Center
        ),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
            .height(40.dp)
            .border(1.dp, colors.outline, RoundedCornerShape(8.dp))
            .semantics { contentDescription = description },
        decorationBox = { field ->
            Box(
                Modifier.fillMaxSize().padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) { field() }
        }
    )
}

/**
 * 증감 버튼. 기본 [IconButton]은 48dp라 한 줄에 두 값(무게·횟수)과 체크박스를 같이 넣으면 넘친다.
 */
@Composable
private fun StepButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(18.dp))
    }
}

/** 이름 하나만 받는 다이얼로그. 루틴·식단표의 추가·이름수정·저장이 전부 이걸 쓴다. */
@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    confirmText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

/**
 * 비워둘 수 있는 숫자 입력. 빈 칸 = "모름"(null)이며 0과 구분된다.
 * 영양정보처럼 모를 수 있는 값에 쓴다.
 */
@Composable
fun NullableNumberField(
    label: String,
    value: Double?,
    modifier: Modifier = Modifier.width(96.dp),
    onValueChange: (Double?) -> Unit
) {
    var text by remember(value) { mutableStateOf(value?.let(::formatNumber) ?: "") }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input
            if (input.isBlank()) onValueChange(null) else input.toDoubleOrNull()?.let(onValueChange)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

/** 다이얼로그 안에서 쓰는 가로 전체폭 숫자 입력. */
@Composable
fun LabeledNumberField(label: String, value: Double, onValueChange: (Double) -> Unit) {
    var text by remember(value) { mutableStateOf(formatNumber(value)) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input
            input.toDoubleOrNull()?.let(onValueChange)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}

private fun formatNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
