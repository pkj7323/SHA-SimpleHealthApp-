package com.pkj.leanbulk.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pkj.leanbulk.data.AppDatabase
import com.pkj.leanbulk.data.BodyRecord
import com.pkj.leanbulk.data.DietTarget
import com.pkj.leanbulk.data.DietTemplate
import com.pkj.leanbulk.data.DietTemplateMeal
import com.pkj.leanbulk.data.Exercise
import com.pkj.leanbulk.data.ItemSource
import com.pkj.leanbulk.data.TrackingType
import com.pkj.leanbulk.data.FoodItem
import com.pkj.leanbulk.data.MacroSource
import com.pkj.leanbulk.data.Macros
import com.pkj.leanbulk.data.MealEntry
import com.pkj.leanbulk.data.MealFood
import com.pkj.leanbulk.data.MealPresetFood
import com.pkj.leanbulk.data.Repository
import com.pkj.leanbulk.data.Routine
import com.pkj.leanbulk.data.RoutineExercise
import com.pkj.leanbulk.data.RoutineSet
import com.pkj.leanbulk.data.WorkoutExercise
import com.pkj.leanbulk.data.WorkoutSet
import com.pkj.leanbulk.data.backup.BackupFiles
import com.pkj.leanbulk.data.backup.SnapshotCodec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream

/**
 * 스냅샷 왕복이 무손실인지 검증한다.
 *
 * 이 테스트가 이 기능의 핵심 안전장치다. 직렬화가 필드를 하나라도 빠뜨리면
 * 동기화할 때마다 조용히 데이터가 사라지는데, 사용자는 한참 뒤에야 알아챈다.
 */
