package com.pkj.leanbulk.data.backup

import kotlinx.serialization.Serializable

/**
 * 백업 · 동기화용 스냅샷 포맷.
 *
 * **엔티티를 직접 직렬화하지 않고 별도 DTO를 둔다.** 엔티티는 앞으로 계속 바뀌는데(카탈로그 계층,
 * 시간형 세트 등), DTO가 엔티티와 같으면 엔티티를 고칠 때마다 파일 포맷이 조용히 따라 바뀌어서
 * 예전 백업을 못 읽게 된다. 분리해두면 포맷 변경이 의도적인 결정이 된다.
 *
 * 로컬 auto ID는 넣지 않는다 — 기기마다 다른 값이라 의미가 없고, 불러올 때 충돌만 만든다.
 * 부모-자식 관계는 ID 대신 **중첩 구조**로 표현한다.
 */
@Serializable
data class Snapshot(
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val exportedAt: String,
    val meals: List<MealDto> = emptyList(),
    val workoutDays: List<WorkoutDayDto> = emptyList(),
    val routines: List<RoutineDto> = emptyList(),
    val dietTemplates: List<DietTemplateDto> = emptyList(),
    val foods: List<FoodDto> = emptyList(),
    val bodyRecords: List<BodyRecordDto> = emptyList(),
    val target: TargetDto? = null
) {
    companion object {
        const val CURRENT_FORMAT_VERSION = 1
    }
}

/**
 * 매크로는 전부 nullable이다. `null`("모름")과 `0.0`("정말 0g")은 다른 뜻이므로
 * 직렬화할 때 null을 생략하지 않고 그대로 기록한다.
 */
@Serializable
data class MacrosDto(
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val sugar: Double? = null
)

@Serializable
data class MealDto(
    val date: String,
    val orderIndex: Int,
    val slotName: String,
    val menuText: String,
    val macros: MacrosDto,
    val eaten: Boolean,
    val memo: String
)

/** 하루치 운동. 세트를 종목 안에 중첩해서 담는다. */
@Serializable
data class WorkoutDayDto(
    val date: String,
    val exercises: List<WorkoutExerciseDto> = emptyList()
)

@Serializable
data class WorkoutExerciseDto(
    val routineName: String,
    val orderIndex: Int,
    val exerciseName: String,
    val defaultSetting: String,
    val memo: String,
    val sets: List<WorkoutSetDto> = emptyList()
)

@Serializable
data class WorkoutSetDto(
    val setIndex: Int,
    val weightKg: Double,
    val reps: Int,
    val done: Boolean
)

@Serializable
data class RoutineDto(
    val name: String,
    val orderIndex: Int,
    val exercises: List<RoutineExerciseDto> = emptyList()
)

@Serializable
data class RoutineExerciseDto(
    val orderIndex: Int,
    val exerciseName: String,
    val defaultSetting: String,
    val defaultWeight: Double,
    val defaultReps: Int
)

@Serializable
data class DietTemplateDto(
    val name: String,
    val orderIndex: Int,
    val meals: List<DietTemplateMealDto> = emptyList()
)

@Serializable
data class DietTemplateMealDto(
    val orderIndex: Int,
    val slotName: String,
    val menuText: String,
    val macros: MacrosDto
)

@Serializable
data class FoodDto(
    val name: String,
    val macros: MacrosDto,
    val usedCount: Int
)

@Serializable
data class BodyRecordDto(
    val date: String,
    val weightKg: Double,
    val muscleMassKg: Double? = null,
    val fatMassKg: Double? = null,
    val fatPercent: Double? = null,
    val waistCm: Double? = null,
    val tool: String,
    val memo: String
)

@Serializable
data class TargetDto(
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val sugar: Double
)
