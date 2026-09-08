package com.pkj.leanbulk.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pkj.leanbulk.data.WorkoutSession

/**
 * 운동 시작 ↔ 종료를 오가는 버튼 하나. 도는 동안은 버튼이 곧 시계다.
 *
 * 지난 날짜에는 버튼을 안 준다 — 어제 기록을 들춰보다 눌러서 "지금부터 어제 운동 시작"이
 * 되면 시작 시각이 통째로 거짓말이 된다. 그날 잰 시간이 있으면 그것만 보여준다.
 */
@Composable
fun WorkoutTimerButton(
    session: WorkoutSession?,
    isToday: Boolean,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 지난 날짜에는 버튼을 안 준다. 잰 시간은 위 타일이 이미 보여준다.
    if (!isToday) return

    when {
        session == null -> ShaPrimaryButton(
            text = "운동 시작",
            onClick = onStart,
            modifier = modifier.fillMaxWidth().testTag("workout_start")
        )

        session.running -> ShaPrimaryButton(
            text = "운동 종료",
            onClick = onEnd,
            modifier = modifier.fillMaxWidth().testTag("workout_end")
        )

        // 끝낸 뒤에도 다시 이어서 할 수 있다. 시작 시각은 그대로 두고 끝 표시만 뗀다.
        else -> ShaSecondaryButton(
            text = "이어서 하기",
            onClick = onStart,
            modifier = modifier.fillMaxWidth().testTag("workout_resume")
        )
    }
}

/**
 * 세트를 체크하면 아래에서 올라오는 휴식 타이머.
 *
 * **내려도 타이머는 계속 돈다.** 화면을 가리는 게 싫어서 접었을 뿐이라, 접힌 자리에는
 * 남은 시간만 적힌 작은 버튼을 남겨두고 다시 누르면 창이 올라온다.
 *
 * 이 컴포저블은 화면 위에 겹쳐 그리는 물건이라 `Box`의 [Alignment.BottomCenter] 자리에
 * 놓는다 — 목록 안에 넣으면 스크롤을 따라 사라진다.
 */
@Composable
fun RestTimerOverlay(
    timer: RestTimer?,
    nowEpochMs: Long,
    onAdjust: (Int) -> Unit,
    onSkip: () -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    expandedModifier: Modifier,
    collapsedModifier: Modifier
) {
    val remaining = timer?.remainingSec(nowEpochMs) ?: 0

    AnimatedVisibility(
        visible = timer != null && !timer.expanded,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = collapsedModifier
    ) {
        Button(
            onClick = { onExpandedChange(true) },
            modifier = Modifier.testTag("rest_collapsed")
        ) {
            Icon(Icons.Filled.Timer, contentDescription = null)
            Text("  ${formatClock(remaining.toLong())}")
        }
    }

    AnimatedVisibility(
        visible = timer != null && timer.expanded,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier = expandedModifier
    ) {
        Surface(
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            modifier = Modifier.fillMaxWidth().testTag("rest_sheet")
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "휴식 · ${timer?.exerciseName.orEmpty()}",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onExpandedChange(false) },
                        modifier = Modifier.testTag("rest_collapse")
                    ) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "내리기")
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { onAdjust(-REST_STEP_SEC) },
                        modifier = Modifier.testTag("rest_minus")
                    ) { Text("−${REST_STEP_SEC}초") }
                    Text(
                        formatClock(remaining.toLong()),
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).testTag("rest_remaining")
                    )
                    OutlinedButton(
                        onClick = { onAdjust(REST_STEP_SEC) },
                        modifier = Modifier.testTag("rest_plus")
                    ) { Text("+${REST_STEP_SEC}초") }
                }

                val total = timer?.totalSec?.takeIf { it > 0 } ?: 1
                LinearProgressIndicator(
                    progress = { (remaining.toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )

                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.fillMaxWidth().testTag("rest_skip")
                ) { Text("건너뛰기") }
            }
        }
    }
}

/**
 * 휴식이 끝났다고 알린다. **소리 대신 진동만 쓴다** — 헬스장에서 알림음은 민폐고,
 * 무음으로 해둔 폰에서도 손목·주머니로는 느껴진다.
 *
 * 진동은 있으면 좋은 것이지 없으면 안 되는 것이 아니다. 기기가 안 떨어도 타이머 자체는
 * 화면에서 이미 0이 됐으므로 조용히 넘어간다.
 */
fun vibrateRestDone(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    if (vibrator?.hasVibrator() != true) return
    vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 250, 150, 250), -1))
}
