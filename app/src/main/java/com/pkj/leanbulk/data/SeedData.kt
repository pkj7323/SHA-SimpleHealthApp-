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
     */
    const val VERSION = 2

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
