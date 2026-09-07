package com.pkj.leanbulk.ui

import com.pkj.leanbulk.data.AmountUnit
import com.pkj.leanbulk.data.FoodItem
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
    val amount: Double?,
    val amountUnit: AmountUnit,
    val macros: Macros,
    val macroSource: MacroSource
)

/** 카탈로그 값 × 배수로 음식 줄 하나를 만든다. */
fun FoodItem.toDraftMealFood(multiplier: Double): DraftMealFood = DraftMealFood(
    foodId = id,
    name = name,
    multiplier = multiplier,
    amount = baseAmount?.times(multiplier),
    amountUnit = baseAmountUnit,
    macros = macros.scaledBy(multiplier),
    macroSource = MacroSource.CATALOG
)

/**
 * 화면에서 편집 중인 끼니 한 줄. 아직 저장 전 상태.
 *
 * 끼니는 이제 **음식 줄의 합**으로만 채운다. [menuText]·[macros]는 그 합을 그대로 복사해둔
 * 값이라 사람이 직접 고치지 않는다([recomputeFromFoods]) — 다만 자유 텍스트로 적던 시절의
 * 기록을 불러오면 그때 적어둔 문장이 [menuText]에 남아 있고, 음식을 담기 전까지 그대로 보여준다.
 */
data class DraftMeal(
    val slotName: String,
    val menuText: String = "",
    val macros: Macros = Macros(),
    /** 이 매크로가 어디서 온 값인가. 비어 있으면 [MacroSource.NONE]. */
    val macroSource: MacroSource = MacroSource.NONE,
    val foods: List<DraftMealFood> = emptyList(),
    /** 이 끼니에 적어둔 글. 음식 줄과 따로 놀아서 [recomputeFromFoods]가 건드리지 않는다. */
    val memo: String = ""
)

/**
 * 음식 줄이 바뀐 뒤 끼니 요약(메뉴 텍스트·매크로·출처)을 다시 계산한다.
 *
 * 이렇게 **`meal_entry` 컬럼 자체에 합계를 그대로 채워 넣어 두는 이유**는, 히스토리·
 * xlsx 내보내기·오늘 섭취 합계가 전부 `MealEntry.macros`/`menuText`를 직접 읽기
 * 때문이다. 음식 줄(`meal_food`)은 그 값의 "근거"이지, 저 화면들이 새로 알아야 하는
 * 별도 데이터가 아니다.
 */
fun DraftMeal.recomputeFromFoods(): DraftMeal = copy(
    menuText = foods.summaryText(),
    macros = foods.summedMacros(),
    macroSource = if (foods.isEmpty()) MacroSource.NONE else MacroSource.CATALOG
)

private fun List<DraftMealFood>.summaryText(): String = joinToString(" + ") { f ->
    val qty = f.amount?.let { "${it.roundToInt()}${f.amountUnit.label}" } ?: "×${formatMultiplier(f.multiplier)}"
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

/** 하루 합계. [unknownCount]는 영양정보를 모르는 채로 담긴 끼니 수다. */
data class DietTotals(
    val entered: Macros,
    val unknownCount: Int
)

/**
 * 담긴 끼니를 전부 더한다.
 *
 * 예전엔 "먹었다" 체크를 한 끼니만 셌지만, 끼니 칸이 아침·점심·저녁·간식으로 고정되면서
 * 그 체크는 없앴다 — 안 먹었으면 음식을 안 담으면 되기 때문에 따로 표시할 이유가 없다.
 */
fun List<DraftMeal>.totals(): DietTotals {
    var kcal = 0.0; var protein = 0.0; var carbs = 0.0; var fat = 0.0; var sugar = 0.0
    var unknown = 0

    forEach { meal ->
        if (!meal.macros.isEmpty) {
            kcal += meal.macros.kcal ?: 0.0
            protein += meal.macros.protein ?: 0.0
            carbs += meal.macros.carbs ?: 0.0
            fat += meal.macros.fat ?: 0.0
            sugar += meal.macros.sugar ?: 0.0
        } else if (meal.foods.isNotEmpty() || meal.menuText.isNotBlank()) {
            // 아무것도 안 담은 빈 칸은 "모름"이 아니다. 담긴 게 있는데 값을 모를 때만 센다.
            unknown++
        }
    }

    return DietTotals(entered = Macros(kcal, protein, carbs, fat, sugar), unknownCount = unknown)
}

/** 화면에 늘 띄워두는 끼니 칸(DIET-REDESIGN.md §9.1). 이름은 고치지 않는다. */
val FIXED_MEAL_SLOTS = listOf("아침", "점심", "저녁", "간식")

/**
 * 저장된 끼니를 고정 칸에 끼워 맞춘다.
 *
 * 저장된 게 있든 없든 [FIXED_MEAL_SLOTS] 네 칸은 **항상** 자리를 차지한다. 예전 기록은
 * 이름이 같은 칸에 들어가고, 이름이 안 맞는 것(옛날에 "끼니 1" 식으로 만들어둔 것,
 * 사용자가 나중에 추가한 5번째 끼니)은 뒤에 그대로 붙는다 — 화면 구조가 바뀌었다고
 * 남의 기록을 지우지 않는다.
 */
fun List<DraftMeal>.intoFixedSlots(): List<DraftMeal> {
    val leftovers = toMutableList()
    val fixed = FIXED_MEAL_SLOTS.map { name ->
        val match = leftovers.firstOrNull { it.slotName == name }
        if (match != null) leftovers.remove(match)
        match ?: DraftMeal(slotName = name)
    }
    return fixed + leftovers
}

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
            amount = it.amount,
            amountUnit = it.amountUnit,
            macros = it.macros,
            macroSource = it.macroSource
        )
    }
    return DraftMeal(
        slotName = entry.slotName,
        menuText = entry.menuText,
        macros = entry.macros,
        macroSource = entry.macroSource,
        foods = draftFoods,
        memo = entry.memo
    )
}

/**
 * 화면의 끼니 목록을 저장 가능한 (끼니, 음식 줄들) 쌍으로 편다.
 *
 * `eaten`은 늘 참으로 저장한다 — "먹었다" 체크를 없앴으므로, 담긴 끼니는 곧 먹은 끼니다.
 * 히스토리·xlsx가 아직 이 칸을 읽기 때문에 컬럼 자체는 남겨둔다.
 */
fun List<DraftMeal>.toMealEntryPairs(date: String): List<Pair<MealEntry, List<MealFood>>> =
    mapIndexed { index, m ->
        val entry = MealEntry(
            date = date,
            orderIndex = index,
            slotName = m.slotName,
            menuText = m.menuText,
            macros = m.macros,
            eaten = true,
            memo = m.memo,
            macroSource = m.macroSource
        )
        val foods = m.foods.mapIndexed { foodIndex, f ->
            MealFood(
                mealEntryId = 0,
                orderIndex = foodIndex,
                foodId = f.foodId,
                name = f.name,
                multiplier = f.multiplier,
                amount = f.amount,
                amountUnit = f.amountUnit,
                macros = f.macros,
                macroSource = f.macroSource
            )
        }
        entry to foods
    }
