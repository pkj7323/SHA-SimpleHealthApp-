package com.pkj.leanbulk.ui

/** −/+ 한 번에 움직이는 폭. 10초면 한두 번 눌러 맞추기 좋고, 잘못 눌러도 크게 어긋나지 않는다. */
const val REST_STEP_SEC = 10

/**
 * 세트를 끝내고 도는 휴식 타이머.
 *
 * 남은 초를 들고 세어 내려가지 않고 **끝나는 시각**을 들고 지금과 비교한다. 앱이 뒤로 갔다
 * 와도, 화면이 꺼졌다 켜져도 남은 시간이 맞는다. 대신 앱이 아예 죽으면 타이머는 사라진다 —
 * 그건 기록이 아니라 지금 이 순간의 상태라서 DB에 남기지 않는다.
 */
data class RestTimer(
    /** 어느 종목을 하다 쉬는 중인지. 창에 적어줘야 어디까지 했는지 헷갈리지 않는다. */
    val exerciseName: String,
    val endsAtEpochMs: Long,
    /** 진행 막대를 그리려면 "얼마짜리 휴식이었나"가 필요하다. −/+로 늘리면 같이 커진다. */
    val totalSec: Int,
    /** 창을 펼쳐 뒀는지. 내려도 타이머 자체는 계속 돈다. */
    val expanded: Boolean = true
) {
    /** [nowEpochMs] 기준 남은 초. 0 밑으로는 안 내려간다. */
    fun remainingSec(nowEpochMs: Long): Int =
        (((endsAtEpochMs - nowEpochMs) + 999) / 1000).coerceAtLeast(0).toInt()
}

/**
 * 초를 시계 모양 글자로. 한 시간이 넘으면 시간 자리를 붙인다.
 *
 * 한 시간 넘는 운동에 `83:20`이라고 적히면 몇 분인지 한 번에 안 읽힌다.
 */
fun formatClock(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    val hours = s / 3600
    val minutes = (s % 3600) / 60
    val seconds = s % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
