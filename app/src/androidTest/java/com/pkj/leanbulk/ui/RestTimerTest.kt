package com.pkj.leanbulk.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pkj.leanbulk.data.WorkoutSession
import org.junit.Assert.assertEquals
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

    /** 아직 안 끝난 운동은 지금까지, 끝낸 운동은 끝낸 시각까지만 센다. */
    @Test
    fun 운동_경과_시간은_끝냈으면_거기서_멈춘다() {
        val running = WorkoutSession("2026-09-08", startedAtEpochMs = t0)
        assertEquals(3_600_000, running.elapsedMs(t0 + 3_600_000))

        val ended = running.copy(endedAtEpochMs = t0 + 3_600_000)
        assertEquals(3_600_000, ended.elapsedMs(t0 + 7_200_000))
        assertEquals(false, ended.running)
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
