package com.pkj.leanbulk.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 권장 목표 계산.
 *
 * 정확한 정답이 있는 계산이 아니라 **어림 가이드라인**이라, 특정 숫자를 못 박기보다
 * 지켜야 할 관계를 검증한다. 계수를 나중에 조정해도 이 관계는 깨지면 안 된다.
 */
@RunWith(AndroidJUnit4::class)
class TargetCalculatorTest {

    private val weight = 75.0

    @Test
    fun 벌크업이_유지보다_많고_컷팅이_유지보다_적다() {
        val bulk = TargetCalculator.suggest(weight, GoalType.BULK, ActivityLevel.MEDIUM)
        val maintain = TargetCalculator.suggest(weight, GoalType.MAINTAIN, ActivityLevel.MEDIUM)
        val cut = TargetCalculator.suggest(weight, GoalType.CUT, ActivityLevel.MEDIUM)

        assertTrue("벌크업이 유지보다 많아야 한다", bulk.kcal > maintain.kcal)
        assertTrue("컷팅이 유지보다 적어야 한다", cut.kcal < maintain.kcal)
    }

    @Test
    fun 활동량이_많을수록_칼로리가_올라간다() {
        val low = TargetCalculator.suggest(weight, GoalType.MAINTAIN, ActivityLevel.LOW)
        val mid = TargetCalculator.suggest(weight, GoalType.MAINTAIN, ActivityLevel.MEDIUM)
        val high = TargetCalculator.suggest(weight, GoalType.MAINTAIN, ActivityLevel.HIGH)

        assertTrue(low.kcal < mid.kcal)
        assertTrue(mid.kcal < high.kcal)
    }

    /** 칼로리를 줄일 때 근손실을 막으려면 단백질은 오히려 더 챙겨야 한다. */
    @Test
    fun 컷팅은_칼로리가_줄어도_단백질은_늘어난다() {
        val bulk = TargetCalculator.suggest(weight, GoalType.BULK, ActivityLevel.MEDIUM)
        val cut = TargetCalculator.suggest(weight, GoalType.CUT, ActivityLevel.MEDIUM)

        assertTrue(cut.kcal < bulk.kcal)
        assertTrue("컷팅 단백질이 벌크업보다 적다", cut.protein > bulk.protein)
    }

    /**
     * 매크로를 칼로리로 환산하면 총 칼로리와 맞아야 한다.
     * 탄수를 "남은 칼로리"로 계산하므로, 이게 어긋나면 배분 로직이 깨진 것이다.
     */
    @Test
    fun 매크로_합이_총_칼로리와_맞는다() {
        GoalType.entries.forEach { goal ->
            val t = TargetCalculator.suggest(weight, goal, ActivityLevel.MEDIUM)
            val fromMacros = t.protein * 4 + t.carbs * 4 + t.fat * 9
            // 반올림 때문에 정확히 같지는 않다. 15kcal 안쪽이면 반올림 오차 수준.
            assertEquals("${goal}에서 매크로 합이 어긋난다", t.kcal, fromMacros, 15.0)
        }
    }

    @Test
    fun 고른_목적과_활동량이_결과에_그대로_담긴다() {
        val t = TargetCalculator.suggest(weight, GoalType.CUT, ActivityLevel.HIGH)
        assertEquals(GoalType.CUT, t.goal)
        assertEquals(ActivityLevel.HIGH, t.activityLevel)
    }

    /** 체중이 0이어도 음수 탄수 같은 이상한 값이 나오면 안 된다. */
    @Test
    fun 체중이_0이어도_음수가_나오지_않는다() {
        val t = TargetCalculator.suggest(0.0, GoalType.CUT, ActivityLevel.LOW)
        assertTrue(t.kcal >= 0)
        assertTrue(t.protein >= 0)
        assertTrue("탄수가 음수다", t.carbs >= 0)
        assertTrue(t.fat >= 0)
    }
}
