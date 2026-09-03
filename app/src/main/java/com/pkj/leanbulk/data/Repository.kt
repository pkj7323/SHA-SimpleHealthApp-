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
    private val appMetaDao = db.appMetaDao()
    private val exerciseDao = db.exerciseDao()

    // ---------- 시드 ----------

    /**
     * 앱에 내장된 시드를 DB에 반영한다. 앱 시작 시 한 번 호출한다.
     *
     * 예전에는 `ensureXxxSeeded()`가 "테이블이 비어 있으면 넣기" 방식이라 **최초 1회만**
     * 동작했다. 그래서 앱을 업데이트해 시드를 늘려도 기존 사용자에겐 영영 반영되지 않았다.
     * 이제 적용된 시드 버전을 DB에 기록해두고, 내장 버전([SeedData.VERSION])이 더 높을 때만
     * 밀린 단계를 돌린다.
     *
     * **새 시드를 추가할 때**: [SeedData.VERSION]을 올리고 아래에 그 버전의 단계를 추가한다.
     */
    suspend fun applySeeds() {
        val applied = seedVersion()
        if (applied >= SeedData.VERSION) return

        if (applied < 1) {
            // 루틴·식단표·목표는 **사용자가 고치는 콘텐츠**라 첫 설치에만 깐다.
            // 재적용하면 사용자가 이름을 바꾸거나 종목을 뺀 걸 덮어쓰거나 중복이 생긴다.
            ensureRoutinesSeeded()
            ensureDietTemplatesSeeded()
            ensureTargetSeeded()
        }

        if (applied < 2) {
            // 종목 카탈로그. slug 기준이라 여러 번 돌아도 중복되지 않고,
            // 사용자가 직접 추가한 항목(source = USER)과 숨김 상태는 건드리지 않는다.
            SeedData.exerciseCatalog.forEach { exerciseDao.upsertSeed(it.toEntity()) }

            // 이름이 같은 카탈로그 종목에 기존 루틴·기록을 이어 붙인다.
            // 시드 종목이 기존 루틴의 이름을 그대로 쓰기 때문에 완전일치가 성립한다.
            exerciseDao.linkRoutineExercises()
            exerciseDao.linkWorkoutExercises()
        }

        appMetaDao.upsert(AppMeta(seedVersion = SeedData.VERSION))
    }

    suspend fun seedVersion(): Int = appMetaDao.seedVersion() ?: 0

    // ---------- 종목 카탈로그 ----------

    fun observeExercises(): Flow<List<Exercise>> = exerciseDao.observeVisible()

    /** 카탈로그에 없는 종목을 사용자가 직접 등록한다. 이후 검색 목록에 계속 남는다. */
    suspend fun addUserExercise(
        name: String,
        bodyPart: String,
        equipment: String,
        trackingType: TrackingType
    ): Exercise {
        val entity = Exercise(
            // 사용자 항목은 slug가 겹치지 않게 접두어를 붙인다. 나중에 같은 이름의 시드가
            // 들어와도 사용자 것이 덮어써지지 않는다.
            slug = "user-" + name.trim().lowercase().replace(Regex("\\s+"), "-") + "-" + System.currentTimeMillis(),
            name = name.trim(),
            bodyPart = bodyPart,
            equipment = equipment,
            trackingType = trackingType,
            source = ItemSource.USER
        )
        return entity.copy(id = exerciseDao.insert(entity))
    }

    suspend fun hideExercise(id: Long) = exerciseDao.setHidden(id, true)

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
        val catalog = exerciseDao.getAll()
        // 기록이 들고 있는 로컬 id를 기기 간에 통하는 slug로 바꿔서 내보낸다.
        val slugById = catalog.associate { it.id to it.slug }

        Snapshot(
            exportedAt = exportedAt,
            seedVersion = seedVersion(),
            exercises = catalog.map { it.toDto() },
            meals = mealDao.getAll().map { it.toDto() },
            workoutDays = workoutDao.getAll().toWorkoutDayDtos(slugById),
            routines = routineDao.getAll().map { it.toDto(slugById) },
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
        exerciseDao.deleteAll()

        // 카탈로그를 먼저 넣어야 기록·루틴이 slug로 이어 붙을 수 있다.
        val idBySlug = snapshot.exercises.associate { dto ->
            dto.slug to exerciseDao.insert(dto.toEntity())
        }

        mealDao.insertAll(snapshot.meals.map { it.toEntity() })

        snapshot.workoutDays.forEach { day ->
            workoutDao.replaceDay(day.date, day.toEntityPairs(idBySlug))
        }

        snapshot.routines.forEach { dto ->
            val id = routineDao.insertRoutine(Routine(name = dto.name, orderIndex = dto.orderIndex))
            if (dto.exercises.isNotEmpty()) {
                routineDao.insertExercises(dto.exercises.map { it.toEntity(id, idBySlug) })
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

        // 시드 버전도 같이 넘어와야, 옛 백업을 불러왔을 때 다음 실행에서 밀린 시드가 채워진다.
        appMetaDao.upsert(AppMeta(seedVersion = snapshot.seedVersion))
    }
}
