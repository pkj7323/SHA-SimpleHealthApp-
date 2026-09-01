package com.pkj.leanbulk.data

import androidx.room.withTransaction
import com.pkj.leanbulk.data.backup.Snapshot
import com.pkj.leanbulk.data.backup.SnapshotCodec
import com.pkj.leanbulk.data.backup.SnapshotCodec.toDto
import com.pkj.leanbulk.data.backup.SnapshotCodec.toEntity
import com.pkj.leanbulk.data.backup.SnapshotCodec.toEntityPairs
import com.pkj.leanbulk.data.backup.SnapshotCodec.toWorkoutDayDtos
import kotlinx.coroutines.flow.Flow

class Repository(private val db: AppDatabase) {

    private val mealDao = db.mealDao()
    private val workoutDao = db.workoutDao()
    private val routineDao = db.routineDao()
    private val dietTemplateDao = db.dietTemplateDao()
    private val foodDao = db.foodDao()
    private val dietTargetDao = db.dietTargetDao()
    private val bodyRecordDao = db.bodyRecordDao()

    // ---------- 식단 기록 ----------

    fun observeMeals(date: String): Flow<List<MealEntry>> = mealDao.observeByDate(date)

    fun observeAllMeals(): Flow<List<MealEntry>> = mealDao.observeAll()

    suspend fun hasMealRecord(date: String): Boolean = mealDao.countForDate(date) > 0

    suspend fun saveMealDay(date: String, entries: List<MealEntry>) {
        mealDao.replaceDay(date, entries)
        // 매크로를 채운 메뉴는 "내 음식 목록"에 쌓아둔다.
        entries.filter { it.menuText.isNotBlank() && !it.macros.isEmpty }
            .forEach { foodDao.remember(it.menuText.trim(), it.macros) }
    }

    // ---------- 식단표 ----------

    fun observeDietTemplates(): Flow<List<DietTemplateWithMeals>> = dietTemplateDao.observeAll()

    suspend fun ensureDietTemplatesSeeded() {
        if (dietTemplateDao.count() > 0) return
        dietTemplateDao.insertWithMeals(
            name = SeedData.DEFAULT_DIET_TEMPLATE_NAME,
            meals = SeedData.mealTemplates.mapIndexed { index, m ->
                DietTemplateMeal(
                    templateId = 0,
                    orderIndex = index,
                    slotName = m.slotName,
                    menuText = m.menuText,
                    macros = Macros(m.kcal, m.protein, m.carbs, m.fat, m.sugar)
                )
            }
        )
    }

    suspend fun addDietTemplate(name: String) = dietTemplateDao.insertWithMeals(name, emptyList())

    suspend fun renameDietTemplate(id: Long, name: String) = dietTemplateDao.rename(id, name)

    suspend fun deleteDietTemplate(id: Long) = dietTemplateDao.delete(id)

    suspend fun overwriteDietTemplate(id: Long, meals: List<DietTemplateMeal>) =
        dietTemplateDao.replaceMeals(id, meals)

    suspend fun saveAsNewDietTemplate(name: String, meals: List<DietTemplateMeal>) =
        dietTemplateDao.insertWithMeals(name, meals)

    // ---------- 내 음식 목록 ----------

    fun observeFoods(): Flow<List<FoodItem>> = foodDao.observeAll()

    suspend fun deleteFood(id: Long) = foodDao.delete(id)

    // ---------- 섭취 목표 ----------

    fun observeTarget(): Flow<DietTarget?> = dietTargetDao.observe()

    suspend fun ensureTargetSeeded() {
        if (dietTargetDao.count() > 0) return
        dietTargetDao.upsert(
            DietTarget(
                kcal = SeedData.TARGET_KCAL,
                protein = SeedData.TARGET_PROTEIN,
                carbs = SeedData.TARGET_CARBS,
                fat = SeedData.TARGET_FAT,
                sugar = SeedData.TARGET_SUGAR
            )
        )
    }

    suspend fun saveTarget(target: DietTarget) = dietTargetDao.upsert(target)

    fun observeWorkout(date: String): Flow<List<ExerciseWithSets>> = workoutDao.observeByDate(date)

    fun observeAllWorkouts(): Flow<List<ExerciseWithSets>> = workoutDao.observeAll()

    suspend fun hasWorkoutRecord(date: String): Boolean = workoutDao.countForDate(date) > 0

