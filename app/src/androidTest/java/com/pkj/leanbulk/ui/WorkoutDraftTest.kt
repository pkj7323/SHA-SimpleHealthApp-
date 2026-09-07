package com.pkj.leanbulk.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pkj.leanbulk.data.TrackingType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** 화면 상태([DraftExercise])를 저장 가능한 모양으로 옮기는 규칙. DB·Compose 없이 돈다. */
@RunWith(AndroidJUnit4::class)
class WorkoutDraftTest {

    private fun draft(memo: String) = DraftExercise(
        name = "벤치프레스",
        defaultSetting = "5x5",
        trackingType = TrackingType.WEIGHT_REPS,
        sets = listOf(DraftSet(weightKg = 60.0, reps = 5, done = true)),
        memo = memo
    )

    @Test
    fun 종목_메모는_그날_기록에_저장된다() {
        val (exercise, _) = listOf(draft("어깨가 뻐근함"))
            .toPersistables(date = "2026-09-08", routineName = "가슴").single()

        assertEquals("어깨가 뻐근함", exercise.memo)
        assertEquals("2026-09-08", exercise.date)
    }

    /**
     * 메모는 **루틴에 담기지 않는다.** 완료 체크를 안 담는 것과 같은 이유로, 메모는 그날
     * 있었던 일이지 다음에 할 계획이 아니다. 담으면 "어깨가 뻐근함"이 매번 따라 나온다.
     * 계획으로 남길 글은 루틴의 기본 세팅(`defaultSetting`)이 맡는다.
     */
    @Test
    fun 종목_메모는_루틴에는_안_담긴다() {
        val (routineExercise, _) = listOf(draft("어깨가 뻐근함")).toRoutineExercises().single()

        assertEquals("5x5", routineExercise.defaultSetting)
        assertEquals("벤치프레스", routineExercise.exerciseName)
    }
}
