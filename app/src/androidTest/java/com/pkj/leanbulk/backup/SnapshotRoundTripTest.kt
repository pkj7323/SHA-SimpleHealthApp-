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
import com.pkj.leanbulk.data.Macros
import com.pkj.leanbulk.data.MealEntry
import com.pkj.leanbulk.data.Repository
import com.pkj.leanbulk.data.Routine
import com.pkj.leanbulk.data.RoutineExercise
import com.pkj.leanbulk.data.WorkoutExercise
import com.pkj.leanbulk.data.WorkoutSet
import com.pkj.leanbulk.data.backup.BackupFiles
import com.pkj.leanbulk.data.backup.SnapshotCodec
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
        // 사용자가 숨긴 항목도 백업에 실려야 복원했을 때 숨김이 유지된다.
        db.exerciseDao().insert(
            Exercise(
                slug = "user-내가-만든-종목-1", name = "내가 만든 종목", bodyPart = "코어", equipment = "기타",
                trackingType = TrackingType.REPS, source = ItemSource.USER, hidden = true
            )
        )

        val mealDao = db.mealDao()
        mealDao.insertAll(
            listOf(
                // 매크로를 아예 모르는 끼니
                MealEntry(
                    date = "2026-08-30", orderIndex = 0, slotName = "모름",
                    menuText = "밖에서 사 먹은 국밥", macros = Macros(), eaten = true, memo = ""
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
        db.routineDao().insertExercises(
            listOf(
                RoutineExercise(
                    routineId = routineId, orderIndex = 0, exerciseName = "스쿼트",
                    defaultSetting = "5x5", defaultWeight = 80.0, defaultReps = 5
                ),
                RoutineExercise(
                    routineId = routineId, orderIndex = 7, exerciseName = "레그프레스",
                    defaultSetting = "", defaultWeight = 120.0, defaultReps = 12
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
                slug = "chicken-breast-100g", name = "닭가슴살 100g", category = "육류",
                macros = Macros(kcal = 165.0, protein = 31.0), usedCount = 42, source = ItemSource.SEED
            )
        )
        // 숨긴 시드 음식도 백업에 실려야 복원했을 때 다시 튀어나오지 않는다.
        db.foodDao().insert(
            FoodItem(
                slug = "ramen-1pack", name = "라면 1봉", category = "간편식",
                macros = Macros(500.0, 10.0, 79.0, 16.0, 4.0), usedCount = 0,
                source = ItemSource.SEED, hidden = true
            )
        )
        db.foodDao().insert(
            FoodItem(
                slug = "user-water", name = "물", macros = Macros(0.0, 0.0, 0.0, 0.0, 0.0), usedCount = 1
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
    }
}
