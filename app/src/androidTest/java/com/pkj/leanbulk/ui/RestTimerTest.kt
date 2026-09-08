package com.pkj.leanbulk.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pkj.leanbulk.data.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 타이머의 순수 계산. **남은 시간·경과 시간을 저장하지 않고 매번 시각 차이로 구한다**는
 * 규칙이 지켜지는지가 요점이다 — 그래야 앱이 뒤로 가 있는 동안에도 시간이 흐른다.
 */
@RunWith(AndroidJUnit4::class)
class RestTimerTest {

    private val t0 = 1_700_000_000_000L

    @Test
    fun 남은_시간은_끝나는_시각과_지금의_차이다() {
        val timer = RestTimer("벤치프레스", endsAtEpochMs = t0 + 90_000, totalSec = 90)

        assertEquals(90, timer.remainingSec(t0))
        assertEquals(60, timer.remainingSec(t0 + 30_000))
        // 앱이 2분 동안 뒤에 있었어도 남은 시간은 0이지 30초가 아니다.
        assertEquals(0, timer.remainingSec(t0 + 120_000))
    }

    /** 도는 중이면 지금까지, 멈춰 있으면 멈춘 그 순간까지만 센다. */
    @Test
    fun 운동_경과_시간은_멈추면_거기서_선다() {
        val running = WorkoutSession("2026-09-08", startedAtEpochMs = t0, resumedAtEpochMs = t0)
        assertEquals(3_600_000, running.elapsedMs(t0 + 3_600_000))

        val paused = running.pausedAt(t0 + 3_600_000)
        assertEquals(3_600_000, paused.elapsedMs(t0 + 7_200_000))
        assertEquals(false, paused.running)
        assertEquals(3_600_000L, paused.finishedMs)
    }

    /**
     * **멈춰 있던 시간은 운동 시간이 아니다.**
     *
     * 예전엔 처음 시작한 시각 하나로만 경과를 재서, 30분 하고 멈춘 뒤 두 시간 있다가
     * 이어서 하면 곧바로 2시간 30분으로 뛰었다. 이어서 하면 이미 잰 30분에서 이어져야 한다.
     */
    @Test
    fun 멈춘_동안은_운동_시간에_안_들어간다() {
        val session = WorkoutSession("2026-09-08", startedAtEpochMs = t0, resumedAtEpochMs = t0)
            .pausedAt(t0 + 30 * 60_000)
            .resumedAt(t0 + 150 * 60_000) // 두 시간 놀다 이어서

        assertEquals("이어서 한 직후엔 멈출 때 그대로여야 한다", 30 * 60_000L, session.elapsedMs(t0 + 150 * 60_000))
        assertEquals(31 * 60_000L, session.elapsedMs(t0 + 151 * 60_000))
        // 처음 시작한 시각은 기록이라 이어서 해도 안 바뀐다.
        assertEquals(t0, session.startedAtEpochMs)
        // 도는 중엔 "그날 몇 분 했다"를 아직 말할 수 없다.
        assertNull(session.finishedMs)
    }

    /** 시계는 한 시간을 넘길 때만 시간 자리를 붙인다. `83:20`으로는 몇 분인지 안 읽힌다. */
    @Test
    fun 시계_표기는_한_시간을_넘겨야_시간_자리가_붙는다() {
        assertEquals("0:05", formatClock(5))
        assertEquals("1:30", formatClock(90))
        assertEquals("59:59", formatClock(3599))
        assertEquals("1:00:00", formatClock(3600))
        assertEquals("2:03:04", formatClock(7384))
        // 시작 시각이 미래인 이상한 값이 들어와도 음수 시계를 그리지 않는다.
        assertEquals("0:00", formatClock(-10))
    }
}
