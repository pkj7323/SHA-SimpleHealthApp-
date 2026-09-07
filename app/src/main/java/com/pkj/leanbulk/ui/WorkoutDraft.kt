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
    val sets: List<DraftSet>,
    /**
     * 그날 이 종목에 대해 적어둔 글. **루틴에는 안 담긴다**([toRoutineExercises]) —
     * 완료 체크와 같은 이유로, 그날 있었던 일이지 다음에 할 계획이 아니다.
     */
    val memo: String = ""
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

/**
 * 루틴을 불러온다. **저장할 때의 세트가 그대로 나온다** — 세트 수도, 세트마다 다른 무게도.
 *
 * 세트가 하나도 없으면(옛 백업에서 들어온 루틴 등) 빈 세트 [SeedData.DEFAULT_SET_COUNT]개를
 * 준다. 종목만 있고 칸이 아예 없으면 기록을 시작할 수가 없기 때문이다.
 */
fun com.pkj.leanbulk.data.RoutineExerciseWithSets.toDraft(): DraftExercise = DraftExercise(
    name = exercise.exerciseName,
    defaultSetting = exercise.defaultSetting,
    exerciseId = exercise.exerciseId,
    trackingType = exercise.trackingType,
    sets = sets.sortedBy { it.setIndex }
        .map { DraftSet(it.weightKg, it.reps, durationSec = it.durationSec, distanceM = it.distanceM) }
        .ifEmpty { List(SeedData.DEFAULT_SET_COUNT) { DraftSet(weightKg = 0.0, reps = 0) } }
)

fun ExerciseWithSets.toDraft(): DraftExercise = DraftExercise(
    name = exercise.exerciseName,
    defaultSetting = exercise.defaultSetting,
    exerciseId = exercise.exerciseId,
    trackingType = exercise.trackingType,
    sets = sets.sortedBy { it.setIndex }.map {
        DraftSet(it.weightKg, it.reps, it.done, it.durationSec, it.distanceM)
    },
    memo = exercise.memo
)

/**
 * 지금 화면의 종목 구성을 루틴으로 굳힌다. **세트를 있는 그대로 담는다** — 워밍업 한 세트가
 * 가벼운 구성도 다음에 불러오면 똑같이 나온다.
 *
 * 완료 체크(`done`)는 안 담는다. 루틴은 "이렇게 할 것"이지 "이렇게 했다"가 아니다.
 * 메모(`defaultSetting`)는 사용자가 적은 글이라 지어내지 않고 있던 것을 그대로 옮긴다.
 */
fun List<DraftExercise>.toRoutineExercises():
    List<Pair<com.pkj.leanbulk.data.RoutineExercise, List<com.pkj.leanbulk.data.RoutineSet>>> =
    mapIndexed { index, draft ->
        com.pkj.leanbulk.data.RoutineExercise(
            routineId = 0,
            orderIndex = index,
            exerciseId = draft.exerciseId,
            exerciseName = draft.name,
            trackingType = draft.trackingType,
            defaultSetting = draft.defaultSetting
        ) to draft.sets.mapIndexed { setIndex, set ->
            com.pkj.leanbulk.data.RoutineSet(
                routineExerciseId = 0,
                setIndex = setIndex,
                weightKg = set.weightKg,
                reps = set.reps,
                durationSec = set.durationSec,
                distanceM = set.distanceM
            )
        }
    }

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
        defaultSetting = draft.defaultSetting,
        memo = draft.memo
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
