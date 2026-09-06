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
    /**
     * 이 기록 뭉치에 어느 버전의 시드까지 반영됐는지. 불러오기로 같이 넘어간다.
     *
     * 옛날 백업(시드 v1)을 지금 앱(시드 v3)에서 불러오면 이 값이 1로 돌아가고,
     * 다음 실행 때 2·3단계 시드가 채워진다. 안 들고 다니면 불러온 데이터에
     * 새 시드가 영영 안 들어간다.
     */
    val seedVersion: Int = 0,
    val exercises: List<ExerciseDto> = emptyList(),
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
    val memo: String,
    /**
     * 매크로의 출처(직접 입력 / 카탈로그 / 어림값 확정).
     *
     * 옛 백업엔 이 값이 없다. 그때는 셋을 구분하지 않고 저장했으므로 지금 와서 갈라낼 수
     * 없고, 매크로가 있으면 `ENTERED`, 없으면 `NONE`으로 읽는다(마이그레이션 10→11과 같은 처리).
     */
    val macroSource: String? = null
)

/** 하루치 운동. 세트를 종목 안에 중첩해서 담는다. */
@Serializable
data class WorkoutDayDto(
    val date: String,
    val exercises: List<WorkoutExerciseDto> = emptyList()
)

/**
 * 종목 카탈로그.
 *
 * 열거형을 문자열로 싣는 이유: 나중 버전이 추적 방식을 추가해도 **구버전 앱이 그 파일을
 * 읽다가 죽지 않게** 하기 위함이다. 모르는 값은 기본값으로 떨어뜨린다.
 *
 * 예전엔 `hidden`이 있었다(종목 숨기기 기능). 기능을 없애면서 빠졌는데, 옛 백업의
 * `hidden` 키는 `ignoreUnknownKeys`가 그냥 흘려보내므로 예전 파일도 계속 읽힌다.
 */
@Serializable
data class ExerciseDto(
    val slug: String,
    val name: String,
    val bodyPart: String,
    val equipment: String,
    val trackingType: String,
    val source: String
)

@Serializable
data class WorkoutExerciseDto(
    val routineName: String,
    val orderIndex: Int,
    /**
     * 카탈로그 참조는 **로컬 id가 아니라 slug로** 싣는다. auto id는 기기마다 달라
     * 다른 기기에서 읽으면 엉뚱한 종목을 가리킨다. 카탈로그에 없는 종목이면 null.
     */
    val exerciseSlug: String? = null,
    val exerciseName: String,
    val trackingType: String = "WEIGHT_REPS",
    val defaultSetting: String,
    val memo: String,
    val sets: List<WorkoutSetDto> = emptyList()
)

@Serializable
data class WorkoutSetDto(
    val setIndex: Int,
    val weightKg: Double,
    val reps: Int,
    val done: Boolean,
    val durationSec: Int? = null,
    val distanceM: Double? = null
)

@Serializable
data class RoutineDto(
    val name: String,
    val orderIndex: Int,
    val exercises: List<RoutineExerciseDto> = emptyList()
)

/**
 * 루틴에 든 종목 하나.
 *
 * [sets]가 실제 내용이다. [defaultWeight]/[defaultReps]는 **세트를 담지 못하던 시절의 잔재**로,
 * 옛 백업을 읽을 때만 쓴다(그 파일엔 `sets`가 없으므로 이 값으로 세트를 펴 준다).
 * 내보낼 때는 첫 세트 값을 넣어 둔다 — 구버전 앱이 이 파일을 읽어도 루틴이 비어 보이지 않게.
 */
@Serializable
data class RoutineExerciseDto(
    val orderIndex: Int,
    val exerciseSlug: String? = null,
    val exerciseName: String,
    val trackingType: String = "WEIGHT_REPS",
    val defaultSetting: String,
    val defaultWeight: Double,
    val defaultReps: Int,
    val sets: List<RoutineSetDto> = emptyList()
)

/** 루틴 종목의 세트. 완료 여부가 없다는 점만 [WorkoutSetDto]와 다르다. */
@Serializable
data class RoutineSetDto(
    val setIndex: Int,
    val weightKg: Double,
    val reps: Int,
    val durationSec: Int? = null,
    val distanceM: Double? = null
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
    val slug: String = "",
    val name: String,
    val category: String = "기타",
    val macros: MacrosDto,
    val usedCount: Int,
    val source: String = "USER",
    val hidden: Boolean = false
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
    val sugar: Double,
    val goal: String = "MAINTAIN",
    val activityLevel: String = "MEDIUM"
)
