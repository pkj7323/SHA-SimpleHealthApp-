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

    /**
     * 내장 시드의 버전. **시드 내용을 추가하거나 고치면 올린다.**
     *
     * 이 번호가 DB에 기록된 것보다 크면 `Repository.applySeeds()`가 밀린 단계를 돌린다.
     * 올리지 않으면 새로 넣은 시드가 기존 사용자에게 영원히 안 들어간다.
     *
     * - 1: 기본 루틴 · 식단표 · 섭취 목표 (첫 설치에만)
     * - 2: 종목 카탈로그 50개 + 기존 루틴·기록 연결
     * - 3: 음식 카탈로그 60여 개
     */
    const val VERSION = 3

    /**
     * 종목 카탈로그 시드.
     *
     * **앞의 27개는 [routines]에 쓰인 종목명과 글자 하나까지 같아야 한다.** 시드 2단계가
     * 이름 완전일치로 기존 루틴·기록을 카탈로그에 연결하기 때문이다. 이름을 다듬고 싶으면
     * [routines] 쪽도 같이 고쳐야 한다.
     *
     * slug는 기기 간 정체성이라 **한번 정하면 바꾸지 않는다.** 표시명은 바꿔도 되지만
     * slug를 바꾸면 기존 기록의 연결이 끊긴다.
     */
    val exerciseCatalog = listOf(
        // ── 기존 루틴에 쓰이는 종목 (이름 고정) ──
        Seed("seated-shoulder-press-machine", "시티드 프레스 머신 (견갑 예열)", "어깨", "머신"),
        Seed("standing-face-pull", "스탠딩 페이스풀", "어깨", "케이블"),
        Seed("bench-press-machine", "벤치 프레스 머신 (다리 올림)", "가슴", "머신"),
        Seed("incline-dumbbell-press", "인클라인 덤벨 프레스 (벤치 30도)", "가슴", "덤벨"),
        Seed("pec-deck-fly-machine", "90도 플라이 머신", "가슴", "머신"),
        Seed("cable-pushdown-ez-bar", "케이블 푸시다운 (이지바)", "팔", "케이블"),
        Seed("cable-overhead-extension-rope", "케이블 오버헤드 익스텐션 (로프)", "팔", "케이블"),
        Seed("suitcase-carry", "수트케이스 캐리", "코어", "덤벨", TrackingType.WEIGHT_TIME),
        Seed("crunch", "크런치", "코어", "맨몸", TrackingType.REPS),
        Seed("leg-raise", "레그레이즈", "코어", "맨몸", TrackingType.REPS),
        Seed("seated-lat-pulldown-warmup", "시티드 랫풀다운 (견갑 예열)", "등", "머신"),
        Seed("smith-machine-barbell-row", "스미스 머신 바벨 로우", "등", "머신"),
        Seed("lat-pulldown", "랫 풀 다운", "등", "머신"),
        Seed("arm-pulldown-ez-bar", "암 풀 다운 (이지바)", "등", "케이블"),
        Seed("one-arm-dumbbell-row", "원암 덤벨 로우", "등", "덤벨"),
        Seed("pallof-press", "팔로프 프레스", "코어", "케이블"),
        Seed("seated-dumbbell-shoulder-press", "시티드 덤벨 숄더 프레스 (80도)", "어깨", "덤벨"),
        Seed("dumbbell-lateral-raise", "덤벨 사이드 레터럴 레이즈", "어깨", "덤벨"),
        Seed("incline-chest-supported-dumbbell-fly", "인클라인 체스트 서포트 덤벨 플라이", "등", "덤벨"),
        Seed("captains-chair-knee-raise", "캡틴체어 니레이즈", "코어", "맨몸", TrackingType.REPS),
        Seed("seated-leg-extension-warmup", "시티드 레그 익스텐션 (예열)", "하체", "머신"),
        Seed("barbell-hip-thrust", "바벨 힙 쓰러스트", "하체", "바벨"),
        Seed("leg-press", "레그 프레스", "하체", "머신"),
        Seed("leg-extension", "레그 익스텐션", "하체", "머신"),
        Seed("lying-leg-curl", "라잉 레그 컬", "하체", "머신"),
        Seed("seated-leg-curl", "시티드 레그 컬", "하체", "머신"),
        Seed("bulgarian-split-squat", "불가리안 스플릿 스쿼트", "하체", "덤벨"),

        // ── 흔히 쓰는 종목 추가 ──
        Seed("barbell-bench-press", "바벨 벤치프레스", "가슴", "바벨"),
        Seed("incline-barbell-bench-press", "인클라인 바벨 벤치프레스", "가슴", "바벨"),
        Seed("dips", "딥스", "가슴", "맨몸", TrackingType.REPS),
        Seed("push-up", "푸시업", "가슴", "맨몸", TrackingType.REPS),
        Seed("cable-crossover", "케이블 크로스오버", "가슴", "케이블"),
        Seed("deadlift", "데드리프트", "등", "바벨"),
        Seed("barbell-row", "바벨 로우", "등", "바벨"),
        Seed("pull-up", "풀업", "등", "맨몸", TrackingType.REPS),
        Seed("seated-cable-row", "시티드 케이블 로우", "등", "케이블"),
        Seed("barbell-overhead-press", "바벨 오버헤드 프레스", "어깨", "바벨"),
        Seed("arnold-press", "아놀드 프레스", "어깨", "덤벨"),
        Seed("barbell-back-squat", "바벨 백스쿼트", "하체", "바벨"),
        Seed("romanian-deadlift", "루마니안 데드리프트", "하체", "바벨"),
        Seed("walking-lunge", "워킹 런지", "하체", "덤벨"),
        Seed("standing-calf-raise", "스탠딩 카프 레이즈", "하체", "머신"),
        Seed("barbell-curl", "바벨 컬", "팔", "바벨"),
        Seed("dumbbell-curl", "덤벨 컬", "팔", "덤벨"),
        Seed("hammer-curl", "해머 컬", "팔", "덤벨"),
        Seed("lying-triceps-extension", "라잉 트라이셉스 익스텐션", "팔", "바벨"),
        Seed("plank", "플랭크", "코어", "맨몸", TrackingType.TIME),
        Seed("side-plank", "사이드 플랭크", "코어", "맨몸", TrackingType.TIME),
        Seed("treadmill-run", "트레드밀 러닝", "유산소", "머신", TrackingType.DISTANCE_TIME),
        Seed("stationary-bike", "사이클", "유산소", "머신", TrackingType.DISTANCE_TIME)
    )

    /**
     * 음식 카탈로그 시드.
     *
     * 영양정보는 **일반적인 참고값**이다. 제품·조리법에 따라 달라지므로, 정확한 값을
     * 아는 음식은 사용자가 고른 뒤 직접 고치면 된다(고친 값이 다음부터 쌓인다).
     *
     * 이름에 제공량을 함께 적는다("닭가슴살 100g"). 이 앱의 한 끼니는 음식 하나가 아니라
     * 자유 텍스트라서 수량을 따로 두지 않는다 — `FoodItem` 주석 참고.
     */
    val foodCatalog = listOf(
        // ── 밥 · 곡물 ──
        FoodSeed("rice-brown-200g", "현미밥 200g", "밥·곡물", 300.0, 6.0, 66.0, 2.5, 0.0),
        FoodSeed("rice-white-200g", "흰쌀밥 200g", "밥·곡물", 310.0, 5.6, 69.0, 0.6, 0.0),
        FoodSeed("rice-multigrain-200g", "잡곡밥 200g", "밥·곡물", 300.0, 7.0, 64.0, 2.0, 0.0),
        FoodSeed("sweet-potato-100g", "고구마 100g", "밥·곡물", 130.0, 1.5, 31.0, 0.2, 9.0),
        FoodSeed("potato-100g", "감자 100g", "밥·곡물", 77.0, 2.0, 17.0, 0.1, 0.8),
        FoodSeed("oatmeal-40g", "오트밀 40g(건조)", "밥·곡물", 150.0, 5.0, 27.0, 3.0, 0.4),
        FoodSeed("white-bread-1slice", "식빵 1장", "밥·곡물", 90.0, 3.0, 17.0, 1.0, 1.5),
        FoodSeed("whole-wheat-bread-1slice", "통밀빵 1장", "밥·곡물", 85.0, 4.0, 15.0, 1.2, 1.5),

        // ── 육류 ──
        FoodSeed("chicken-breast-100g", "닭가슴살 100g", "육류", 165.0, 31.0, 0.0, 3.6, 0.0),
        FoodSeed("chicken-tender-100g", "닭안심 100g", "육류", 110.0, 24.0, 0.0, 1.5, 0.0),
        FoodSeed("chicken-thigh-100g", "닭다리살 100g", "육류", 210.0, 26.0, 0.0, 11.0, 0.0),
        FoodSeed("beef-tenderloin-100g", "소고기 안심 100g", "육류", 220.0, 21.0, 0.0, 15.0, 0.0),
        FoodSeed("beef-shank-100g", "소고기 사태 100g", "육류", 140.0, 21.0, 0.0, 6.0, 0.0),
        FoodSeed("pork-tenderloin-100g", "돼지고기 안심 100g", "육류", 145.0, 22.0, 0.0, 6.0, 0.0),
        FoodSeed("pork-neck-100g", "돼지고기 목살 100g", "육류", 240.0, 18.0, 0.0, 19.0, 0.0),
        FoodSeed("pork-belly-100g", "삼겹살 100g", "육류", 330.0, 17.0, 0.0, 29.0, 0.0),

        // ── 생선 · 해산물 ──
        FoodSeed("salmon-100g", "연어 100g", "생선·해산물", 200.0, 20.0, 0.0, 13.0, 0.0),
        FoodSeed("canned-tuna-100g", "참치캔(기름 뺀) 100g", "생선·해산물", 110.0, 25.0, 0.0, 1.0, 0.0),
        FoodSeed("mackerel-100g", "고등어 100g", "생선·해산물", 190.0, 20.0, 0.0, 12.0, 0.0),
        FoodSeed("cod-100g", "흰살생선(대구) 100g", "생선·해산물", 82.0, 18.0, 0.0, 0.7, 0.0),
        FoodSeed("shrimp-100g", "새우 100g", "생선·해산물", 99.0, 24.0, 0.0, 0.3, 0.0),
        FoodSeed("squid-100g", "오징어 100g", "생선·해산물", 92.0, 16.0, 3.0, 1.4, 0.0),

        // ── 계란 · 유제품 ──
        FoodSeed("egg-1", "계란 1개", "계란·유제품", 72.0, 6.3, 0.4, 4.8, 0.2),
        FoodSeed("egg-white-1", "계란흰자 1개", "계란·유제품", 17.0, 3.6, 0.2, 0.1, 0.2),
        FoodSeed("milk-200ml", "우유 200ml", "계란·유제품", 125.0, 6.6, 9.6, 6.8, 9.6),
        FoodSeed("low-fat-milk-200ml", "저지방우유 200ml", "계란·유제품", 90.0, 6.8, 10.0, 2.0, 10.0),
        FoodSeed("greek-yogurt-100g", "그릭요거트 100g", "계란·유제품", 97.0, 9.0, 4.0, 5.0, 4.0),
        FoodSeed("plain-yogurt-100g", "플레인요거트 100g", "계란·유제품", 61.0, 3.5, 4.7, 3.3, 4.7),
        FoodSeed("cheddar-1slice", "체다치즈 1장", "계란·유제품", 65.0, 4.0, 1.0, 5.0, 0.5),
        FoodSeed("cottage-cheese-100g", "코티지치즈 100g", "계란·유제품", 98.0, 11.0, 3.4, 4.3, 2.7),

        // ── 두부 · 콩 ──
        FoodSeed("tofu-100g", "두부 100g", "두부·콩", 84.0, 8.0, 2.0, 5.0, 0.6),
        FoodSeed("soft-tofu-100g", "순두부 100g", "두부·콩", 47.0, 5.0, 2.0, 2.5, 0.5),
        FoodSeed("natto-1pack", "낫토 1팩", "두부·콩", 100.0, 8.0, 6.0, 5.0, 1.0),
        FoodSeed("chickpea-100g", "병아리콩 100g(삶은)", "두부·콩", 164.0, 9.0, 27.0, 2.6, 4.8),
        FoodSeed("black-bean-100g", "검은콩 100g(삶은)", "두부·콩", 132.0, 9.0, 24.0, 0.5, 0.3),

        // ── 채소 ──
        FoodSeed("broccoli-100g", "브로콜리 100g", "채소", 34.0, 2.8, 7.0, 0.4, 1.7),
        FoodSeed("spinach-100g", "시금치 100g", "채소", 23.0, 2.9, 3.6, 0.4, 0.4),
        FoodSeed("cabbage-100g", "양배추 100g", "채소", 25.0, 1.3, 6.0, 0.1, 3.2),
        FoodSeed("tomato-1", "토마토 1개", "채소", 27.0, 1.3, 5.8, 0.3, 4.0),
        FoodSeed("cherry-tomato-100g", "방울토마토 100g", "채소", 18.0, 0.9, 3.9, 0.2, 2.6),
        FoodSeed("cucumber-100g", "오이 100g", "채소", 15.0, 0.7, 3.6, 0.1, 1.7),
        FoodSeed("paprika-100g", "파프리카 100g", "채소", 31.0, 1.0, 6.0, 0.3, 4.2),
        FoodSeed("mushroom-100g", "양송이버섯 100g", "채소", 22.0, 3.1, 3.3, 0.3, 2.0),
        FoodSeed("kimchi-100g", "김치 100g", "채소", 32.0, 1.7, 6.0, 0.5, 1.8),

        // ── 과일 ──
        FoodSeed("banana-1", "바나나 1개", "과일", 105.0, 1.3, 27.0, 0.4, 14.0),
        FoodSeed("apple-1", "사과 1개", "과일", 104.0, 0.5, 28.0, 0.3, 21.0),
        FoodSeed("blueberry-100g", "블루베리 100g", "과일", 57.0, 0.7, 14.0, 0.3, 10.0),
        FoodSeed("strawberry-100g", "딸기 100g", "과일", 32.0, 0.7, 7.7, 0.3, 4.9),
        FoodSeed("orange-1", "오렌지 1개", "과일", 70.0, 1.3, 17.0, 0.2, 13.0),

        // ── 견과 ──
        FoodSeed("almond-15g", "아몬드 15g", "견과", 87.0, 3.2, 3.3, 7.5, 0.6),
        FoodSeed("walnut-15g", "호두 15g", "견과", 98.0, 2.3, 2.0, 9.8, 0.4),
        FoodSeed("peanut-butter-15g", "땅콩버터 15g", "견과", 94.0, 4.0, 3.2, 8.0, 1.4),

        // ── 간편식 ──
        FoodSeed("chicken-sausage-100g", "닭가슴살 소시지 100g", "간편식", 110.0, 17.0, 6.0, 2.0, 1.0),
        FoodSeed("gimbap-1roll", "김밥 1줄", "간편식", 400.0, 10.0, 68.0, 9.0, 3.0),
        FoodSeed("tuna-gimbap-1roll", "참치김밥 1줄", "간편식", 480.0, 18.0, 70.0, 13.0, 4.0),
        FoodSeed("ramen-1pack", "라면 1봉", "간편식", 500.0, 10.0, 79.0, 16.0, 4.0),
        FoodSeed("boiled-egg-1", "삶은계란 1개", "간편식", 72.0, 6.3, 0.4, 4.8, 0.2),

        // ── 음료 ──
        FoodSeed("protein-shake-1scoop", "프로틴쉐이크 1스쿱", "음료", 120.0, 24.0, 3.0, 1.5, 1.0),
        FoodSeed("americano", "아메리카노(무가당)", "음료", 5.0, 0.3, 1.0, 0.0, 0.0),
        FoodSeed("black-coffee", "블랙커피", "음료", 2.0, 0.1, 0.0, 0.0, 0.0),
        FoodSeed("sports-drink-500ml", "이온음료 500ml", "음료", 125.0, 0.0, 31.0, 0.0, 31.0)
    )

    /** 음식 카탈로그 시드 한 줄. */
    data class FoodSeed(
        val slug: String,
        val name: String,
        val category: String,
        val kcal: Double,
        val protein: Double,
        val carbs: Double,
        val fat: Double,
        val sugar: Double
    ) {
        fun toEntity() = FoodItem(
            slug = slug,
            name = name,
            category = category,
            macros = Macros(kcal, protein, carbs, fat, sugar),
            usedCount = 0,
            source = ItemSource.SEED
        )
    }

    /** 카탈로그 시드 한 줄. 대부분이 무게×횟수라 그것만 기본값으로 둔다. */
    data class Seed(
        val slug: String,
        val name: String,
        val bodyPart: String,
        val equipment: String,
        val trackingType: TrackingType = TrackingType.WEIGHT_REPS
    ) {
        fun toEntity() = Exercise(
            slug = slug,
            name = name,
            bodyPart = bodyPart,
            equipment = equipment,
            trackingType = trackingType,
            source = ItemSource.SEED
        )
    }

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
