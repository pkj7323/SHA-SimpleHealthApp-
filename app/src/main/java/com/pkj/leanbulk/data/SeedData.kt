package com.pkj.leanbulk.data

/** 린매스업_식단표.md "하루 식단 예시" 표 기반 기본 끼니 */
data class MealTemplate(
    val slotName: String,
    val menuText: String,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val sugar: Double?
)

/** 루틴에 들어가는 종목 하나의 기본값. */
data class ExerciseTemplate(
    val exerciseName: String,
    val defaultSetting: String,
    val defaultWeight: Double,
    val defaultReps: Int
)

/** 하나의 운동 루틴(= 기존 Day 1~4 같은 분할). 사용자가 직접 골라서 불러온다. */
data class RoutineTemplate(
    val name: String,
    val exercises: List<ExerciseTemplate>
)

object SeedData {

    const val TARGET_KCAL = 2550.0
    const val TARGET_PROTEIN = 175.0
    const val TARGET_CARBS = 260.0
    const val TARGET_FAT = 67.0

    /** 린매스업 기준 당류 권장 상한(총 칼로리의 10% 이내). */
    const val TARGET_SUGAR = 60.0

    /** 종목 추가 시 기본으로 깔리는 세트 수 */
    const val DEFAULT_SET_COUNT = 3

    const val DEFAULT_DIET_TEMPLATE_NAME = "기본 식단표 (린매스업)"

    val mealTemplates = listOf(
        MealTemplate("아침", "계란 3개 + 그릭요거트 150g + 바나나 1개", kcal = 420.0, protein = 33.0, carbs = 30.0, fat = 20.0, sugar = 18.0),
        MealTemplate("점심", "현미밥 180g + 닭가슴살 150g + 나물반찬", kcal = 540.0, protein = 40.0, carbs = 75.0, fat = 8.0, sugar = 3.0),
        MealTemplate("운동 전 간식", "고구마 120g + 프로틴쉐이크 1스쿱", kcal = 290.0, protein = 25.0, carbs = 40.0, fat = 3.0, sugar = 12.0),
        MealTemplate("운동 후", "프로틴쉐이크 1.5스쿱 + 바나나 1개", kcal = 320.0, protein = 35.0, carbs = 35.0, fat = 5.0, sugar = 20.0),
        MealTemplate("저녁", "잡곡밥 150g + 소고기(또는 흰살생선) 150g + 야채", kcal = 550.0, protein = 38.0, carbs = 55.0, fat = 20.0, sugar = 4.0),
        MealTemplate("취침 전", "그릭요거트 150g + 견과류 15g", kcal = 200.0, protein = 15.0, carbs = 8.0, fat = 12.0, sugar = 6.0)
    )

    val routines = listOf(
        RoutineTemplate(
            "가슴 & 삼두 & 코어",
            listOf(
                ExerciseTemplate("시티드 프레스 머신 (견갑 예열)", "20kg / 20회 / 2세트", 20.0, 20),
                ExerciseTemplate("스탠딩 페이스풀", "15kg / 15회 / 3세트", 15.0, 15),
                ExerciseTemplate("벤치 프레스 머신 (다리 올림)", "40kg / 12회 / 4세트", 40.0, 12),
                ExerciseTemplate("인클라인 덤벨 프레스 (벤치 30도)", "12~14kg / 12회 / 3세트", 13.0, 12),
                ExerciseTemplate("90도 플라이 머신", "30kg / 12~15회 / 3세트", 30.0, 12),
                ExerciseTemplate("케이블 푸시다운 (이지바)", "35kg / 12회 / 3세트", 35.0, 12),
                ExerciseTemplate("케이블 오버헤드 익스텐션 (로프)", "15kg / 10~12회 / 3세트", 15.0, 12),
                ExerciseTemplate("수트케이스 캐리", "한 손 16~20kg / 30~40초 / 양쪽 3세트", 18.0, 1),
                ExerciseTemplate("크런치", "12회 / 2~3세트", 0.0, 12),
                ExerciseTemplate("레그레이즈", "10회 / 1~2세트 (허리 뜨면 중단)", 0.0, 10)
            )
        ),
        RoutineTemplate(
            "등 & 후면 & 코어",
            listOf(
                ExerciseTemplate("시티드 랫풀다운 (견갑 예열)", "20~25kg / 20회 / 2세트", 22.0, 20),
                ExerciseTemplate("스탠딩 페이스풀", "15kg / 12~15회 / 3세트", 15.0, 12),
                ExerciseTemplate("스미스 머신 바벨 로우", "25kg / 10~12회 / 4세트", 25.0, 12),
                ExerciseTemplate("랫 풀 다운", "45kg / 12회 / 4세트", 45.0, 12),
                ExerciseTemplate("암 풀 다운 (이지바)", "30~35kg / 10~12회 / 3세트", 32.0, 12),
                ExerciseTemplate("원암 덤벨 로우", "12~14kg / 10회 / 3세트", 13.0, 10),
                ExerciseTemplate("팔로프 프레스", "10~15kg / 10~12회 / 양쪽 3세트", 12.0, 12),
                ExerciseTemplate("크런치", "12회 / 3세트", 0.0, 12),
                ExerciseTemplate("레그레이즈", "10회 / 1세트 (허리 뜨면 중단)", 0.0, 10)
            )
        ),
        RoutineTemplate(
            "어깨 & 코어",
            listOf(
                ExerciseTemplate("시티드 프레스 머신 (견갑 예열)", "15~20kg / 20회 / 2세트", 17.0, 20),
                ExerciseTemplate("스탠딩 페이스풀", "15kg / 15회 / 2~3세트", 15.0, 15),
                ExerciseTemplate("시티드 덤벨 숄더 프레스 (80도)", "14kg / 12회 / 3세트", 14.0, 12),
                ExerciseTemplate("덤벨 사이드 레터럴 레이즈", "6kg / 15회 / 3~4세트", 6.0, 15),
                ExerciseTemplate("인클라인 체스트 서포트 덤벨 플라이", "7kg / 12~15회 / 3~4세트", 7.0, 12),
                ExerciseTemplate("캡틴체어 니레이즈", "12~15회 / 3세트", 0.0, 12),
                ExerciseTemplate("수트케이스 캐리", "한 손 16~20kg / 30~40초 / 양쪽 2~3세트", 18.0, 1)
            )
        ),
        RoutineTemplate(
            "하체 & 코어",
            listOf(
                ExerciseTemplate("시티드 레그 익스텐션 (예열)", "20kg / 20회 / 2세트", 20.0, 20),
                ExerciseTemplate("바벨 힙 쓰러스트", "20~40kg / 10~12회 / 3세트", 30.0, 12),
                ExerciseTemplate("레그 프레스", "50kg / 12회 / 4세트 (허리 뜨기 직전까지만)", 50.0, 12),
                ExerciseTemplate("레그 익스텐션", "50kg / 12~13회 / 4세트", 50.0, 12),
                ExerciseTemplate("라잉 레그 컬", "40~45kg / 12회 / 3~4세트", 42.0, 12),
                ExerciseTemplate("시티드 레그 컬", "가벼운 무게 / 12~15회 / 2~3세트", 0.0, 12),
                ExerciseTemplate("불가리안 스플릿 스쿼트", "덤벨 6~8kg / 8~10회 / 3세트", 7.0, 10),
                ExerciseTemplate("수트케이스 캐리", "한 손 16~20kg / 30~40초 / 양쪽 2~3세트", 18.0, 1)
            )
        ),
        RoutineTemplate("빈 루틴 (직접 추가)", emptyList())
    )
}
