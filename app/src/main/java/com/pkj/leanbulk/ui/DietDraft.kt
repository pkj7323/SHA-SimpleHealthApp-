package com.pkj.leanbulk.ui

import com.pkj.leanbulk.data.DietTemplateMeal
import com.pkj.leanbulk.data.MacroEstimator
import com.pkj.leanbulk.data.Macros
import com.pkj.leanbulk.data.MealEntry

/** 화면에서 편집 중인 끼니 한 줄. 아직 저장 전 상태. */
data class DraftMeal(
    val slotName: String,
    val menuText: String,
    val macros: Macros = Macros(),
    val eaten: Boolean = false
) {
    /** 매크로를 직접 넣지 않았을 때만 쓰는 참고용 어림값. 합계에는 섞지 않는다. */
    val estimate: Macros? get() = if (macros.isEmpty) MacroEstimator.estimate(menuText) else null
}

/** 하루 합계. 직접 입력한 값만 더한 것과, 어림값까지 더한 것을 분리해서 들고 있다. */
data class DietTotals(
    val entered: Macros,
    val estimatedExtra: Macros?,
    val unknownCount: Int
)

/** 체크한 끼니만 집계한다. 매크로가 없는 끼니는 어림값 쪽으로 따로 모은다. */
fun List<DraftMeal>.totals(): DietTotals {
    val eaten = filter { it.eaten }

    var kcal = 0.0; var protein = 0.0; var carbs = 0.0; var fat = 0.0; var sugar = 0.0
    var eKcal = 0.0; var eProtein = 0.0; var eCarbs = 0.0; var eFat = 0.0; var eSugar = 0.0
    var hasEstimate = false
    var unknown = 0

    eaten.forEach { meal ->
        if (!meal.macros.isEmpty) {
            kcal += meal.macros.kcal ?: 0.0
            protein += meal.macros.protein ?: 0.0
            carbs += meal.macros.carbs ?: 0.0
            fat += meal.macros.fat ?: 0.0
            sugar += meal.macros.sugar ?: 0.0
        } else {
            unknown++
            val est = meal.estimate
            if (est != null) {
                hasEstimate = true
                eKcal += est.kcal ?: 0.0
                eProtein += est.protein ?: 0.0
                eCarbs += est.carbs ?: 0.0
                eFat += est.fat ?: 0.0
                eSugar += est.sugar ?: 0.0
            }
        }
    }

    return DietTotals(
        entered = Macros(kcal, protein, carbs, fat, sugar),
        estimatedExtra = if (hasEstimate) Macros(eKcal, eProtein, eCarbs, eFat, eSugar) else null,
        unknownCount = unknown
    )
}

fun DietTemplateMeal.toDraft(): DraftMeal =
    DraftMeal(slotName = slotName, menuText = menuText, macros = macros)

fun MealEntry.toDraft(): DraftMeal =
    DraftMeal(slotName = slotName, menuText = menuText, macros = macros, eaten = eaten)

fun List<DraftMeal>.toMealEntries(date: String): List<MealEntry> = mapIndexed { index, m ->
    MealEntry(
        date = date,
        orderIndex = index,
        slotName = m.slotName,
        menuText = m.menuText,
        macros = m.macros,
        eaten = m.eaten
    )
}

fun List<DraftMeal>.toTemplateMeals(): List<DietTemplateMeal> = mapIndexed { index, m ->
    DietTemplateMeal(
        templateId = 0,
        orderIndex = index,
        slotName = m.slotName,
        menuText = m.menuText,
        macros = m.macros
    )
}
