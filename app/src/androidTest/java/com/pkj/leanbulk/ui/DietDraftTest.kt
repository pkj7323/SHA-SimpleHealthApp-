package com.pkj.leanbulk.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pkj.leanbulk.data.MacroSource
import com.pkj.leanbulk.data.Macros
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 음식 줄 방식 끼니의 순수 계산 로직을 검증한다(DIET-REDESIGN.md 3단계).
 *
 * DB나 화면 없이 [DraftMeal]/[DraftMealFood]만으로 도는 로직이라 Room·Compose 없이 빠르게
 * 돈다. Repository·DAO 수준 검증은 `MealFoodTest`(1단계)가 이미 맡고 있다.
 */
@RunWith(AndroidJUnit4::class)
class DietDraftTest {

    private fun food(name: String, kcal: Double?, protein: Double?, gram: Double?, multiplier: Double = 1.0) =
        DraftMealFood(
            foodId = 1, name = name, multiplier = multiplier, amountGram = gram,
            macros = Macros(kcal = kcal, protein = protein, carbs = 0.0, fat = 0.0, sugar = 0.0),
            macroSource = MacroSource.CATALOG
        )

    @Test
    fun 음식_줄_매크로를_더해서_끼니_요약을_다시_계산한다() {
        val meal = DraftMeal(
            slotName = "점심",
            foods = listOf(
                food("닭가슴살", kcal = 247.5, protein = 46.5, gram = 150.0),
                food("현미밥", kcal = 300.0, protein = 6.0, gram = 200.0)
            )
        ).recomputeFromFoods()

        assertEquals(547.5, meal.macros.kcal!!, 0.001)
        assertEquals(52.5, meal.macros.protein!!, 0.001)
        assertEquals(MacroSource.CATALOG, meal.macroSource)
        assertEquals("닭가슴살 150g + 현미밥 200g", meal.menuText)
    }

    /** 그램을 모르는 음식은 요약 텍스트에서도 그램을 지어내지 않고 배수로 적는다. */
    @Test
    fun 그램_모르는_음식은_요약에_배수로_적힌다() {
        val meal = DraftMeal(
            slotName = "저녁",
            foods = listOf(food("김밥", kcal = 800.0, protein = 20.0, gram = null, multiplier = 2.0))
        ).recomputeFromFoods()

        assertEquals("김밥 ×2", meal.menuText)
    }

    /**
     * 음식 줄 하나라도 그 항목이 모르는 값(null)이면 합계도 모른다로 남아야 한다.
     * 모르는 값을 0 취급해서 더하면 "정말 0"과 구분이 안 된다.
     */
    @Test
    fun 한_줄이라도_모르면_합계도_모른다() {
        val meal = DraftMeal(
            slotName = "간식",
            foods = listOf(
                food("아몬드", kcal = 87.0, protein = null, gram = 15.0),
                food("바나나", kcal = 105.0, protein = 1.3, gram = null)
            )
        ).recomputeFromFoods()

        assertEquals(192.0, meal.macros.kcal!!, 0.001)
        assertNull("한쪽 단백질이 모르는데 합계가 나오면 안 된다", meal.macros.protein)
    }

    /** 음식을 다 지우면 다시 "모름"이 된다(0으로 남으면 "정말 0을 먹음"과 헷갈린다). */
    @Test
    fun 음식이_없으면_매크로도_모름이다() {
        val meal = DraftMeal(slotName = "점심", foods = emptyList())
            .recomputeFromFoods()

        assertTrue(meal.macros.isEmpty)
        assertEquals(MacroSource.NONE, meal.macroSource)
        assertEquals("", meal.menuText)
    }

    @Test
    fun 저장된_게_없어도_고정_네_칸이_나온다() {
        val slots = emptyList<DraftMeal>().intoFixedSlots()

        assertEquals(listOf("아침", "점심", "저녁", "간식"), slots.map { it.slotName })
        assertTrue("빈 칸에 뭔가 들어있으면 안 된다", slots.all { it.foods.isEmpty() && it.menuText.isBlank() })
    }

    /** 이름이 같은 기록은 그 칸에 들어가야 한다. 순서가 뒤죽박죽이어도 마찬가지다. */
    @Test
    fun 저장된_끼니가_이름이_같은_칸에_들어간다() {
        val saved = listOf(
            DraftMeal(slotName = "저녁", foods = listOf(food("닭가슴살", 165.0, 31.0, 100.0))),
            DraftMeal(slotName = "아침", menuText = "햇반")
        )

        val slots = saved.intoFixedSlots()

        assertEquals(listOf("아침", "점심", "저녁", "간식"), slots.map { it.slotName })
        assertEquals("햇반", slots[0].menuText)
        assertEquals(1, slots[2].foods.size)
    }

    /**
     * 고정 칸 이름과 안 맞는 기록(옛날에 "끼니 1" 식으로 만든 것, 나중에 추가한 5번째 끼니)은
     * 뒤에 그대로 붙는다 — 화면 구조가 바뀌었다고 남의 기록을 지우면 안 된다.
     */
    @Test
    fun 이름이_안_맞는_기록은_뒤에_그대로_붙는다() {
        val saved = listOf(
            DraftMeal(slotName = "끼니 1", menuText = "옛날 기록"),
            DraftMeal(slotName = "야식", menuText = "라면")
        )

        val slots = saved.intoFixedSlots()

        assertEquals(6, slots.size)
        assertEquals(listOf("끼니 1", "야식"), slots.drop(4).map { it.slotName })
        assertEquals("옛날 기록", slots[4].menuText)
    }
}