    suspend fun saveWorkoutDay(date: String, exercises: List<Pair<WorkoutExercise, List<WorkoutSet>>>) =
        workoutDao.replaceDay(date, exercises)

    // ---------- 루틴 ----------

    fun observeRoutines(): Flow<List<RoutineWithExercises>> = routineDao.observeAll()

    /** 최초 실행 시 기본 4분할 루틴을 넣어둔다. 이후엔 사용자가 자유롭게 수정한다. */
    suspend fun ensureRoutinesSeeded() {
        if (routineDao.count() > 0) return
        SeedData.routines.forEach { template ->
            routineDao.insertWithExercises(
                name = template.name,
                exercises = template.exercises.mapIndexed { index, ex ->
                    RoutineExercise(
                        routineId = 0,
                        orderIndex = index,
                        exerciseName = ex.exerciseName,
                        defaultSetting = ex.defaultSetting,
                        defaultWeight = ex.defaultWeight,
                        defaultReps = ex.defaultReps
                    )
                }
            )
        }
    }

    suspend fun addRoutine(name: String) = routineDao.insertWithExercises(name, emptyList())

    suspend fun renameRoutine(routineId: Long, name: String) = routineDao.rename(routineId, name)

    suspend fun deleteRoutine(routineId: Long) = routineDao.delete(routineId)

    fun observeBodyRecords(): Flow<List<BodyRecord>> = bodyRecordDao.observeAll()

    suspend fun upsertBodyRecord(record: BodyRecord) = bodyRecordDao.upsert(record)

    suspend fun deleteBodyRecord(id: Long) = bodyRecordDao.delete(id)

    // ---------- 백업 스냅샷 ----------

    /** 지금 DB 전체를 스냅샷 하나로 뜬다. 백업 파일과 동기화가 같은 포맷을 쓴다. */
    suspend fun exportSnapshot(exportedAt: String): Snapshot = db.withTransaction {
        Snapshot(
            exportedAt = exportedAt,
            meals = mealDao.getAll().map { it.toDto() },
            workoutDays = workoutDao.getAll().toWorkoutDayDtos(),
            routines = routineDao.getAll().map { it.toDto() },
            dietTemplates = dietTemplateDao.getAll().map { it.toDto() },
            foods = foodDao.getAll().map { it.toDto() },
            bodyRecords = bodyRecordDao.getAll().map { it.toDto() },
            target = dietTargetDao.get()?.toDto()
        )
    }

    /**
     * 스냅샷으로 DB를 통째로 교체한다.
     *
     * 트랜잭션 하나로 묶는 게 중요하다 — 중간에 실패해서 반쯤 덮어쓴 상태가 제일 나쁘다.
     * 순서·orderIndex를 그대로 살리려고 `insertWithExercises` 같은 헬퍼 대신 하위 DAO를
     * 직접 조합한다(헬퍼는 orderIndex를 새로 매긴다).
     */
    suspend fun importSnapshot(snapshot: Snapshot) = db.withTransaction {
        mealDao.deleteAll()
        workoutDao.deleteAll()
        routineDao.deleteAll()
        dietTemplateDao.deleteAll()
        foodDao.deleteAll()
        bodyRecordDao.deleteAll()

        mealDao.insertAll(snapshot.meals.map { it.toEntity() })

        snapshot.workoutDays.forEach { day ->
            workoutDao.replaceDay(day.date, day.toEntityPairs())
        }

        snapshot.routines.forEach { dto ->
            val id = routineDao.insertRoutine(Routine(name = dto.name, orderIndex = dto.orderIndex))
            if (dto.exercises.isNotEmpty()) {
                routineDao.insertExercises(dto.exercises.map { it.toEntity(id) })
            }
        }

        snapshot.dietTemplates.forEach { dto ->
            val id = dietTemplateDao.insertTemplate(DietTemplate(name = dto.name, orderIndex = dto.orderIndex))
            if (dto.meals.isNotEmpty()) {
                dietTemplateDao.insertMeals(dto.meals.map { it.toEntity(id) })
            }
        }

        snapshot.foods.forEach { foodDao.insert(it.toEntity()) }
        snapshot.bodyRecords.forEach { bodyRecordDao.upsert(it.toEntity()) }
        snapshot.target?.let { dietTargetDao.upsert(it.toEntity()) }
    }
}
