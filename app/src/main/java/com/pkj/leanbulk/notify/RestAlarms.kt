package com.pkj.leanbulk.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * 휴식이 끝나는 순간을 알려줄 물건.
 *
 * **앱이 세고 있으면 안 된다.** 다른 앱을 보고 있거나 화면이 꺼져 있으면 우리 코루틴은
 * 멈추고, 프로세스가 정리되면 아예 사라진다. 그래서 "이 시각에 깨워달라"고 시스템에
 * 맡기고([AlarmManager]), 실제로 알리는 일은 [RestAlarmReceiver]가 한다.
 *
 * 인터페이스로 빼둔 이유는 ViewModel이 Context를 안 들고도 되게 하려는 것이다.
 */
interface RestAlarms {
    fun schedule(endsAtEpochMs: Long, exerciseName: String)
    fun cancel()
}

/** 알림을 안 쓰는 자리(테스트 등)에 끼우는 빈 구현. */
object NoRestAlarms : RestAlarms {
    override fun schedule(endsAtEpochMs: Long, exerciseName: String) = Unit
    override fun cancel() = Unit
}

class AndroidRestAlarms(context: Context) : RestAlarms {

    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun schedule(endsAtEpochMs: Long, exerciseName: String) {
        val pending = pendingIntent(exerciseName)
        // 정확한 알람이 막혀 있으면(사용자가 껐거나 옛 안드로이드에서 권한이 없으면) 대충이라도
        // 울리게 한다. 30초 늦은 알림이 아무 알림도 없는 것보다 낫다.
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAtEpochMs, pending)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, endsAtEpochMs, pending)
        }
    }

    override fun cancel() {
        alarmManager.cancel(pendingIntent(null))
    }

    /**
     * 예약과 취소가 **같은 알람을 가리키려면** PendingIntent가 같아야 한다. 같은지 따질 때
     * extras는 안 보므로([Intent.filterEquals]) 종목 이름이 달라도 하나로 취급된다 —
     * 휴식은 한 번에 하나뿐이라 그게 맞다.
     */
    private fun pendingIntent(exerciseName: String?): PendingIntent {
        val intent = Intent(appContext, RestAlarmReceiver::class.java).apply {
            exerciseName?.let { putExtra(RestAlarmReceiver.EXTRA_EXERCISE_NAME, it) }
        }
        return PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    companion object {
        private const val REQUEST_CODE = 1001
    }
}

/** 휴식 알림이 나갈 채널. 앱이 뜰 때 한 번 만들어 둔다. */
const val REST_CHANNEL_ID = "rest_timer"

fun createRestNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val channel = NotificationChannel(
        REST_CHANNEL_ID,
        "휴식 타이머",
        // 다른 앱을 보고 있어도 눈에 띄어야 하므로 HIGH. 헤드업으로 잠깐 떴다가 알림창에 남는다.
        NotificationManager.IMPORTANCE_HIGH
    ).apply {
        description = "휴식 시간이 끝나면 알립니다"
        enableVibration(true)
    }
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    manager.createNotificationChannel(channel)
}
