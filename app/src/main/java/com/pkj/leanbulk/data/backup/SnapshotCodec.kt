package com.pkj.leanbulk.data.backup

import com.pkj.leanbulk.data.AmountUnit
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
import com.pkj.leanbulk.data.MacroSource
import com.pkj.leanbulk.data.Macros
import com.pkj.leanbulk.data.MealEntry
import com.pkj.leanbulk.data.MealEntryWithFoods
import com.pkj.leanbulk.data.MealFood
import com.pkj.leanbulk.data.MealPreset
import com.pkj.leanbulk.data.MealPresetFood
import com.pkj.leanbulk.data.MealPresetWithFoods
import com.pkj.leanbulk.data.RoutineExercise
import com.pkj.leanbulk.data.RoutineSet
import com.pkj.leanbulk.data.RoutineWithExercises
import com.pkj.leanbulk.data.SeedData
import com.pkj.leanbulk.data.WorkoutExercise
import com.pkj.leanbulk.data.WorkoutSession
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
        memo = memo,
        macroSource = macroSource.name
    )

    /** 음식 줄까지 포함해 끼니 하나를 내보낸다. */
    fun MealEntryWithFoods.toDto(foodSlugById: Map<Long, String>) = entry.toDto().copy(
        foods = foods.sortedBy { it.orderIndex }.map { f ->
            MealFoodDto(
                orderIndex = f.orderIndex,
                foodSlug = f.foodId?.let { foodSlugById[it] },
                name = f.name,
                multiplier = f.multiplier,
                amountGram = f.amount,
                amountUnit = f.amountUnit.name,
                macros = f.macros.toDto(),
                macroSource = f.macroSource.name
            )
        }
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
    fun List<ExerciseWithSets>.toWorkoutDayDtos(
        slugById: Map<Long, String>,
        sessions: Map<String, WorkoutSession> = emptyMap()
    ): List<WorkoutDayDto> {
        val byDate = groupBy { it.exercise.date }
        // 시작 시각만 있고 종목은 안 담은 날도 있다(운동을 켜두고 아무것도 안 적은 날).
        // 종목 쪽 날짜만 훑으면 그런 날이 백업에서 통째로 빠진다.
        return (byDate.keys + sessions.keys).sorted()
            .map { date ->
                val list = byDate[date].orEmpty()
                val session = sessions[date]
                WorkoutDayDto(
                    date = date,
                    startedAt = session?.startedAtEpochMs,
                    accumulatedMs = session?.accumulatedMs,
                    resumedAt = session?.resumedAtEpochMs,
                    endedAt = session?.endedAtEpochMs,
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
    }

    /** 시작 시각이 실려 있는 날만 세션 한 줄이 된다. 없으면 그날은 타이머를 안 쓴 것이다. */
    fun WorkoutDayDto.toSession(): WorkoutSession? = startedAt?.let { started ->
        WorkoutSession(
            date = date,
            startedAtEpochMs = started,
            // 옛 백업(멈춤이 없던 시절)엔 누적이 없다. 그때는 시작~종료가 곧 운동 시간이었다.
            accumulatedMs = accumulatedMs ?: endedAt?.let { (it - started).coerceAtLeast(0) } ?: 0,
            // 도는 중이던 기록도 같은 규칙으로 읽는다 — 끝난 표시가 없으면 시작이 곧 구간 시작.
            resumedAtEpochMs = resumedAt ?: started.takeIf { accumulatedMs == null && endedAt == null },
            endedAtEpochMs = endedAt
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
        baseAmountGram = baseAmount,
        baseAmountUnit = baseAmountUnit.name,
        favorite = favorite
    )

    /** 세트 안 음식까지 포함해 "내 식사" 하나를 내보낸다. */
    fun MealPresetWithFoods.toDto(foodSlugById: Map<Long, String>) = MealPresetDto(
        name = preset.name,
        orderIndex = preset.orderIndex,
        foods = foods.sortedBy { it.orderIndex }.map { f ->
            MealPresetFoodDto(
                orderIndex = f.orderIndex,
                foodSlug = f.foodId?.let { foodSlugById[it] },
                name = f.name,
                multiplier = f.multiplier,
                amountGram = f.amount,
                amountUnit = f.amountUnit.name,
                macros = f.macros.toDto(),
                macroSource = f.macroSource.name
            )
        }
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

    fun MealDto.toEntity(): MealEntry {
        val restored = macros.toEntity()
        return MealEntry(
            date = date,
            orderIndex = orderIndex,
            slotName = slotName,
            menuText = menuText,
            macros = restored,
            eaten = eaten,
            memo = memo,
            // 옛 백업엔 출처가 없다. 있으면 값이 있는 것으로, 없으면 모름으로 읽는다.
            macroSource = macroSource?.let {
                runCatching { MacroSource.valueOf(it) }.getOrNull()
            } ?: if (restored.isEmpty) MacroSource.NONE else MacroSource.ENTERED
        )
    }

    /** 끼니 + 그 안의 음식 줄을 되돌린다. `foods`가 비어 있으면 옛 자유 텍스트 방식이다. */
    fun MealDto.toEntityPair(idBySlug: Map<String, Long>): Pair<MealEntry, List<MealFood>> {
        val entity = toEntity()
        val restoredFoods = foods.sortedBy { it.orderIndex }.map { f ->
            MealFood(
                mealEntryId = 0,
                orderIndex = f.orderIndex,
                foodId = f.foodSlug?.let { idBySlug[it] },
                name = f.name,
                multiplier = f.multiplier,
                amount = f.amountGram,
                amountUnit = amountUnitOf(f.amountUnit),
                macros = f.macros.toEntity(),
                macroSource = runCatching { MacroSource.valueOf(f.macroSource) }.getOrDefault(MacroSource.CATALOG)
            )
        }
        return entity to restoredFoods
    }

    private fun amountUnitOf(name: String): AmountUnit =
        runCatching { AmountUnit.valueOf(name) }.getOrDefault(AmountUnit.G)

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
        baseAmount = baseAmountGram,
        baseAmountUnit = amountUnitOf(baseAmountUnit),
        favorite = favorite
    )

    /** "내 식사" + 그 안의 음식 줄을 되돌린다. */
    fun MealPresetDto.toEntityPair(idBySlug: Map<String, Long>): Pair<MealPreset, List<MealPresetFood>> {
        val entity = MealPreset(name = name, orderIndex = orderIndex)
        val restoredFoods = foods.sortedBy { it.orderIndex }.map { f ->
            MealPresetFood(
                presetId = 0,
                orderIndex = f.orderIndex,
                foodId = f.foodSlug?.let { idBySlug[it] },
                name = f.name,
                multiplier = f.multiplier,
                amount = f.amountGram,
                amountUnit = amountUnitOf(f.amountUnit),
                macros = f.macros.toEntity(),
                macroSource = runCatching { MacroSource.valueOf(f.macroSource) }.getOrDefault(MacroSource.CATALOG)
            )
        }
        return entity to restoredFoods
    }

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
