package com.pkj.leanbulk.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 루틴 덮어쓰기와 이름 중복 검사.
 *
 * 덮어쓰기는 **되돌릴 수 없다** — 옛 종목 구성이 지워진다. 지우고 다시 넣는 두 단계라
 * 중간에 잘못되면 루틴이 텅 빈 채로 남을 수 있어서, 결과가 정확히 새 구성인지 확인한다.
 */
@RunWith(AndroidJUnit4::class)
class RoutineEditTest {

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

    /** 종목마다 세트 두 개(가벼운 첫 세트 + 본 세트). 무게는 종목 순서를 알아볼 수 있게 다르게. */
    private fun exercises(vararg names: String) = names.mapIndexed { index, name ->
        RoutineExercise(
            routineId = 0,
            orderIndex = index,
            exerciseName = name,
            defaultSetting = ""
        ) to listOf(
            RoutineSet(routineExerciseId = 0, setIndex = 0, weightKg = 20.0 + index, reps = 10),
            RoutineSet(routineExerciseId = 0, setIndex = 1, weightKg = 40.0 + index, reps = 8)
        )
    }

    @Test
    fun 덮어쓰면_옛_종목은_남지_않는다() = runBlocking<Unit> {
        repo.saveAsNewRoutine("하체", exercises("스쿼트", "레그 프레스", "레그 컬"))
        val routine = repo.observeRoutines().first().first { it.routine.name == "하체" }

        repo.overwriteRoutine(routine.routine.id, exercises("데드리프트", "힙 쓰러스트"))

        val after = repo.observeRoutines().first().first { it.routine.name == "하체" }
        assertEquals(
            listOf("데드리프트", "힙 쓰러스트"),
            after.exercises.sortedBy { it.exercise.orderIndex }.map { it.exercise.exerciseName }
        )
        // 이름이 겹치지 않는 옛 종목이 하나라도 남으면 삭제가 덜 된 것이다.
        assertEquals("루틴이 하나뿐이어야 한다", 1, repo.observeRoutines().first().size)
        // 지운 종목의 세트도 CASCADE로 같이 사라져야 한다. 남으면 고아 행이 쌓인다.
        assertEquals("세트가 종목 수 × 2개여야 한다", 4, after.exercises.sumOf { it.sets.size })
    }

    /**
     * 세트를 있는 그대로 담는 게 이 구조의 존재 이유다.
     *
     * 예전에는 기본 무게·횟수 한 쌍만 담아서, 워밍업이 섞인 구성을 저장하면 불러올 때
     * 전부 워밍업 무게가 됐다.
     */
    @Test
    fun 세트_수와_세트별_무게가_그대로_남는다() = runBlocking<Unit> {
        repo.saveAsNewRoutine(
            "가슴",
            listOf(
                RoutineExercise(routineId = 0, orderIndex = 0, exerciseName = "벤치", defaultSetting = "") to
                    listOf(
                        RoutineSet(routineExerciseId = 0, setIndex = 0, weightKg = 40.0, reps = 12),
                        RoutineSet(routineExerciseId = 0, setIndex = 1, weightKg = 60.0, reps = 8),
                        RoutineSet(routineExerciseId = 0, setIndex = 2, weightKg = 60.0, reps = 8),
                        RoutineSet(routineExerciseId = 0, setIndex = 3, weightKg = 60.0, reps = 6)
                    )
            )
        )

        val saved = repo.observeRoutines().first()
            .first { it.routine.name == "가슴" }
            .exercises.single()
        assertEquals("세트 수가 달라졌다", 4, saved.sets.size)
        assertEquals(
            listOf(40.0, 60.0, 60.0, 60.0),
            saved.sets.sortedBy { it.setIndex }.map { it.weightKg }
        )
        assertEquals(
            listOf(12, 8, 8, 6),
            saved.sets.sortedBy { it.setIndex }.map { it.reps }
        )
    }

    @Test
    fun 덮어써도_순서와_기본값이_그대로_들어간다() = runBlocking<Unit> {
        repo.saveAsNewRoutine("등", exercises("풀업"))
        val routine = repo.observeRoutines().first().first { it.routine.name == "등" }

        repo.overwriteRoutine(routine.routine.id, exercises("랫풀다운", "시티드 로우", "페이스 풀"))

        val saved = repo.observeRoutines().first()
            .first { it.routine.name == "등" }
            .exercises.sortedBy { it.exercise.orderIndex }
        assertEquals(listOf(0, 1, 2), saved.map { it.exercise.orderIndex })
        assertEquals(
            listOf(20.0, 21.0, 22.0),
            saved.map { it.sets.minByOrNull { s -> s.setIndex }!!.weightKg }
        )
    }

    /** 오늘 고친 구성이 그 루틴에 그대로 들어가야 다음에 불러올 때 최신이 나온다. */
    @Test
    fun 편집한_구성이_같은_이름의_루틴에_반영된다() = runBlocking<Unit> {
        repo.saveAsNewRoutine("하체", exercises("스쿼트", "레그 프레스"))

        val applied = repo.syncRoutineByName("하체", exercises("스쿼트", "레그 프레스", "레그 컬"))

        assertTrue("반영됐다고 알려야 한다", applied)
        val after = repo.observeRoutines().first().first { it.routine.name == "하체" }
        assertEquals(3, after.exercises.size)
    }

    /**
     * 자동으로 도는 덮어쓰기라 안 되는 경우가 더 중요하다. 되돌릴 수단이 없다.
     */
    @Test
    fun 빈_구성이나_없는_이름은_루틴을_건드리지_않는다() = runBlocking<Unit> {
        repo.saveAsNewRoutine("하체", exercises("스쿼트", "레그 프레스"))

        // 다시 짜려고 종목을 전부 지운 순간 루틴까지 비면 복구할 방법이 없다.
        assertFalse("빈 구성이 반영됐다", repo.syncRoutineByName("하체", emptyList()))
        // 루틴을 지웠거나 이름을 바꾼 뒤의 기록이 여기로 온다.
        assertFalse("없는 루틴에 반영됐다", repo.syncRoutineByName("없는 루틴", exercises("스쿼트")))
        assertFalse("이름이 비었는데 반영됐다", repo.syncRoutineByName("", exercises("스쿼트")))

        val after = repo.observeRoutines().first().first { it.routine.name == "하체" }
        assertEquals("루틴이 바뀌었다", 2, after.exercises.size)
    }

    @Test
    fun 같은_이름의_루틴을_찾아낸다() = runBlocking<Unit> {
        repo.addRoutine("가슴")
        val routine = repo.observeRoutines().first().first()

        assertTrue(repo.routineNameExists("가슴"))
        assertFalse("없는 이름인데 있다고 한다", repo.routineNameExists("가슴 & 삼두"))
        // 이름 수정 화면은 자기 자신과의 충돌을 무시해야 한다 — 아니면 이름을 그대로 둔 채
        // 다른 걸 고치려 할 때마다 막힌다.
        assertFalse(
            "자기 자신을 중복으로 본다",
            repo.routineNameExists("가슴", exceptId = routine.routine.id)
        )
    }
}
