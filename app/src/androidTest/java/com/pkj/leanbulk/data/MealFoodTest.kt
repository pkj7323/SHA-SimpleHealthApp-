package com.pkj.leanbulk.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 음식이 g 단위로 계산되는 새 구조(`meal_food`)를 검증한다(DIET-REDESIGN.md).
 *
 * 세 가지를 못 박는다:
 *  - 배수를 곱한 매크로·그램 계산이 맞는지
 *  - 기준량을 모르는 음식은 배수를 곱해도 그램이 여전히 `null`인지(지어내지 않는다)
 *  - 끼니를 지우면 그 안의 음식 줄도 CASCADE로 같이 사라지는지
 */
@RunWith(AndroidJUnit4::class)
class MealFoodTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: Repository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repo = Repository(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun 배수를_곱한_매크로와_그램이_계산된다() {
        val chicken = FoodItem(
            id = 1, slug = "chicken-breast-100g", name = "닭가슴살", category = "육류",
            macros = Macros(kcal = 165.0, protein = 31.0, carbs = 0.0, fat = 3.6, sugar = 0.0),
            source = ItemSource.SEED, baseAmount = 100.0
        )

        val line = chicken.toMealFood(mealEntryId = 0, orderIndex = 0, multiplier = 1.5)

        assertEquals(247.5, line.macros.kcal!!, 0.001)
        assertEquals(46.5, line.macros.protein!!, 0.001)
        assertEquals("기준량 × 배수여야 한다", 150.0, line.amount!!, 0.001)
        assertEquals(MacroSource.CATALOG, line.macroSource)
    }

    /**
     * "김밥 1줄"처럼 그램을 모르는 음식은 배수를 곱해도 그램을 만들어내면 안 된다.
     * 매크로는 "1줄 몫 × 배수"로 정상 계산된다 — 그램을 몰라도 문제없다.
     */
    @Test
    fun 기준량을_모르면_배수를_곱해도_그램은_null이다() {
        val gimbap = FoodItem(
            id = 2, slug = "gimbap-1roll", name = "김밥", category = "간편식",
            macros = Macros(kcal = 400.0, protein = 10.0, carbs = 68.0, fat = 9.0, sugar = 3.0),
            source = ItemSource.SEED, baseAmount = null
        )

        val line = gimbap.toMealFood(mealEntryId = 0, orderIndex = 0, multiplier = 2.0)

        assertEquals(800.0, line.macros.kcal!!, 0.001)
        assertNull("그램을 모르는데 계산해내면 안 된다", line.amount)
    }

    /** null(모름)에 배수를 곱해도 여전히 null이어야 한다 — 모르는 값에서 숫자를 만들어내면 안 된다. */
    @Test
    fun scaledBy는_null을_그대로_null로_남긴다() {
        val macros = Macros(kcal = 100.0, protein = null)
        val scaled = macros.scaledBy(3.0)

        assertEquals(300.0, scaled.kcal!!, 0.001)
        assertNull(scaled.protein)
    }

    @Test
    fun 끼니를_저장하고_불러오면_음식_줄이_그대로_남는다() = runBlocking {
        val chicken = FoodItem(
            slug = "chicken-breast-100g", name = "닭가슴살", category = "육류",
            macros = Macros(kcal = 165.0, protein = 31.0), source = ItemSource.SEED, baseAmount = 100.0
        )
        val foodId = db.foodDao().run { insert(chicken); findBySlug(chicken.slug)!!.id }

        val entry = MealEntry(
            date = "2026-09-06", orderIndex = 0, slotName = "점심", menuText = "",
            macros = Macros(), eaten = true
        )
        val food = chicken.copy(id = foodId).toMealFood(mealEntryId = 0, orderIndex = 0, multiplier = 1.5)

        repo.saveMealDay("2026-09-06", listOf(entry to listOf(food)))

        val saved = repo.observeMealsWithFoods("2026-09-06").first().single()
        assertEquals(1, saved.foods.size)
        val savedFood = saved.foods.single()
        assertEquals("닭가슴살", savedFood.name)
        assertEquals(150.0, savedFood.amount!!, 0.001)
        assertEquals(247.5, savedFood.macros.kcal!!, 0.001)
    }

    /** meal_food는 meal_entry의 자식이다. 부모(그 날짜의 끼니)를 지우면 CASCADE로 같이 사라져야 한다. */
    @Test
    fun 끼니를_다시_저장하면_옛_음식_줄은_남지_않는다() = runBlocking {
        val entry = MealEntry(
            date = "2026-09-06", orderIndex = 0, slotName = "점심", menuText = "",
            macros = Macros(), eaten = true
        )
        val firstFood = MealFood(
            mealEntryId = 0, orderIndex = 0, name = "닭가슴살", multiplier = 1.0,
            amount = 100.0, macros = Macros(kcal = 165.0), macroSource = MacroSource.CATALOG
        )
        repo.saveMealDay("2026-09-06", listOf(entry to listOf(firstFood)))

        // 같은 날짜를 다른 구성으로 덮어쓴다.
        repo.saveMealDay("2026-09-06", listOf(entry to emptyList()))

        val saved = repo.observeMealsWithFoods("2026-09-06").first().single()
        assertTrue("옛 음식 줄이 CASCADE 없이 남았다", saved.foods.isEmpty())
    }
}
