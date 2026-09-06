package com.pkj.leanbulk.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pkj.leanbulk.data.FoodItem
import com.pkj.leanbulk.data.ItemSource
import com.pkj.leanbulk.data.Macros
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 음식 선택 · 수량 화면을 실제로 렌더링해서 검증한다(DIET-REDESIGN.md 2단계).
 *
 * 화면 하나 바꿀 때마다 에뮬레이터에서 스크린샷으로 눈으로 확인하는 대신, 여기서
 * 컴포저블을 직접 그려 클릭·입력을 흉내 내고 결과를 단언한다.
 */
@RunWith(AndroidJUnit4::class)
class FoodQuantityPickerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val chicken = FoodItem(
        id = 1, slug = "chicken-breast-100g", name = "닭가슴살", category = "육류",
        macros = Macros(kcal = 165.0, protein = 31.0, carbs = 0.0, fat = 3.6, sugar = 0.0),
        source = ItemSource.SEED, baseAmountGram = 100.0
    )

    /** 그램을 모르는 음식. 배수를 곱해도 그램을 지어내면 안 된다. */
    private val gimbap = FoodItem(
        id = 2, slug = "gimbap-1roll", name = "김밥", category = "간편식",
        macros = Macros(kcal = 400.0, protein = 10.0, carbs = 68.0, fat = 9.0, sugar = 3.0),
        source = ItemSource.SEED, baseAmountGram = null
    )

    @Test
    fun 배수를_늘리면_그램과_매크로가_같이_늘어난다() {
        var confirmed: Double? = null
        composeRule.setContent {
            MaterialTheme {
                FoodQuantityDialog(food = chicken, onConfirm = { confirmed = it }, onDismiss = {})
            }
        }

        // 초기값(배수 1.0) = 기준량 그대로.
        composeRule.onNodeWithTag("food_quantity_gram").assertTextContains("100g")
        composeRule.onNodeWithTag("food_quantity_macros").assertTextContains("165kcal", substring = true)

        composeRule.onNodeWithContentDescription("배수 늘리기").performClick()

        // 1.0 + step(0.5) = 1.5배 → 그램도 매크로도 1.5배여야 한다.
        composeRule.onNodeWithTag("food_quantity_gram").assertTextContains("150g")
        composeRule.onNodeWithTag("food_quantity_macros").assertTextContains("248kcal", substring = true) // 165 * 1.5 반올림

        composeRule.onNodeWithText("추가").performClick()
        assertEquals("확정한 배수가 콜백으로 안 넘어왔다", 1.5, confirmed!!, 0.001)
    }

    @Test
    fun 기준량을_모르는_음식은_그램을_안_보여준다() {
        composeRule.setContent {
            MaterialTheme {
                FoodQuantityDialog(food = gimbap, onConfirm = {}, onDismiss = {})
            }
        }

        composeRule.onNodeWithTag("food_quantity_gram").assertDoesNotExist()
        // 그램은 몰라도 "1줄 몫"의 매크로는 알고 있으므로 이건 보여야 한다.
        composeRule.onNodeWithTag("food_quantity_macros").assertTextContains("400kcal", substring = true)
    }

    @Test
    fun 검색으로_거르고_고른_음식이_그대로_확정된다() {
        var picked: Pair<FoodItem, Double>? = null
        composeRule.setContent {
            MaterialTheme {
                FoodQuantityPickerDialog(
                    foods = listOf(chicken, gimbap),
                    onConfirm = { food, multiplier -> picked = food to multiplier },
                    onToggleFavorite = {},
                    onAddNewFood = { _, _, _, _ -> chicken },
                    onHide = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText("닭가슴살").assertExists()
        composeRule.onNodeWithText("김밥").assertExists()

        composeRule.onNodeWithTag("food_search_query").performTextInput("닭")

        composeRule.onNodeWithText("닭가슴살").assertExists()
        composeRule.onNodeWithText("김밥").assertDoesNotExist()

        composeRule.onNodeWithText("닭가슴살").performClick()
        // 수량 단계로 넘어갔으면 검색창은 잠시 사라진다.
        composeRule.onNodeWithTag("food_search_query").assertDoesNotExist()

        composeRule.onNodeWithText("추가").performClick()

        assertEquals("닭가슴살", picked!!.first.name)
        assertEquals(1.0, picked!!.second, 0.001)
        // 확정하고 나면 다른 음식을 더 담을 수 있게 검색 목록으로 돌아와야 한다.
        composeRule.onNodeWithTag("food_search_query").assertExists()
    }
}
