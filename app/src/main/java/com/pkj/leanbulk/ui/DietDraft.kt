package com.pkj.leanbulk.ui

import com.pkj.leanbulk.data.DietTemplateMeal
import com.pkj.leanbulk.data.FoodItem
import com.pkj.leanbulk.data.MacroEstimator
import com.pkj.leanbulk.data.MacroSource
import com.pkj.leanbulk.data.Macros
import com.pkj.leanbulk.data.MealEntry
import com.pkj.leanbulk.data.MealEntryWithFoods
import com.pkj.leanbulk.data.MealFood
import kotlin.math.roundToInt

/** 끼니 안 음식 한 줄. 화면에서 편집 중인, 아직 저장 전 상태([MealFood]와 같은 모양). */
data class DraftMealFood(
    val foodId: Long?,
    val name: String,
    val multiplier: Double,
    val amountGram: Double?,
    val macros: Macros,
    val macroSource: MacroSource
)

/** 카탈로그 값 × 배수로 음식 줄 하나를 만든다. */
fun FoodItem.toDraftMealFood(multiplier: Double): DraftMealFood = DraftMealFood(
    foodId = id,
    name = name,
    multiplier = multiplier,
    amountGram = baseAmountGram?.times(multiplier),
    macros = macros.scaledBy(multiplier),
    macroSource = MacroSource.CATALOG
)

/** 화면에서 편집 중인 끼니 한 줄. 아직 저장 전 상태. */
data class DraftMeal(
    val slotName: String,
    val menuText: String,
    val macros: Macros = Macros(),
    val eaten: Boolean = false,
    /** 이 매크로가 어디서 온 값인가. 비어 있으면 [MacroSource.NONE]. */
    val macroSource: MacroSource = MacroSource.NONE,
    /** 음식 줄들. 채워져 있으면 새 방식이다 — 자세한 건 [isFoodBased] 참고. */
    val foods: List<DraftMealFood> = emptyList(),
    /**
     * 새 방식(음식 줄)으로 편집 중인지.
     *
     * `foods.isNotEmpty()`만으로 판단하지 않는 이유: 막 새 방식을 고르고 아직 음식을 하나도
     * 안 담았을 때도 새 방식 화면을 보여줘야 하는데, 그 순간엔 `foods`가 비어 있다.
     */
    val isFoodBased: Boolean = foods.isNotEmpty()
) {
    /**
     * 매크로를 직접 넣지 않았을 때만 쓰는 참고용 어림값. 새 방식 끼니에는 안 쓴다 —
     * 매크로가 늘 카탈로그에서 계산되어 있어서 "모름"이 나올 일이 없다.
     */
    val estimate: Macros? get() = if (!isFoodBased && macros.isEmpty) MacroEstimator.estimate(menuText) else null
}

/**
 * 사용자가 숫자를 직접 고칠 때 쓴다. **출처를 같이 바꿔야** 카탈로그에서 채운 값이나
 * 확정한 어림값을 손으로 고쳤을 때 표시가 옛것으로 남지 않는다.
 * 전부 지우면 다시 "모름"이 된다.
 */
fun DraftMeal.editMacros(transform: (Macros) -> Macros): DraftMeal {
    val next = transform(macros)
    return copy(
        macros = next,
        macroSource = if (next.isEmpty) MacroSource.NONE else MacroSource.ENTERED
    )
}

/**
 * 음식 줄이 바뀐 뒤 끼니 요약(메뉴 텍스트·매크로·출처)을 다시 계산한다.
 *
 * 이렇게 **`meal_entry` 컬럼 자체에 합계를 그대로 채워 넣어 두는 이유**는, 히스토리·
 * xlsx 내보내기·오늘 섭취 합계가 전부 `MealEntry.macros`/`menuText`를 직접 읽기
 * 때문이다. 음식 줄(`meal_food`)은 그 값의 "근거"이지, 저 화면들이 새로 알아야 하는
 * 별도 데이터가 아니다 — 덕분에 이번 개편에서 그 화면들을 하나도 안 건드려도 된다.
 */
