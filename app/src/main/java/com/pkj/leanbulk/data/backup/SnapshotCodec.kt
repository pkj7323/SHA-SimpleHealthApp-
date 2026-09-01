package com.pkj.leanbulk.data.backup

import com.pkj.leanbulk.data.BodyRecord
import com.pkj.leanbulk.data.DietTarget
import com.pkj.leanbulk.data.DietTemplateMeal
import com.pkj.leanbulk.data.DietTemplateWithMeals
import com.pkj.leanbulk.data.ExerciseWithSets
import com.pkj.leanbulk.data.FoodItem
import com.pkj.leanbulk.data.Macros
import com.pkj.leanbulk.data.MealEntry
import com.pkj.leanbulk.data.RoutineExercise
import com.pkj.leanbulk.data.RoutineWithExercises
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

    /** 종목을 날짜별로 묶고 세트를 그 안에 중첩한다. */
    fun List<ExerciseWithSets>.toWorkoutDayDtos(): List<WorkoutDayDto> =
        groupBy { it.exercise.date }
            .toSortedMap()
            .map { (date, list) ->
                WorkoutDayDto(
                    date = date,
                    exercises = list.sortedBy { it.exercise.orderIndex }.map { ews ->
                        WorkoutExerciseDto(
                            routineName = ews.exercise.routineName,
                            orderIndex = ews.exercise.orderIndex,
                            exerciseName = ews.exercise.exerciseName,
                            defaultSetting = ews.exercise.defaultSetting,
                            memo = ews.exercise.memo,
                            sets = ews.sets.sortedBy { it.setIndex }.map {
                                WorkoutSetDto(it.setIndex, it.weightKg, it.reps, it.done)
                            }
                        )
                    }
                )
            }

    fun RoutineWithExercises.toDto() = RoutineDto(
        name = routine.name,
        orderIndex = routine.orderIndex,
        exercises = exercises.sortedBy { it.orderIndex }.map {
            RoutineExerciseDto(it.orderIndex, it.exerciseName, it.defaultSetting, it.defaultWeight, it.defaultReps)
        }
    )

    fun DietTemplateWithMeals.toDto() = DietTemplateDto(
        name = template.name,
        orderIndex = template.orderIndex,
        meals = meals.sortedBy { it.orderIndex }.map {
            DietTemplateMealDto(it.orderIndex, it.slotName, it.menuText, it.macros.toDto())
        }
    )

    fun FoodItem.toDto() = FoodDto(name, macros.toDto(), usedCount)

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

    fun DietTarget.toDto() = TargetDto(kcal, protein, carbs, fat, sugar)

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

    /** 하루치 종목을 `WorkoutDao.replaceDay`가 받는 (종목, 세트들) 쌍으로 편다. */
    fun WorkoutDayDto.toEntityPairs(): List<Pair<WorkoutExercise, List<WorkoutSet>>> =
        exercises.map { ex ->
            WorkoutExercise(
                date = date,
                routineName = ex.routineName,
                orderIndex = ex.orderIndex,
                exerciseName = ex.exerciseName,
                defaultSetting = ex.defaultSetting,
                memo = ex.memo
            ) to ex.sets.map {
                WorkoutSet(exerciseId = 0, setIndex = it.setIndex, weightKg = it.weightKg, reps = it.reps, done = it.done)
            }
        }

    fun RoutineExerciseDto.toEntity(routineId: Long) = RoutineExercise(
        routineId = routineId,
        orderIndex = orderIndex,
        exerciseName = exerciseName,
        defaultSetting = defaultSetting,
        defaultWeight = defaultWeight,
        defaultReps = defaultReps
    )

    fun DietTemplateMealDto.toEntity(templateId: Long) = DietTemplateMeal(
        templateId = templateId,
        orderIndex = orderIndex,
        slotName = slotName,
        menuText = menuText,
        macros = macros.toEntity()
    )

    fun FoodDto.toEntity() = FoodItem(name = name, macros = macros.toEntity(), usedCount = usedCount)

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

    fun TargetDto.toEntity() = DietTarget(kcal = kcal, protein = protein, carbs = carbs, fat = fat, sugar = sugar)
}
