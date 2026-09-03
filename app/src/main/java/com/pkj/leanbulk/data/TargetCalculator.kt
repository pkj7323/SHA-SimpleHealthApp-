package com.pkj.leanbulk.data

import kotlin.math.roundToInt

/**
 * 체중 · 목적 · 활동량으로 하루 섭취 목표를 어림한다.
 *
 * **일반적인 근력운동 가이드라인을 따른 어림값이지 의학적 처방이 아니다.** 그래서 계산
 * 결과를 목표에 바로 밀어넣지 않고, 화면에서 "권장값"으로 보여준 뒤 사용자가 채울지
 * 고르게 한다. 숫자를 직접 고치는 기존 방식도 그대로 남아 있다.
 *
 * 계산이 여기 따로 나와 있는 이유는 **화면 없이 테스트할 수 있게** 하기 위함이다.
 */
object TargetCalculator {

    /**
     * 유지 칼로리 = 체중(kg) × 이 계수.
     *
     * 정밀한 TDEE 공식(Mifflin-St Jeor 등)은 키·나이·성별이 필요한데 이 앱은 그걸 안 받는다.
     * 체중당 계수는 근력운동을 하는 성인에게 흔히 쓰는 어림법이고, 어차피 실제 체중 변화를
     * 보며 조정하는 게 정석이라 시작점으로 충분하다.
     */
    private fun maintenancePerKg(level: ActivityLevel): Double = when (level) {
        ActivityLevel.LOW -> 29.0     // 좌식 생활 + 주 1~2회 운동
        ActivityLevel.MEDIUM -> 33.0  // 주 3~5회 운동
        ActivityLevel.HIGH -> 37.0    // 주 6회 이상 또는 몸 쓰는 직업
    }

    /** 목적에 따른 칼로리 보정. 린매스업은 급격한 증량보다 완만한 흑자를 노린다. */
    private fun calorieFactor(goal: GoalType): Double = when (goal) {
        GoalType.BULK -> 1.12      // +12%
        GoalType.MAINTAIN -> 1.0
        GoalType.CUT -> 0.82       // −18%
    }

    /**
     * 체중 1kg당 단백질(g).
     * 컷팅에서 더 높이는 이유는 칼로리를 줄일 때 근손실을 막기 위함이다.
     */
    private fun proteinPerKg(goal: GoalType): Double = when (goal) {
        GoalType.BULK -> 1.8
        GoalType.MAINTAIN -> 1.8
        GoalType.CUT -> 2.2
    }

    /** 총 칼로리에서 지방이 차지할 비율. 호르몬 유지에 필요한 최소선을 지키는 값. */
    private const val FAT_RATIO = 0.25

    /** 당류 권장 상한 — 총 칼로리의 10% (WHO 권고). */
    private const val SUGAR_RATIO = 0.10

    fun suggest(weightKg: Double, goal: GoalType, activityLevel: ActivityLevel): DietTarget {
        val kcal = weightKg * maintenancePerKg(activityLevel) * calorieFactor(goal)
        val protein = weightKg * proteinPerKg(goal)
        val fat = kcal * FAT_RATIO / 9.0

        // 탄수는 남은 칼로리를 채운다. 단백질·지방을 먼저 확보한 뒤 나머지라는 뜻.
        val remaining = kcal - protein * 4.0 - fat * 9.0
        val carbs = (remaining / 4.0).coerceAtLeast(0.0)

        val sugar = kcal * SUGAR_RATIO / 4.0

        return DietTarget(
            kcal = round5(kcal),
            protein = round1(protein),
            carbs = round1(carbs),
            fat = round1(fat),
            sugar = round1(sugar),
            goal = goal,
            activityLevel = activityLevel
        )
    }

    /** 칼로리는 5 단위로 끊어야 읽기 편하다. 어차피 어림값이라 정밀도가 의미 없다. */
    private fun round5(value: Double): Double = (value / 5.0).roundToInt() * 5.0

    private fun round1(value: Double): Double = value.roundToInt().toDouble()
}