fun DraftMeal.recomputeFromFoods(): DraftMeal {
    if (!isFoodBased) return this
    return copy(
        menuText = foods.summaryText(),
        macros = foods.summedMacros(),
        macroSource = if (foods.isEmpty()) MacroSource.NONE else MacroSource.CATALOG
    )
}

private fun List<DraftMealFood>.summaryText(): String = joinToString(" + ") { f ->
    val qty = f.amountGram?.let { "${it.roundToInt()}g" } ?: "×${formatMultiplier(f.multiplier)}"
    "${f.name} $qty"
}

/** "2.0" 대신 "2"로 — 이 문자열은 [MealEntry.menuText]로 그대로 저장돼 히스토리·xlsx에 나간다. */
private fun formatMultiplier(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

/**
 * 음식 줄들의 매크로를 더한다. 한 줄이라도 그 항목이 `null`(모름)이면 합계도 `null`이다 —
 * 모르는 값이 섞였는데 더해서 아는 척 숫자를 내놓으면 안 된다.
 */
private fun List<DraftMealFood>.summedMacros(): Macros {
    if (isEmpty()) return Macros()
    fun sum(pick: (Macros) -> Double?): Double? {
        val values = map { pick(it.macros) }
        return if (values.any { it == null }) null else values.sumOf { it!! }
    }
    return Macros(
        kcal = sum { it.kcal },
        protein = sum { it.protein },
        carbs = sum { it.carbs },
        fat = sum { it.fat },
        sugar = sum { it.sugar }
    )
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

/**
 * 아직 아무것도 안 적은 끼니뿐인지. 기본으로 채워둔 아침/점심/저녁/간식(§9.1)은 그 자체로는
 * "내용"이 아니므로, 식단표를 불러올 때 갈아엎기 확인을 띄울지 판단하는 데 쓴다 —
 * `isEmpty()`만 보면 이 기본 4칸 때문에 매번 확인 창이 뜬다.
 */
fun List<DraftMeal>.hasContent(): Boolean =
    any { it.menuText.isNotBlank() || !it.macros.isEmpty || it.foods.isNotEmpty() }

fun DietTemplateMeal.toDraft(): DraftMeal = DraftMeal(
    slotName = slotName,
    menuText = menuText,
    macros = macros,
    // 식단표는 출처를 담지 않는다. 계획 단계의 값이라 사용자가 넣은 것으로 본다.
    macroSource = if (macros.isEmpty) MacroSource.NONE else MacroSource.ENTERED
)

/**
 * 끼니 + 그 안의 음식 줄을 화면 상태로 옮긴다.
 * 음식 줄이 하나라도 있으면 [DraftMeal.isFoodBased]가 자동으로 참이 된다.
 */
fun MealEntryWithFoods.toDraft(): DraftMeal {
    val draftFoods = foods.sortedBy { it.orderIndex }.map {
        DraftMealFood(
            foodId = it.foodId,
            name = it.name,
            multiplier = it.multiplier,
            amountGram = it.amountGram,
            macros = it.macros,
            macroSource = it.macroSource
        )
    }
    return DraftMeal(
        slotName = entry.slotName,
        menuText = entry.menuText,
        macros = entry.macros,
        eaten = entry.eaten,
        macroSource = entry.macroSource,
        foods = draftFoods
    )
}

/** 화면의 끼니 목록을 저장 가능한 (끼니, 음식 줄들) 쌍으로 편다. */
fun List<DraftMeal>.toMealEntryPairs(date: String): List<Pair<MealEntry, List<MealFood>>> =
    mapIndexed { index, m ->
        val entry = MealEntry(
            date = date,
            orderIndex = index,
            slotName = m.slotName,
            menuText = m.menuText,
            macros = m.macros,
            eaten = m.eaten,
            macroSource = m.macroSource
        )
        val foods = m.foods.mapIndexed { foodIndex, f ->
            MealFood(
                mealEntryId = 0,
                orderIndex = foodIndex,
                foodId = f.foodId,
                name = f.name,
                multiplier = f.multiplier,
                amountGram = f.amountGram,
                macros = f.macros,
                macroSource = f.macroSource
            )
        }
        entry to foods
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
