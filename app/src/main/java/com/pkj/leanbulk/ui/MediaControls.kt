package com.pkj.leanbulk.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pkj.leanbulk.notify.MediaAccessService
import com.pkj.leanbulk.ui.theme.ShaSpacing
import com.pkj.leanbulk.ui.theme.ShaTheme
import kotlinx.coroutines.delay

/**
 * 음악 바가 떠 있을 때 그 위에 겹쳐 놓을 것(휴식 버튼, 목록 끝 여백)이 비워 둘 높이.
 * 바 높이에 사이 여백을 더한 값이다.
 */
val MINI_PLAYER_CLEARANCE = 68.dp

/** "알림 접근"이 켜져 있나. 켜야 다른 앱의 곡 제목·진행 위치를 읽을 수 있다. */
fun hasMediaAccess(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

/** 알림 접근을 켜고 끄는 시스템 화면. 앱이 대신 켤 수는 없다. */
fun mediaAccessSettingsIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)

private data class NowPlaying(
    val title: String?,
    val artist: String?,
    val playing: Boolean,
    val positionMs: Long,
    val durationMs: Long
)

/** 지금 소리를 내는 앱의 재생 세션. 재생 중인 게 없으면 마지막으로 쓰던 것. */
private fun activeController(context: Context): MediaController? {
    val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    val sessions = try {
        manager.getActiveSessions(ComponentName(context, MediaAccessService::class.java))
    } catch (e: SecurityException) {
        // 권한 확인과 이 호출 사이에 사용자가 껐을 수 있다.
        return null
    }
    return sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        ?: sessions.firstOrNull()
}

private fun MediaController.snapshot(): NowPlaying {
    val state = playbackState
    val playing = state?.state == PlaybackState.STATE_PLAYING
    // 앱들은 위치를 매 순간 알려주지 않는다. 마지막으로 알려준 위치에 그 뒤로 흐른 시간을 더한다.
    val position = when {
        state == null -> 0L
        playing && state.lastPositionUpdateTime > 0 ->
            state.position + ((SystemClock.elapsedRealtime() - state.lastPositionUpdateTime) * state.playbackSpeed).toLong()
        else -> state.position
    }
    val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
    return NowPlaying(
        title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
        artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST),
        playing = playing,
        positionMs = if (duration > 0) position.coerceIn(0L, duration) else position.coerceAtLeast(0L),
        durationMs = duration
    )
}

/**
 * 운동 탭 아래에 떠 있는 작은 음악 바. 이전 · 재생/일시정지 · 다음.
 *
 * **"알림 접근"을 켰느냐에 따라 두 가지로 동작한다.**
 * - 켰으면: 재생 세션을 직접 읽어 곡 제목·아티스트·진행바를 보이고, 버튼도 그 세션에 보낸다.
 * - 안 켰으면: 이어폰 버튼처럼 미디어 키만 보낸다(권한 없이 어느 앱이든 된다). 제목 자리에
 *   켜는 곳으로 가는 안내를 둔다.
 */
@Composable
fun MiniPlayer(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val colors = ShaTheme.colors
    val type = ShaTheme.type

    // 설정 앱에서 켜고 돌아오면 바로 바뀌어야 한다. 화면으로 돌아올 때마다 다시 본다.
    var access by remember { mutableStateOf(hasMediaAccess(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) access = hasMediaAccess(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 재생 상태는 추측하지 않고 시스템에 물어본다. 이어폰·알림창에서 멈춰도 맞게 보이도록.
    // 진행바가 끊겨 보이지 않을 만큼만 자주(0.5초) 본다.
    var now by remember { mutableStateOf<NowPlaying?>(null) }
    var musicActive by remember { mutableStateOf(audio.isMusicActive) }
    LaunchedEffect(access) {
        while (true) {
            now = if (access) activeController(context)?.snapshot() else null
            musicActive = audio.isMusicActive
            delay(500)
        }
    }
    val playing = now?.playing ?: musicActive

    fun sendKey(keyCode: Int) {
        val t = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, keyCode, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, keyCode, 0))
    }

    // 세션을 읽을 수 있으면 그 세션에 직접 보낸다. 아니면 미디어 키.
    fun control(keyCode: Int, direct: (MediaController.TransportControls) -> Unit) {
        val controller = if (access) activeController(context) else null
        if (controller != null) direct(controller.transportControls) else sendKey(keyCode)
    }

    Surface(
        modifier = modifier.fillMaxWidth().testTag("mini_player"),
        shape = RoundedCornerShape(16.dp),
        color = colors.surfaceRaised,
        border = BorderStroke(1.dp, colors.border),
        shadowElevation = 6.dp
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(start = ShaSpacing.lg, end = ShaSpacing.xs).height(52.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    Modifier
                        .weight(1f)
                        .then(
                            if (access) Modifier else
                                Modifier.clickable { context.startActivity(mediaAccessSettingsIntent()) }
                        )
                ) {
                    val title = now?.title
                    Text(
                        text = when {
                            title != null -> title
                            playing -> "재생 중"
                            else -> "음악"
                        },
                        style = type.bodyStrong,
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val sub = when {
                        !access -> "눌러서 곡 정보 켜기"
                        else -> now?.artist
                    }
                    if (!sub.isNullOrBlank()) {
                        Text(
                            sub,
                            style = type.caption,
                            color = if (access) colors.muted else colors.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(
                    onClick = { control(KeyEvent.KEYCODE_MEDIA_PREVIOUS) { it.skipToPrevious() } },
                    modifier = Modifier.size(40.dp).testTag("media_prev")
                ) { Icon(Icons.Filled.SkipPrevious, contentDescription = "이전 곡", tint = colors.ink) }
                IconButton(
                    onClick = {
                        control(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) { if (playing) it.pause() else it.play() }
                        // 시스템이 반영하기 전에 아이콘부터 뒤집는다. 다음 확인에서 사실로 맞춰진다.
                        now = now?.copy(playing = !playing)
                        musicActive = !playing
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
                    onClick = { control(KeyEvent.KEYCODE_MEDIA_NEXT) { it.skipToNext() } },
                    modifier = Modifier.size(40.dp).testTag("media_next")
                ) { Icon(Icons.Filled.SkipNext, contentDescription = "다음 곡", tint = colors.ink) }
            }

            // 길이를 모르는 방송·라이브는 진행바를 안 그린다. 빈 막대는 고장 난 것처럼 보인다.
            val np = now
            if (np != null && np.durationMs > 0) {
                LinearProgressIndicator(
                    progress = { (np.positionMs.toFloat() / np.durationMs).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(3.dp).testTag("media_progress"),
                    color = colors.primary,
                    trackColor = colors.border,
                    strokeCap = StrokeCap.Butt,
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )
            }
        }
    }
}
