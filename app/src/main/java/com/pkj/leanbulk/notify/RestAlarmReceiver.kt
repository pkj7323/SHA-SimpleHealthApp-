package com.pkj.leanbulk.notify

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pkj.leanbulk.MainActivity
import com.pkj.leanbulk.R
import com.pkj.leanbulk.ui.vibrateRestDone

/**
 * 휴식이 끝난 시각에 시스템이 깨워주는 자리.
 *
 * 앱이 앞에 있든 뒤에 있든, 아예 죽어 있든 여기로 온다. 그래서 **알리는 일은 전부 여기서**
 * 한다 — 화면 쪽에서 따로 울리면 앞에 있을 때만 두 번 울린다.
 */
class RestAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val exerciseName = intent.getStringExtra(EXTRA_EXERCISE_NAME).orEmpty()

        // 진동은 알림 권한과 무관하게 울린다. 알림을 거절한 사람도 휴식이 끝난 건 알아야 한다.
        vibrateRestDone(context)

        if (!canNotify(context)) return

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, REST_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_rest)
            .setContentTitle("휴식 끝")
            .setContentText(if (exerciseName.isBlank()) "다음 세트 시작" else "$exerciseName · 다음 세트 시작")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    /** 안드로이드 13부터는 알림도 사용자가 켜줘야 한다. 안 켰으면 조용히 넘어간다. */
    private fun canNotify(context: Context): Boolean =
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        const val EXTRA_EXERCISE_NAME = "exerciseName"
        private const val NOTIFICATION_ID = 2001
    }
}