@RunWith(AndroidJUnit4::class)
class SnapshotRoundTripTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: Repository

    /** 왕복 비교에서 시각 때문에 실패하지 않도록 고정값을 쓴다. */
    private val fixedTime = "2026-09-02T10:00:00"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repo = Repository(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun 전체_왕복_후_스냅샷이_완전히_같다() = runBlocking {
        seedTrickyData()

        val before = SnapshotCodec.toJson(repo.exportSnapshot(fixedTime))
        repo.importSnapshot(SnapshotCodec.fromJson(before))
        val after = SnapshotCodec.toJson(repo.exportSnapshot(fixedTime))

        assertEquals(before, after)
    }

    /**
     * 이 앱에서 `null`(모름)과 `0.0`(정말 0g)은 다른 뜻이다.
     * 왕복 중에 null이 0으로 뭉개지면 사용자가 입력하지 않은 값이 "0을 먹었다"로 바뀐다.
     *
     * **위의 왕복 비교와 겹쳐 보이지만 지우면 안 된다.** 왕복 비교는 내보내기 양쪽에 똑같이
     * 적용되는 변형을 못 잡는다 — 실제로 `Macros.toDto()`가 null을 0으로 바꾸게 만들어 보면
     * 왕복 비교는 그대로 통과하고(A도 B도 똑같이 뭉개지므로) 이 테스트만 실패한다.
     */
    @Test
    fun null_매크로와_0_매크로가_섞여도_구분이_유지된다() = runBlocking {
        seedTrickyData()

        repo.importSnapshot(SnapshotCodec.fromJson(SnapshotCodec.toJson(repo.exportSnapshot(fixedTime))))

        val meals = repo.exportSnapshot(fixedTime).meals.associateBy { it.slotName }

        val unknown = requireNotNull(meals["모름"]).macros
        assertNull("입력하지 않은 칼로리는 null로 남아야 한다", unknown.kcal)
        assertNull(unknown.protein)

        val genuineZero = requireNotNull(meals["진짜0"]).macros
        assertEquals("0으로 입력한 값은 0으로 남아야 한다", 0.0, genuineZero.kcal!!, 0.0)
        assertEquals(0.0, genuineZero.protein!!, 0.0)

        val partial = requireNotNull(meals["일부만"]).macros
        assertEquals(520.0, partial.kcal!!, 0.0)
        assertNull("입력하지 않은 단백질은 null로 남아야 한다", partial.protein)
    }

    /**
     * 매크로의 **출처**도 왕복에서 살아남아야 한다.
     *
     * 어림값이 `ENTERED`로 새어 나가면 다음에 그 숫자를 읽는 쪽(AI 챗)이 추정치를 사실로
     * 세탁한다. 값이 맞는지만 보는 왕복 비교로는 안 잡히므로 따로 확인한다.
     */
    @Test
    fun 매크로_출처가_왕복에서_유지된다() = runBlocking {
        seedTrickyData()

        repo.importSnapshot(SnapshotCodec.fromJson(SnapshotCodec.toJson(repo.exportSnapshot(fixedTime))))

        val meals = repo.exportSnapshot(fixedTime).meals.associateBy { it.slotName }
        assertEquals(
            "어림값 확정이 직접 입력으로 둔갑했다",
            MacroSource.ESTIMATED.name,
            requireNotNull(meals["어림"]).macroSource
        )
        assertEquals(MacroSource.NONE.name, requireNotNull(meals["모름"]).macroSource)
        assertEquals(MacroSource.ENTERED.name, requireNotNull(meals["진짜0"]).macroSource)
    }

    /**
     * 음식 줄(`meal_food`)의 카탈로그 참조는 로컬 id가 아니라 slug로 실린다(다른 참조들과
     * 같은 이유 — auto id는 기기마다 다르다). 왕복한 뒤에도 슬러그로 올바른 음식에 다시
     * 붙는지, 카탈로그에 없는 줄(`foodId = null`)이 엉뚱한 음식에 붙지 않는지 확인한다.
     */
    @Test
    fun 음식_줄이_카탈로그에_slug로_다시_이어붙는다() = runBlocking {
        seedTrickyData()

        repo.importSnapshot(SnapshotCodec.fromJson(SnapshotCodec.toJson(repo.exportSnapshot(fixedTime))))

        val meal = repo.observeMealsWithFoods("2026-09-01").first().single()
        assertEquals("음식 줄 수가 달라졌다", 2, meal.foods.size)

        val chickenLine = meal.foods.first { it.name == "닭가슴살" }
        val chickenId = requireNotNull(db.foodDao().findBySlug("chicken-breast-100g")).id
        assertEquals("카탈로그 연결이 slug로 다시 안 이어졌다", chickenId, chickenLine.foodId)
        assertEquals(150.0, chickenLine.amount!!, 0.001)

        val aiLine = meal.foods.first { it.name == "그릭요거트 200g" }
        assertNull("카탈로그에 없던 줄이 엉뚱한 음식에 붙었다", aiLine.foodId)
        assertEquals(MacroSource.AI_SUGGESTED, aiLine.macroSource)
    }

    @Test
    fun gzip_왕복도_같은_결과를_준다() = runBlocking {
        seedTrickyData()

        val snapshot = repo.exportSnapshot(fixedTime)
        val restored = SnapshotCodec.gunzip(ByteArrayInputStream(SnapshotCodec.gzip(snapshot)))

        assertEquals(SnapshotCodec.toJson(snapshot), SnapshotCodec.toJson(restored))
    }

    /**
     * 실제 파일로 굽고 되읽는 경로까지 확인한다. 앱에서 백업 버튼이 하는 일과 같다.
     *
     * `runBlocking<Unit>`으로 반환형을 못 박아야 한다 — 마지막 줄이 `delete()`라 그냥 두면
     * 반환형이 Boolean으로 추론되고, JUnit이 "테스트는 void여야 한다"며 **클래스 전체**를
     * 로드하지 못해 이 파일의 테스트가 전부 안 돌게 된다.
     */
    @Test
    fun 파일로_굽고_되읽어도_같다() = runBlocking<Unit> {
        seedTrickyData()
        val context = ApplicationProvider.getApplicationContext<Context>()

        val snapshot = repo.exportSnapshot(fixedTime)
        val file = BackupFiles.writeForSharing(context, snapshot)
        val restored = file.inputStream().use { SnapshotCodec.gunzip(it) }

        assertEquals(SnapshotCodec.toJson(snapshot), SnapshotCodec.toJson(restored))
        file.delete()
    }

    @Test
    fun 빈_DB도_왕복된다() = runBlocking {
        val before = SnapshotCodec.toJson(repo.exportSnapshot(fixedTime))
        repo.importSnapshot(SnapshotCodec.fromJson(before))
        assertEquals(before, SnapshotCodec.toJson(repo.exportSnapshot(fixedTime)))
    }

    /** 왕복에서 깨지기 쉬운 값들을 골고루 심는다. */
    private suspend fun seedTrickyData() {
        // 카탈로그를 먼저 넣어야 기록이 slug로 이어 붙는다.
        val plankId = db.exerciseDao().insert(
            Exercise(
                slug = "plank", name = "플랭크", bodyPart = "코어", equipment = "맨몸",
                trackingType = TrackingType.TIME, source = ItemSource.SEED
            )
        )
        val runId = db.exerciseDao().insert(
            Exercise(
                slug = "treadmill-run", name = "트레드밀 러닝", bodyPart = "유산소", equipment = "머신",
                trackingType = TrackingType.DISTANCE_TIME, source = ItemSource.SEED
            )
        )
        // 사용자가 직접 만든 종목도 백업에 실려야 한다 — 시드로는 복구되지 않기 때문이다.
        db.exerciseDao().insert(
            Exercise(
                slug = "user-내가-만든-종목-1", name = "내가 만든 종목", bodyPart = "코어", equipment = "기타",
                trackingType = TrackingType.REPS, source = ItemSource.USER
            )
        )

        val mealDao = db.mealDao()
        mealDao.insertAll(
            listOf(
                // 매크로를 아예 모르는 끼니
                MealEntry(
                    date = "2026-08-30", orderIndex = 0, slotName = "모름",
                    menuText = "밖에서 사 먹은 국밥", macros = Macros(), eaten = true, memo = "",
                    macroSource = MacroSource.NONE
                ),
                // 어림값을 확정한 끼니 — 직접 잰 값과 섞이면 안 되므로 출처가 살아남아야 한다
                MealEntry(
                    date = "2026-08-30", orderIndex = 2, slotName = "어림",
                    menuText = "김밥 한 줄", macros = Macros(kcal = 480.0, protein = 12.0),
                    eaten = true, memo = "", macroSource = MacroSource.ESTIMATED
                ),
                // 진짜로 0인 끼니 (물만 마심 등)
                MealEntry(
                    date = "2026-08-30", orderIndex = 1, slotName = "진짜0",
                    menuText = "블랙커피", macros = Macros(0.0, 0.0, 0.0, 0.0, 0.0),
                    eaten = true, memo = "메모 있음"
                ),
                // 칼로리만 아는 끼니
                MealEntry(
                    date = "2026-08-31", orderIndex = 0, slotName = "일부만",
                    menuText = "닭가슴살 샐러드 🥗", macros = Macros(kcal = 520.0),
                    eaten = false, memo = ""
                )
            )
        )

        db.workoutDao().replaceDay(
            "2026-08-30",
            listOf(
                WorkoutExercise(
                    date = "2026-08-30", routineName = "가슴/삼두", orderIndex = 0,
                    exerciseName = "벤치프레스", defaultSetting = "5x5", memo = "허리 조심"
                ) to listOf(
                    WorkoutSet(exerciseId = 0, setIndex = 0, weightKg = 60.0, reps = 10, done = true),
                    WorkoutSet(exerciseId = 0, setIndex = 1, weightKg = 62.5, reps = 8, done = false)
                ),
                // 시간형 — 무게·횟수가 아니라 durationSec으로 기록된다
                WorkoutExercise(
                    date = "2026-08-30", routineName = "가슴/삼두", orderIndex = 1,
                    exerciseId = plankId, exerciseName = "플랭크",
                    trackingType = TrackingType.TIME, defaultSetting = "", memo = ""
                ) to listOf(
                    WorkoutSet(exerciseId = 0, setIndex = 0, weightKg = 0.0, reps = 0, done = true, durationSec = 90)
                ),
                // 유산소 — 거리 + 시간
                WorkoutExercise(
                    date = "2026-08-30", routineName = "가슴/삼두", orderIndex = 2,
                    exerciseId = runId, exerciseName = "트레드밀 러닝",
                    trackingType = TrackingType.DISTANCE_TIME, defaultSetting = "", memo = ""
                ) to listOf(
                    WorkoutSet(
                        exerciseId = 0, setIndex = 0, weightKg = 0.0, reps = 0, done = true,
                        durationSec = 1_800, distanceM = 5_000.0
                    )
                )
            )
        )

        // orderIndex에 일부러 구멍을 낸다. 불러올 때 0,1,2로 다시 매겨지면 이 테스트가 잡아낸다.
        val routineId = db.routineDao().insertRoutine(Routine(name = "4분할 A", orderIndex = 3))
        db.routineDao().insertExercisesWithSets(
            routineId,
            listOf(
                // 세트마다 무게가 다른 구성. 예전엔 기본값 한 쌍만 담겨서 이런 게 뭉개졌다.
                RoutineExercise(
                    routineId = routineId, orderIndex = 0, exerciseName = "스쿼트",
                    defaultSetting = "5x5"
                ) to listOf(
                    RoutineSet(routineExerciseId = 0, setIndex = 0, weightKg = 60.0, reps = 8),
                    RoutineSet(routineExerciseId = 0, setIndex = 1, weightKg = 80.0, reps = 5),
                    RoutineSet(routineExerciseId = 0, setIndex = 2, weightKg = 80.0, reps = 5)
                ),
                // 세트 수가 기본값과 다른 구성.
                RoutineExercise(
                    routineId = routineId, orderIndex = 7, exerciseName = "레그프레스",
                    defaultSetting = ""
                ) to listOf(
                    RoutineSet(routineExerciseId = 0, setIndex = 0, weightKg = 120.0, reps = 12)
                )
            )
        )

        val templateId = db.dietTemplateDao().insertTemplate(DietTemplate(name = "증량 식단", orderIndex = 2))
        db.dietTemplateDao().insertMeals(
            listOf(
                DietTemplateMeal(
                    templateId = templateId, orderIndex = 0, slotName = "아침",
                    menuText = "오트밀", macros = Macros(kcal = 350.0, protein = 12.0)
                )
            )
        )

        db.foodDao().insert(
            FoodItem(
                slug = "chicken-breast-100g", name = "닭가슴살", category = "육류",
                macros = Macros(kcal = 165.0, protein = 31.0), usedCount = 42, source = ItemSource.SEED,
                baseAmount = 100.0
            )
        )
        // 이름이 같은 음식을 둘 둔다 — 이름은 유니크가 아니므로 왕복에서도 둘 다 살아야 한다.
        db.foodDao().insert(
            FoodItem(
                slug = "ramen-1pack", name = "라면", category = "간편식",
                macros = Macros(500.0, 10.0, 79.0, 16.0, 4.0), usedCount = 0,
                source = ItemSource.SEED
            )
        )
        db.foodDao().insert(
            FoodItem(
                slug = "user-ramen-mine", name = "라면", category = "간편식",
                macros = Macros(420.0, 9.0, 65.0, 13.0, 3.0), usedCount = 2,
                source = ItemSource.USER
            )
        )
        db.foodDao().insert(
            FoodItem(
                slug = "user-water", name = "물", macros = Macros(0.0, 0.0, 0.0, 0.0, 0.0), usedCount = 1,
                favorite = true
            )
        )

        // 음식 줄이 있는 새 방식 끼니(DIET-REDESIGN.md). 카탈로그에서 온 줄 하나 +
        // AI가 막 제안해서 아직 카탈로그에 없는 줄 하나(foodId=null)를 섞는다.
        val chickenId = requireNotNull(db.foodDao().findBySlug("chicken-breast-100g")).id
        repo.saveMealDay(
            "2026-09-01",
            listOf(
                MealEntry(
                    date = "2026-09-01", orderIndex = 0, slotName = "점심",
                    menuText = "", macros = Macros(), eaten = true
                ) to listOf(
                    MealFood(
                        mealEntryId = 0, orderIndex = 0, foodId = chickenId, name = "닭가슴살",
                        multiplier = 1.5, amount = 150.0,
                        macros = Macros(kcal = 247.5, protein = 46.5, carbs = 0.0, fat = 5.4, sugar = 0.0),
                        macroSource = MacroSource.CATALOG
                    ),
                    MealFood(
                        mealEntryId = 0, orderIndex = 1, foodId = null, name = "그릭요거트 200g",
                        multiplier = 2.0, amount = null,
                        macros = Macros(kcal = 194.0, protein = 18.0, carbs = 8.0, fat = 10.0, sugar = 8.0),
                        macroSource = MacroSource.AI_SUGGESTED
                    )
                )
            )
        )

        db.bodyRecordDao().upsert(
            BodyRecord(
                date = "2026-08-28", weightKg = 74.3, muscleMassKg = 35.1,
                fatMassKg = 11.2, fatPercent = 15.07, waistCm = null, tool = "인바디", memo = ""
            )
        )
        db.bodyRecordDao().upsert(
            BodyRecord(
                date = "2026-08-14", weightKg = 75.0, muscleMassKg = null,
                fatMassKg = null, fatPercent = null, waistCm = 82.0, tool = "", memo = "집 체중계"
            )
        )

        db.dietTargetDao().upsert(
            DietTarget(kcal = 2600.0, protein = 165.0, carbs = 300.0, fat = 70.0, sugar = 50.0)
        )

        // "내 식사" — 카탈로그에서 온 줄 + 아직 카탈로그에 없는 줄을 섞어 slug 이어붙임까지 확인한다.
        db.mealPresetDao().insertWithFoods(
            "닭가슴살 세트",
            listOf(
                MealPresetFood(
                    presetId = 0, orderIndex = 0, foodId = chickenId, name = "닭가슴살",
                    multiplier = 1.5, amount = 150.0,
                    macros = Macros(kcal = 247.5, protein = 46.5, carbs = 0.0, fat = 5.4, sugar = 0.0),
                    macroSource = MacroSource.CATALOG
                ),
                MealPresetFood(
                    presetId = 0, orderIndex = 1, foodId = null, name = "그릭요거트 200g",
                    multiplier = 2.0, amount = null,
                    macros = Macros(kcal = 194.0, protein = 18.0, carbs = 8.0, fat = 10.0, sugar = 8.0),
                    macroSource = MacroSource.AI_SUGGESTED
                )
            )
        )
    }
}
