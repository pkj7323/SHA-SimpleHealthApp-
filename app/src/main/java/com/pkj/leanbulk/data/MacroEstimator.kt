package com.pkj.leanbulk.data

/**
 * 매크로를 입력하지 않은 끼니의 "어림값"을 메뉴 이름에서 추정한다.
 *
 * 어디까지나 참고용 대략치이므로 실제 합계에는 절대 섞지 않고,
 * 화면에는 항상 괄호로 감싸 따로 보여준다.
 */
object MacroEstimator {

    /** 흔한 음식의 1인분 대략값. (칼로리, 단백질, 탄수, 지방, 당) */
    private val table: List<Pair<List<String>, Macros>> = listOf(
        listOf("현미밥", "잡곡밥", "흰쌀밥", "쌀밥", "공기밥", "즉석밥", "밥") to
            Macros(kcal = 300.0, protein = 6.0, carbs = 66.0, fat = 1.0, sugar = 0.0),
        listOf("닭가슴살") to Macros(kcal = 165.0, protein = 31.0, carbs = 0.0, fat = 4.0, sugar = 0.0),
        listOf("계란", "달걀") to Macros(kcal = 78.0, protein = 6.0, carbs = 1.0, fat = 5.0, sugar = 1.0),
        listOf("프로틴", "쉐이크", "단백질보충제") to Macros(kcal = 120.0, protein = 24.0, carbs = 3.0, fat = 2.0, sugar = 2.0),
        listOf("그릭요거트") to Macros(kcal = 130.0, protein = 15.0, carbs = 8.0, fat = 4.0, sugar = 6.0),
        listOf("바나나") to Macros(kcal = 105.0, protein = 1.0, carbs = 27.0, fat = 0.0, sugar = 14.0),
        listOf("고구마") to Macros(kcal = 130.0, protein = 2.0, carbs = 30.0, fat = 0.0, sugar = 9.0),
        listOf("소고기", "스테이크") to Macros(kcal = 250.0, protein = 26.0, carbs = 0.0, fat = 15.0, sugar = 0.0),
        listOf("돼지고기", "삼겹살") to Macros(kcal = 330.0, protein = 20.0, carbs = 0.0, fat = 27.0, sugar = 0.0),
        listOf("연어", "고등어", "생선") to Macros(kcal = 200.0, protein = 22.0, carbs = 0.0, fat = 12.0, sugar = 0.0),
        listOf("두부") to Macros(kcal = 120.0, protein = 13.0, carbs = 3.0, fat = 7.0, sugar = 1.0),
        listOf("라면") to Macros(kcal = 500.0, protein = 10.0, carbs = 70.0, fat = 18.0, sugar = 4.0),
        listOf("김밥") to Macros(kcal = 480.0, protein = 12.0, carbs = 80.0, fat = 12.0, sugar = 5.0),
        listOf("샌드위치", "토스트", "식빵") to Macros(kcal = 350.0, protein = 14.0, carbs = 40.0, fat = 14.0, sugar = 6.0),
        listOf("치킨") to Macros(kcal = 600.0, protein = 40.0, carbs = 30.0, fat = 35.0, sugar = 5.0),
        listOf("피자") to Macros(kcal = 550.0, protein = 22.0, carbs = 60.0, fat = 24.0, sugar = 8.0),
        listOf("햄버거", "버거") to Macros(kcal = 550.0, protein = 25.0, carbs = 45.0, fat = 30.0, sugar = 9.0),
        listOf("샐러드") to Macros(kcal = 150.0, protein = 5.0, carbs = 12.0, fat = 9.0, sugar = 5.0),
        listOf("오트밀") to Macros(kcal = 150.0, protein = 5.0, carbs = 27.0, fat = 3.0, sugar = 1.0),
        listOf("우유", "두유") to Macros(kcal = 130.0, protein = 7.0, carbs = 12.0, fat = 5.0, sugar = 10.0),
        listOf("견과류", "아몬드", "호두") to Macros(kcal = 90.0, protein = 3.0, carbs = 3.0, fat = 8.0, sugar = 1.0),
        listOf("국수", "칼국수", "우동", "파스타") to Macros(kcal = 500.0, protein = 15.0, carbs = 80.0, fat = 12.0, sugar = 5.0),
        listOf("찌개", "국", "탕") to Macros(kcal = 250.0, protein = 15.0, carbs = 12.0, fat = 14.0, sugar = 3.0)
    )

    /**
     * 메뉴 이름에 들어 있는 음식들을 찾아 대략값을 더한다.
     * 하나도 못 찾으면 null(= 추정 불가).
     */
    fun estimate(menuText: String): Macros? {
        if (menuText.isBlank()) return null

        val matched = table.filter { (keywords, _) -> keywords.any { menuText.contains(it) } }
        if (matched.isEmpty()) return null

        var kcal = 0.0
        var protein = 0.0
        var carbs = 0.0
        var fat = 0.0
        var sugar = 0.0
        matched.forEach { (_, m) ->
            kcal += m.kcal ?: 0.0
            protein += m.protein ?: 0.0
            carbs += m.carbs ?: 0.0
            fat += m.fat ?: 0.0
            sugar += m.sugar ?: 0.0
        }
        return Macros(kcal, protein, carbs, fat, sugar)
    }
}
