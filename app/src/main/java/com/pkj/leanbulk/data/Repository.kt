package com.pkj.leanbulk.data

import androidx.room.withTransaction
import com.pkj.leanbulk.data.backup.DietTemplateDto
import com.pkj.leanbulk.data.backup.ExerciseDto
import com.pkj.leanbulk.data.backup.RoutineDto
import com.pkj.leanbulk.data.backup.Snapshot
import com.pkj.leanbulk.data.backup.SnapshotCodec
import com.pkj.leanbulk.data.backup.SnapshotCodec.toDto
import com.pkj.leanbulk.data.backup.SnapshotCodec.toEntity
import com.pkj.leanbulk.data.backup.SnapshotCodec.toEntityPair
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

        if (applied < 3) {
            // 음식 카탈로그. 종목과 같은 규칙 — slug 기준이고 사용자 것은 안 건드린다.
            SeedData.foodCatalog.forEach { foodDao.upsertSeed(it.toEntity()) }
        }

        appMetaDao.upsert(AppMeta(seedVersion = SeedData.VERSION))
    }

    suspend fun seedVersion(): Int = appMetaDao.seedVersion() ?: 0

    // ---------- 종목 카탈로그 ----------

    fun observeExercises(): Flow<List<Exercise>> = exerciseDao.observeAll()

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

    suspend fun hideFood(id: Long) = foodDao.setHidden(id, true)

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
                        defaultSetting = ex.defaultSetting
                    ) to List(ex.setCount) { setIndex ->
                        RoutineSet(
                            routineExerciseId = 0,
                            setIndex = setIndex,
                            weightKg = ex.defaultWeight,
                            reps = ex.defaultReps
                        )
                    }
                }
            )
        }
    }

    suspend fun routineNameExists(name: String, exceptId: Long = 0): Boolean =
        routineDao.countByName(name, exceptId) > 0

    suspend fun addRoutine(name: String) = routineDao.insertWithExercises(name, emptyList())

    suspend fun saveAsNewRoutine(
        name: String,
        exercises: List<Pair<RoutineExercise, List<RoutineSet>>>
    ) = routineDao.insertWithExercises(name, exercises)

    suspend fun overwriteRoutine(
        routineId: Long,
        exercises: List<Pair<RoutineExercise, List<RoutineSet>>>
    ) = routineDao.replaceExercises(routineId, exercises)

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
            routineDao.insertExercisesWithSets(id, dto.exercises.map { it.toEntityPair(id, idBySlug) })
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

    /** 선택적 병합 결과. 뭐가 몇 개 들어왔는지 알려주려고 센다. */
    data class MergeResult(
        val routines: Int = 0,
        val dietTemplates: Int = 0,
        val exercises: Int = 0,
        val foods: Int = 0
    )

    /**
     * 백업 파일에서 **고른 것만 지금 기록에 더한다.** 전체 교체(`importSnapshot`)와 달리
     * 기존 데이터를 지우지 않는다 — 남의 루틴 하나를 베껴오려고 내 기록을 날릴 수는 없다.
     *
     * 두 가지를 신경 쓴다:
     *
     * 1. **루틴은 종목 카탈로그를 slug로 참조한다.** 남의 루틴이 내게 없는 종목을 쓰고
     *    있으면 그 종목도 같이 들여와야 이름만 남고 추적 방식이 날아가지 않는다.
     *    딸려 들어오는 종목은 내 시드가 아니므로 `USER`로 넣는다.
     * 2. **이름이 겹치면 덮어쓰지 않고 " (2)"를 붙인다.** 어느 쪽이 맞는지는 사용자만
     *    알기 때문에, 둘 다 남겨두고 고르게 하는 게 안전하다.
     *
     * 일별 기록(식단·운동·신체)은 병합하지 않는다. 남의 하루 기록을 내 기록에 섞는 건
     * 의미가 없고, 같은 날짜를 어떻게 합칠지는 레코드 단위 동기화 문제라 별개다.
     */
    suspend fun mergeFromSnapshot(
        catalog: List<ExerciseDto>,
        routines: List<RoutineDto>,
        dietTemplates: List<DietTemplateDto>,
        exercises: List<ExerciseDto>
    ): MergeResult = db.withTransaction {
        val catalogBySlug = catalog.associateBy { it.slug }

        // 직접 고른 종목 + 고른 루틴이 쓰는 종목을 합쳐서 필요한 것만 들여온다.
        val neededSlugs = buildSet {
            exercises.forEach { add(it.slug) }
            routines.forEach { r -> r.exercises.forEach { it.exerciseSlug?.let(::add) } }
        }

        var addedExercises = 0
        val idBySlug = mutableMapOf<String, Long>()
        neededSlugs.forEach { slug ->
            val mine = exerciseDao.findBySlug(slug)
            if (mine != null) {
                // 이미 있으면 내 것을 쓴다. 남의 파일이 내 카탈로그를 고치게 두지 않는다.
                idBySlug[slug] = mine.id
            } else {
                val dto = catalogBySlug[slug] ?: return@forEach
                idBySlug[slug] = exerciseDao.insert(dto.toEntity().copy(source = ItemSource.USER))
                addedExercises++
            }
        }

        val takenRoutineNames = routineDao.getAll().map { it.routine.name }.toMutableSet()
        var addedRoutines = 0
        routines.forEach { dto ->
            val name = uniqueName(dto.name, takenRoutineNames)
            takenRoutineNames += name
            val id = routineDao.insertRoutine(Routine(name = name, orderIndex = routineDao.nextOrderIndex()))
            routineDao.insertExercisesWithSets(id, dto.exercises.map { it.toEntityPair(id, idBySlug) })
            addedRoutines++
        }

        val takenTemplateNames = dietTemplateDao.getAll().map { it.template.name }.toMutableSet()
        var addedTemplates = 0
        dietTemplates.forEach { dto ->
            val name = uniqueName(dto.name, takenTemplateNames)
            takenTemplateNames += name
            val id = dietTemplateDao.insertTemplate(
                DietTemplate(name = name, orderIndex = dietTemplateDao.nextOrderIndex())
            )
            if (dto.meals.isNotEmpty()) {
                dietTemplateDao.insertMeals(dto.meals.map { it.toEntity(id) })
            }
            addedTemplates++
        }

        MergeResult(
            routines = addedRoutines,
            dietTemplates = addedTemplates,
            exercises = addedExercises
        )
    }

    /** 이름이 겹치면 뒤에 번호를 붙인다. 덮어쓰는 것보다 둘 다 남기는 쪽이 안전하다. */
    private fun uniqueName(base: String, taken: Set<String>): String {
        if (base !in taken) return base
        var n = 2
        while ("$base ($n)" in taken) n++
        return "$base ($n)"
    }
}
