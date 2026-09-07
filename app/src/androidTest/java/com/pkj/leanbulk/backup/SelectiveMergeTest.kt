package com.pkj.leanbulk.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pkj.leanbulk.data.AppDatabase
import com.pkj.leanbulk.data.Macros
import com.pkj.leanbulk.data.FoodItem
import com.pkj.leanbulk.data.AmountUnit
import com.pkj.leanbulk.data.ItemSource
import com.pkj.leanbulk.data.Repository
import com.pkj.leanbulk.data.TrackingType
import com.pkj.leanbulk.data.backup.DietTemplateDto
import com.pkj.leanbulk.data.backup.DietTemplateMealDto
import com.pkj.leanbulk.data.backup.ExerciseDto
import com.pkj.leanbulk.data.backup.MacrosDto
import com.pkj.leanbulk.data.backup.RoutineDto
import com.pkj.leanbulk.data.backup.RoutineExerciseDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "남의 파일에서 이 루틴만 가져오기".
 *
 * 전체 덮어쓰기와 달리 **아무것도 지우면 안 되고**, 루틴이 쓰는 종목이 내게 없으면
 * 같이 들여와야 한다. 그러지 않으면 이름만 남고 추적 방식이 날아간다.
 */
@RunWith(AndroidJUnit4::class)
class SelectiveMergeTest {

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

    /** 남이 직접 만든 종목. 내 카탈로그엔 없다. */
    private val theirExercise = ExerciseDto(
        slug = "user-their-special-lift",
        name = "남이 만든 특이한 종목",
        bodyPart = "코어",
        equipment = "기타",
        trackingType = "TIME",
        source = "USER"
    )

    private val theirRoutine = RoutineDto(
        name = "남의 루틴",
        orderIndex = 0,
        exercises = listOf(
            RoutineExerciseDto(
                orderIndex = 0,
                exerciseSlug = theirExercise.slug,
                exerciseName = theirExercise.name,
                trackingType = "TIME",
                defaultSetting = "60초 3세트",
                defaultWeight = 0.0,
                defaultReps = 0
            ),
            // 내게도 있는 시드 종목
            RoutineExerciseDto(
                orderIndex = 1,
                exerciseSlug = "plank",
                exerciseName = "플랭크",
                trackingType = "TIME",
                defaultSetting = "",
                defaultWeight = 0.0,
                defaultReps = 0
            )
        )
    )

    @Test
    fun 고른_루틴이_들어오고_없는_종목이_같이_따라온다() = runBlocking {
        repo.applySeeds() // 내 카탈로그에 plank가 생긴다
        val beforeCatalog = repo.observeExercises().first().size

        val result = repo.mergeFromSnapshot(
            catalog = listOf(theirExercise),
            routines = listOf(theirRoutine),
            dietTemplates = emptyList(),
            exercises = emptyList()
        )

        assertEquals(1, result.routines)
        assertEquals("내게 없던 종목 하나만 들어와야 한다", 1, result.exercises)
        assertEquals(beforeCatalog + 1, repo.observeExercises().first().size)

        val merged = repo.observeRoutines().first().first { it.routine.name == "남의 루틴" }
        val theirs = merged.exercises.first { it.exercise.exerciseName == theirExercise.name }.exercise
        assertNotNull("딸려온 종목이 카탈로그에 연결돼야 한다", theirs.exerciseId)
        assertEquals("추적 방식이 날아갔다", TrackingType.TIME, theirs.trackingType)

        // 내게 이미 있던 종목은 새로 만들지 않고 내 것을 쓴다.
        val myPlankId = repo.observeExercises().first().first { it.slug == "plank" }.id
        assertEquals(
            myPlankId,
            merged.exercises.first { it.exercise.exerciseName == "플랭크" }.exercise.exerciseId
        )
    }

    /** 병합은 더하기지 갈아엎기가 아니다. */
    @Test
    fun 기존_루틴과_기록이_그대로_남는다() = runBlocking {
        repo.applySeeds()
        val beforeRoutines = repo.observeRoutines().first().size
        val beforeTemplates = db.dietTemplateDao().getAll().size

        repo.mergeFromSnapshot(listOf(theirExercise), listOf(theirRoutine), emptyList(), emptyList())

        assertEquals(beforeRoutines + 1, repo.observeRoutines().first().size)
        assertEquals("식단표는 건드리지 않았는데 바뀌었다", beforeTemplates, db.dietTemplateDao().getAll().size)
    }

