package com.pkj.leanbulk.ui

import com.pkj.leanbulk.data.Exercise
import com.pkj.leanbulk.data.ExerciseWithSets
import com.pkj.leanbulk.data.SeedData
import com.pkj.leanbulk.data.TrackingType
import com.pkj.leanbulk.data.WorkoutExercise
import com.pkj.leanbulk.data.WorkoutSet

/**
 * 화면에서 편집 중인 세트 한 줄. 아직 저장 전 상태.
 *
 * 추적 방식마다 쓰는 칸이 다르다 — 무게×횟수 종목은 [durationSec]/[distanceM]이 null이고,
 * 플랭크 같은 시간형은 [weightKg]/[reps]가 0으로 남는다. 어느 칸을 보여줄지는
 * [DraftExercise.trackingType]이 정한다.
 */
data class DraftSet(
    val weightKg: Double,
    val reps: Int,
    val done: Boolean = false,
    val durationSec: Int? = null,
    val distanceM: Double? = null
)

/** 화면에서 편집 중인 종목 하나. */
data class DraftExercise(
    val name: String,
    val defaultSetting: String = "",
    val exerciseId: Long? = null,
    val trackingType: TrackingType = TrackingType.WEIGHT_REPS,
    val sets: List<DraftSet>
) {
    /** 무게를 안 쓰는 종목은 자연히 0이 된다. 볼륨이라는 개념 자체가 없기 때문. */
    val volume: Double get() = sets.filter { it.done }.sumOf { it.weightKg * it.reps }
}

/** 카탈로그에서 종목을 고르면 추적 방식이 따라온다. */
fun Exercise.toDraft(): DraftExercise = DraftExercise(
    name = name,
    exerciseId = id,
    trackingType = trackingType,
    sets = List(SeedData.DEFAULT_SET_COUNT) { DraftSet(weightKg = 0.0, reps = 0) }
)

fun com.pkj.leanbulk.data.RoutineExercise.toDraft(): DraftExercise = DraftExercise(
    name = exerciseName,
    defaultSetting = defaultSetting,
    exerciseId = exerciseId,
    trackingType = trackingType,
    sets = List(SeedData.DEFAULT_SET_COUNT) { DraftSet(defaultWeight, defaultReps) }
)

fun ExerciseWithSets.toDraft(): DraftExercise = DraftExercise(
    name = exercise.exerciseName,
    defaultSetting = exercise.defaultSetting,
    exerciseId = exercise.exerciseId,
    trackingType = exercise.trackingType,
    sets = sets.sortedBy { it.setIndex }.map {
        DraftSet(it.weightKg, it.reps, it.done, it.durationSec, it.distanceM)
    }
)

fun List<DraftExercise>.toPersistables(
    date: String,
    routineName: String
): List<Pair<WorkoutExercise, List<WorkoutSet>>> = mapIndexed { index, draft ->
    val exercise = WorkoutExercise(
        date = date,
        routineName = routineName,
        orderIndex = index,
        exerciseId = draft.exerciseId,
        exerciseName = draft.name,
        trackingType = draft.trackingType,
        defaultSetting = draft.defaultSetting
    )
    val sets = draft.sets.mapIndexed { setIndex, s ->
        WorkoutSet(
            exerciseId = 0,
            setIndex = setIndex,
            weightKg = s.weightKg,
            reps = s.reps,
            done = s.done,
            durationSec = s.durationSec,
            distanceM = s.distanceM
        )
    }
    exercise to sets
}
