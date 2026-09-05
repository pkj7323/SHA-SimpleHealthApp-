package com.pkj.leanbulk.data.backup

import com.pkj.leanbulk.data.BodyRecord
import com.pkj.leanbulk.data.ActivityLevel
import com.pkj.leanbulk.data.DietTarget
import com.pkj.leanbulk.data.GoalType
import com.pkj.leanbulk.data.DietTemplateMeal
import com.pkj.leanbulk.data.DietTemplateWithMeals
import com.pkj.leanbulk.data.Exercise
import com.pkj.leanbulk.data.ExerciseWithSets
import com.pkj.leanbulk.data.FoodItem
import com.pkj.leanbulk.data.ItemSource
import com.pkj.leanbulk.data.TrackingType
import com.pkj.leanbulk.data.Macros
import com.pkj.leanbulk.data.MealEntry
import com.pkj.leanbulk.data.RoutineExercise
import com.pkj.leanbulk.data.RoutineSet
import com.pkj.leanbulk.data.RoutineWithExercises
import com.pkj.leanbulk.data.SeedData
import com.pkj.leanbulk.data.WorkoutExercise
import com.pkj.leanbulk.data.WorkoutSet
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * 스냅샷을 JSON(gzip)으로 굽고 되읽는다. 엔티티 ↔ DTO 변환도 여기 모아둔다.
 *
 * 이 변환이 무손실이어야 동기화가 안전하다. 필드를 하나 빠뜨리면 동기화할 때마다
 * 조용히 데이터가 사라지므로, `SnapshotRoundTripTest`가 왕복을 강제로 검증한다.
 */
object SnapshotCodec {

    private val json = Json {
        prettyPrint = true       // gzip으로 압축하므로 크기 손해가 없고, 열어서 눈으로 확인할 수 있다
        encodeDefaults = true    // formatVersion 같은 기본값도 파일에 남긴다
        ignoreUnknownKeys = true // 나중 버전이 추가한 필드를 지금 버전이 만나도 죽지 않게
    }

    fun toJson(snapshot: Snapshot): String = json.encodeToString(snapshot)

    fun fromJson(text: String): Snapshot = json.decodeFromString(text)

