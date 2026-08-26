package com.pkj.leanbulk.data

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
}
