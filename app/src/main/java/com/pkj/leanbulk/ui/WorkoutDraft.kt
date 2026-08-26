package com.pkj.leanbulk.ui

import com.pkj.leanbulk.data.ExerciseWithSets
import com.pkj.leanbulk.data.SeedData
import com.pkj.leanbulk.data.WorkoutExercise
import com.pkj.leanbulk.data.WorkoutSet

/** 화면에서 편집 중인 세트 한 줄. 아직 저장 전 상태. */
data class DraftSet(
    val weightKg: Double,
    val reps: Int,
    val done: Boolean = false
)

/** 화면에서 편집 중인 종목 하나. */
data class DraftExercise(
    val name: String,
    val defaultSetting: String = "",
    val sets: List<DraftSet>
) {
    val volume: Double get() = sets.filter { it.done }.sumOf { it.weightKg * it.reps }
}

fun com.pkj.leanbulk.data.RoutineExercise.toDraft(): DraftExercise = DraftExercise(
    name = exerciseName,
    defaultSetting = defaultSetting,
    sets = List(SeedData.DEFAULT_SET_COUNT) { DraftSet(defaultWeight, defaultReps) }
)

fun ExerciseWithSets.toDraft(): DraftExercise = DraftExercise(
    name = exercise.exerciseName,
    defaultSetting = exercise.defaultSetting,
    sets = sets.sortedBy { it.setIndex }.map { DraftSet(it.weightKg, it.reps, it.done) }
)

fun List<DraftExercise>.toPersistables(
    date: String,
    routineName: String
): List<Pair<WorkoutExercise, List<WorkoutSet>>> = mapIndexed { index, draft ->
    val exercise = WorkoutExercise(
        date = date,
        routineName = routineName,
        orderIndex = index,
        exerciseName = draft.name,
        defaultSetting = draft.defaultSetting
    )
    val sets = draft.sets.mapIndexed { setIndex, s ->
        WorkoutSet(exerciseId = 0, setIndex = setIndex, weightKg = s.weightKg, reps = s.reps, done = s.done)
    }
    exercise to sets
}