    fun gzip(snapshot: Snapshot): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(toJson(snapshot).toByteArray()) }
        return out.toByteArray()
    }

    fun gunzip(input: InputStream): Snapshot =
        GZIPInputStream(input).use { fromJson(it.readBytes().decodeToString()) }

    // ===================== 엔티티 → DTO =====================

    fun Macros.toDto() = MacrosDto(kcal, protein, carbs, fat, sugar)

    fun MealEntry.toDto() = MealDto(
        date = date,
        orderIndex = orderIndex,
        slotName = slotName,
        menuText = menuText,
        macros = macros.toDto(),
        eaten = eaten,
        memo = memo
    )

    fun Exercise.toDto() = ExerciseDto(
        slug = slug,
        name = name,
        bodyPart = bodyPart,
        equipment = equipment,
        trackingType = trackingType.name,
        source = source.name
    )

    /**
     * 종목을 날짜별로 묶고 세트를 그 안에 중첩한다.
     *
     * [slugById]는 카탈로그의 로컬 id를 slug로 바꾸기 위한 표다. id를 그대로 실으면
     * 다른 기기에서 엉뚱한 종목을 가리키게 된다.
     */
    fun List<ExerciseWithSets>.toWorkoutDayDtos(slugById: Map<Long, String>): List<WorkoutDayDto> =
        groupBy { it.exercise.date }
            .toSortedMap()
            .map { (date, list) ->
                WorkoutDayDto(
                    date = date,
                    exercises = list.sortedBy { it.exercise.orderIndex }.map { ews ->
                        WorkoutExerciseDto(
                            routineName = ews.exercise.routineName,
                            orderIndex = ews.exercise.orderIndex,
                            exerciseSlug = ews.exercise.exerciseId?.let { slugById[it] },
                            exerciseName = ews.exercise.exerciseName,
                            trackingType = ews.exercise.trackingType.name,
                            defaultSetting = ews.exercise.defaultSetting,
                            memo = ews.exercise.memo,
                            sets = ews.sets.sortedBy { it.setIndex }.map {
                                WorkoutSetDto(
                                    it.setIndex, it.weightKg, it.reps, it.done, it.durationSec, it.distanceM
                                )
                            }
                        )
                    }
                )
            }

    fun RoutineWithExercises.toDto(slugById: Map<Long, String>) = RoutineDto(
        name = routine.name,
        orderIndex = routine.orderIndex,
        exercises = exercises.sortedBy { it.exercise.orderIndex }.map { item ->
            val sets = item.sets.sortedBy { it.setIndex }
            RoutineExerciseDto(
                orderIndex = item.exercise.orderIndex,
                exerciseSlug = item.exercise.exerciseId?.let { id -> slugById[id] },
                exerciseName = item.exercise.exerciseName,
                trackingType = item.exercise.trackingType.name,
                defaultSetting = item.exercise.defaultSetting,
                // 구버전 앱이 읽을 때를 위한 값. 지금 앱은 아래 sets만 본다.
                defaultWeight = sets.firstOrNull()?.weightKg ?: 0.0,
                defaultReps = sets.firstOrNull()?.reps ?: 0,
                sets = sets.map {
                    RoutineSetDto(it.setIndex, it.weightKg, it.reps, it.durationSec, it.distanceM)
                }
            )
        }
    )

    fun DietTemplateWithMeals.toDto() = DietTemplateDto(
        name = template.name,
        orderIndex = template.orderIndex,
        meals = meals.sortedBy { it.orderIndex }.map {
            DietTemplateMealDto(it.orderIndex, it.slotName, it.menuText, it.macros.toDto())
        }
    )

    fun FoodItem.toDto() = FoodDto(
        slug = slug,
        name = name,
        category = category,
        macros = macros.toDto(),
        usedCount = usedCount,
        source = source.name,
        hidden = hidden
    )

    fun BodyRecord.toDto() = BodyRecordDto(
        date = date,
        weightKg = weightKg,
        muscleMassKg = muscleMassKg,
        fatMassKg = fatMassKg,
        fatPercent = fatPercent,
        waistCm = waistCm,
        tool = tool,
        memo = memo
    )

    fun DietTarget.toDto() = TargetDto(kcal, protein, carbs, fat, sugar, goal.name, activityLevel.name)

    // ===================== DTO → 엔티티 =====================
    // id는 전부 0으로 두고 Room이 새로 매기게 한다. 관계는 중첩 구조로 복원한다.

    fun MacrosDto.toEntity() = Macros(kcal, protein, carbs, fat, sugar)

    fun MealDto.toEntity() = MealEntry(
        date = date,
        orderIndex = orderIndex,
        slotName = slotName,
        menuText = menuText,
        macros = macros.toEntity(),
        eaten = eaten,
        memo = memo
    )

    /** 모르는 값이 와도 죽지 않게 기본값으로 떨어뜨린다(구버전이 신버전 파일을 읽는 경우). */
    private fun trackingTypeOf(name: String): TrackingType =
        runCatching { TrackingType.valueOf(name) }.getOrDefault(TrackingType.WEIGHT_REPS)

    fun ExerciseDto.toEntity() = Exercise(
        slug = slug,
        name = name,
        bodyPart = bodyPart,
        equipment = equipment,
        trackingType = trackingTypeOf(trackingType),
        source = runCatching { ItemSource.valueOf(source) }.getOrDefault(ItemSource.USER)
    )

    /**
     * 하루치 종목을 `WorkoutDao.replaceDay`가 받는 (종목, 세트들) 쌍으로 편다.
     * [idBySlug]로 카탈로그 참조를 이 기기의 로컬 id로 되돌린다.
     */
    fun WorkoutDayDto.toEntityPairs(idBySlug: Map<String, Long>): List<Pair<WorkoutExercise, List<WorkoutSet>>> =
        exercises.map { ex ->
            WorkoutExercise(
                date = date,
                routineName = ex.routineName,
                orderIndex = ex.orderIndex,
                exerciseId = ex.exerciseSlug?.let { idBySlug[it] },
                exerciseName = ex.exerciseName,
                trackingType = trackingTypeOf(ex.trackingType),
                defaultSetting = ex.defaultSetting,
                memo = ex.memo
            ) to ex.sets.map {
                WorkoutSet(
                    exerciseId = 0,
                    setIndex = it.setIndex,
                    weightKg = it.weightKg,
                    reps = it.reps,
                    done = it.done,
                    durationSec = it.durationSec,
                    distanceM = it.distanceM
                )
            }
        }

    /**
     * 루틴 종목 + 세트로 되돌린다.
     *
     * `sets`가 비어 있으면 **세트를 담지 못하던 시절의 백업**이다. 그때는 기본값 한 쌍으로
     * 세트 [SeedData.DEFAULT_SET_COUNT]개를 폈으므로, 그 파일을 읽을 때도 같은 모양으로 편다.
     */
    fun RoutineExerciseDto.toEntityPair(
        routineId: Long,
        idBySlug: Map<String, Long>
    ): Pair<RoutineExercise, List<RoutineSet>> {
        val exercise = RoutineExercise(
            routineId = routineId,
            orderIndex = orderIndex,
            exerciseId = exerciseSlug?.let { idBySlug[it] },
            exerciseName = exerciseName,
            trackingType = trackingTypeOf(trackingType),
            defaultSetting = defaultSetting
        )
        val restored = sets
            .sortedBy { it.setIndex }
            .map {
                RoutineSet(
                    routineExerciseId = 0,
                    setIndex = it.setIndex,
                    weightKg = it.weightKg,
                    reps = it.reps,
                    durationSec = it.durationSec,
                    distanceM = it.distanceM
                )
            }
            .ifEmpty {
                List(SeedData.DEFAULT_SET_COUNT) { index ->
                    RoutineSet(
                        routineExerciseId = 0,
                        setIndex = index,
                        weightKg = defaultWeight,
                        reps = defaultReps
                    )
                }
            }
        return exercise to restored
    }

    fun DietTemplateMealDto.toEntity(templateId: Long) = DietTemplateMeal(
        templateId = templateId,
        orderIndex = orderIndex,
        slotName = slotName,
        menuText = menuText,
        macros = macros.toEntity()
    )

    fun FoodDto.toEntity() = FoodItem(
        // 옛 백업엔 slug가 없다. 비워 두면 유니크 인덱스에 걸리므로 그 자리에서 만들어 붙인다.
        slug = slug.ifBlank { "user-restored-${name.hashCode()}" },
        name = name,
        category = category,
        macros = macros.toEntity(),
        usedCount = usedCount,
        source = runCatching { ItemSource.valueOf(source) }.getOrDefault(ItemSource.USER),
        hidden = hidden
    )

    fun BodyRecordDto.toEntity() = BodyRecord(
        date = date,
        weightKg = weightKg,
        muscleMassKg = muscleMassKg,
        fatMassKg = fatMassKg,
        fatPercent = fatPercent,
        waistCm = waistCm,
        tool = tool,
        memo = memo
    )

    fun TargetDto.toEntity() = DietTarget(
        kcal = kcal, protein = protein, carbs = carbs, fat = fat, sugar = sugar,
        goal = runCatching { GoalType.valueOf(goal) }.getOrDefault(GoalType.MAINTAIN),
        activityLevel = runCatching { ActivityLevel.valueOf(activityLevel) }
            .getOrDefault(ActivityLevel.MEDIUM)
    )
}
