package com.pkj.leanbulk.ui

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.pkj.leanbulk.ui.theme.ShaSpacing
import com.pkj.leanbulk.ui.theme.ShaTheme
import kotlinx.coroutines.delay

/**
 * 지금 재생 중인 음악을 이전 · 재생/일시정지 · 다음으로 조절한다.
 *
 * **미디어 키를 보내는 방식**이다(`AudioManager.dispatchMediaKeyEvent`). 이어폰 버튼을
 * 누른 것과 똑같이 시스템이 지금 소리를 내는 앱에 전달한다. 권한이 필요 없고 스포티파이·
 * 유튜브 뮤직·멜론 어느 앱이든 된다.
 *
 * 곡 제목은 안 보여준다. 그걸 읽으려면 "알림 접근" 권한을 받아야 하는데, 운동 기록 앱이
 * 모든 알림을 읽겠다고 하는 건 얻는 것에 비해 요구가 너무 크다.
 */
@Composable
fun MediaControlRow(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val colors = ShaTheme.colors

    // 재생 중인지는 시스템에 물어본다. 버튼을 누른 쪽으로 추측해 두면 다른 곳(이어폰, 알림창)에서
    // 멈췄을 때 아이콘이 거짓말을 한다. 1초마다 다시 본다.
    var playing by remember { mutableStateOf(audio.isMusicActive) }
    LaunchedEffect(Unit) {
        while (true) {
            playing = audio.isMusicActive
            delay(1000)
        }
    }

    fun send(keyCode: Int) {
        val now = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
    }

    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ShaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (playing) "재생 중" else "음악",
            style = ShaTheme.type.caption,
            color = colors.muted,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = { send(KeyEvent.KEYCODE_MEDIA_PREVIOUS) },
            modifier = Modifier.size(40.dp).testTag("media_prev")
        ) { Icon(Icons.Filled.SkipPrevious, contentDescription = "이전 곡", tint = colors.ink) }
        IconButton(
            onClick = {
                send(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                // 시스템이 반영하기 전에 아이콘부터 뒤집는다. 다음 1초 확인에서 사실로 맞춰진다.
                playing = !playing
            },
            modifier = Modifier.size(40.dp).testTag("media_play_pause")
        ) {
            Icon(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (playing) "일시정지" else "재생",
                tint = colors.primary
            )
        }
        IconButton(
            onClick = { send(KeyEvent.KEYCODE_MEDIA_NEXT) },
            modifier = Modifier.size(40.dp).testTag("media_next")
        ) { Icon(Icons.Filled.SkipNext, contentDescription = "다음 곡", tint = colors.ink) }
    }
}