    /**
     * 이름이 겹쳐도 덮어쓰지 않는다. 어느 쪽이 맞는지는 사용자만 아니까
     * 둘 다 남기고 고르게 한다.
     */
    @Test
    fun 이름이_겹치면_번호를_붙여서_둘_다_남긴다() = runBlocking {
        repo.applySeeds()
        val existingName = repo.observeRoutines().first().first().routine.name

        repo.mergeFromSnapshot(
            catalog = emptyList(),
            routines = listOf(RoutineDto(name = existingName, orderIndex = 0)),
            dietTemplates = emptyList(),
            exercises = emptyList()
        )

        val names = repo.observeRoutines().first().map { it.routine.name }
        assertTrue("원래 루틴이 사라졌다", existingName in names)
        assertTrue("가져온 루틴이 없다", "$existingName (2)" in names)
    }

    @Test
    fun 식단표만_골라_가져올_수_있다() = runBlocking {
        repo.applySeeds()
        val before = db.dietTemplateDao().getAll().size

        val result = repo.mergeFromSnapshot(
            catalog = emptyList(),
            routines = emptyList(),
            dietTemplates = listOf(
                DietTemplateDto(
                    name = "남의 식단표",
                    orderIndex = 0,
                    meals = listOf(
                        DietTemplateMealDto(0, "아침", "오트밀", MacrosDto(kcal = 350.0))
                    )
                )
            ),
            exercises = emptyList()
        )

        assertEquals(1, result.dietTemplates)
        assertEquals(0, result.routines)
        val templates = db.dietTemplateDao().getAll()
        assertEquals(before + 1, templates.size)
        assertEquals(1, templates.first { it.template.name == "남의 식단표" }.meals.size)
    }

    /**
     * 직접 등록한 음식만 뽑아 두었다가 다른 기기/사람이 담는 경로(ROADMAP 3번 "시드 승격").
     *
     * **이미 있는 slug는 건너뛴다** — 남의 파일이 내가 고쳐둔 영양정보를 덮어쓰면 안 된다.
     * 이름만 겹치는 건 뒤에 번호를 붙여 둘 다 남긴다(`food_item.name`이 유니크라 그냥 넣으면
     * 조용히 무시된다).
     */
    @Test
    fun 내_음식만_뽑아_다시_담을_수_있다() = runBlocking {
        repo.applySeeds()
        db.foodDao().insert(
            FoodItem(
                slug = "user-내가-만든-음식", name = "내가 만든 음식", category = "간편식",
                macros = Macros(kcal = 300.0), source = ItemSource.USER,
                baseAmount = 500.0, baseAmountUnit = AmountUnit.ML
            )
        )
        // 숨긴 것과 시드는 내보내지 않는다 — 다음 버전 시드에 넣을 후보가 아니다.
        db.foodDao().insert(
            FoodItem(
                slug = "user-치운-음식", name = "치운 음식", macros = Macros(kcal = 10.0),
                source = ItemSource.USER, hidden = true
            )
        )

        val exported = repo.exportUserFoods("2026-09-07T00:00:00")

        assertEquals("직접 등록한 것만 나가야 한다", 1, exported.foods.size)
        assertEquals("내가 만든 음식", exported.foods.single().name)
        assertEquals("ML", exported.foods.single().baseAmountUnit)

        // 받는 쪽: 아직 없는 음식이라 그대로 들어온다.
        val fresh = Repository(
            Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(), AppDatabase::class.java
            ).build()
        )
        val result = fresh.mergeFromSnapshot(
            catalog = emptyList(), routines = emptyList(), dietTemplates = emptyList(),
            exercises = emptyList(), foods = exported.foods
        )
        assertEquals(1, result.foods)

        // 같은 파일을 또 담아도 늘지 않는다(slug가 같으므로).
        assertEquals(0, fresh.mergeFromSnapshot(
            catalog = emptyList(), routines = emptyList(), dietTemplates = emptyList(),
            exercises = emptyList(), foods = exported.foods
        ).foods)
    }

    /** 루틴 없이 종목만 골라 가져오는 경우. 남이 만든 종목을 내 카탈로그에 담는다. */
    @Test
    fun 종목만_골라_가져오면_사용자_항목으로_들어온다() = runBlocking {
        repo.applySeeds()

        repo.mergeFromSnapshot(
            catalog = listOf(theirExercise),
            routines = emptyList(),
            dietTemplates = emptyList(),
            exercises = listOf(theirExercise)
        )

        val added = repo.observeExercises().first().first { it.slug == theirExercise.slug }
        assertEquals("남의 시드가 내 시드가 되면 안 된다", ItemSource.USER, added.source)
        assertEquals(TrackingType.TIME, added.trackingType)
    }
}
