package com.pkj.leanbulk.data

import androidx.room.withTransaction
import com.pkj.leanbulk.data.backup.DietTemplateDto
import com.pkj.leanbulk.data.backup.ExerciseDto
import com.pkj.leanbulk.data.backup.FoodDto
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
    private val mealPresetDao = db.mealPresetDao()

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

        if (applied < 4) {
            // 음식 이름에서 제공량을 떼고 기준량으로 옮겼다(DIET-REDESIGN.md).
            // upsertSeed가 멱등이라 다시 돌려도 기존 SEED 행의 이름·기준량만 갱신된다.
            SeedData.foodCatalog.forEach { foodDao.upsertSeed(it.toEntity()) }
        }

        if (applied < 5) {
            // 기준량에 단위가 생겼다(g/ml/L). 우유·저지방우유·이온음료는 원래 ml인데
            // g로 들어가 있어서 바로잡는다. 사용자가 고친 행(source = USER)은 안 건드린다.
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

    fun observeMealsWithFoods(date: String): Flow<List<MealEntryWithFoods>> =
        mealDao.observeByDateWithFoods(date)

    fun observeAllMeals(): Flow<List<MealEntry>> = mealDao.observeAll()

    suspend fun hasMealRecord(date: String): Boolean = mealDao.countForDate(date) > 0

    /**
     * 하루치 식단을 저장한다.
     *
     * 예전에는 자유 텍스트로 적은 끼니를 통째로 "내 음식 목록"에 기억시켰는데
     * (`"잡곡밥 150g + 소고기 150g + 야채"` 같은 것이 음식 하나로 쌓였다), 끼니를 음식 줄로만
     * 채우게 되면서 그 경로를 없앴다 — 한 끼니 전체는 음식이 아니라 조합이고, 조합은
     * "내 식사"([saveAsMealPreset])가 담당한다.
     */
    suspend fun saveMealDay(date: String, entries: List<Pair<MealEntry, List<MealFood>>>) =
        mealDao.replaceDayWithFoods(date, entries)

    // ---------- 식단표 ----------
    // 화면에서는 뺐다(하루 전체 틀 → 끼니 단위 "내 식사"로 통합). 저장된 데이터와 백업 경로는
    // 그대로 두므로 예전에 만들어둔 식단표가 백업 파일에서 사라지지는 않는다.

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

    // ---------- 내 음식 목록 ----------

    fun observeFoods(): Flow<List<FoodItem>> = foodDao.observeAll()

    /**
     * 고른 음식을 목록에서 지운다. 시드로 깔린 것도 똑같이 지운다.
     *
     * 이미 기록된 끼니는 이름·영양정보를 그 순간 복사해 두므로([MealFood]) 과거 기록이
     * 망가지지 않는다. 다만 **시드 음식은 다음 시드 갱신 때 slug로 다시 들어올 수 있다** —
     * 그때 또 지우면 된다. 그걸 막으려고 "숨김" 표시를 남겨뒀더니, 안 보이는 행이 유니크한
     * 이름을 붙들고 있어서 같은 이름을 다시 쓸 수 없는 상태에 갇히는 게 더 나빴다.
     */
    suspend fun deleteFoods(ids: List<Long>) {
        if (ids.isNotEmpty()) foodDao.delete(ids)
    }

    suspend fun setFoodFavorite(id: Long, favorite: Boolean) = foodDao.setFavorite(id, favorite)

    /** "직접 등록" — 검색해도 없는 새 음식을 카탈로그에 바로 추가한다. 종목의 [addUserExercise]와 같은 자리. */
    suspend fun addUserFood(
        name: String,
        category: String,
        macros: Macros,
        baseAmount: Double?,
        baseAmountUnit: AmountUnit
    ): FoodItem {
        val entity = FoodItem(
            slug = "user-" + name.trim().lowercase().replace(Regex("\\s+"), "-") + "-" + System.currentTimeMillis(),
            name = name.trim(),
            category = category,
            macros = macros,
            source = ItemSource.USER,
            baseAmount = baseAmount,
            baseAmountUnit = baseAmountUnit
        )
        return entity.copy(id = foodDao.insertReturningId(entity))
    }

    /**
     * 음식을 고친다. **이름이 겹쳐도 그대로 저장한다** — 같은 이름의 음식을 여러 개 두는 건
     * 사용자 자유이고(닭가슴살 두 종류처럼), 기기 간 정체성은 slug가 맡는다.
     *
     * 시드 음식을 고치면 그 행을 `USER`로 돌린다. 안 그러면 다음 시드 갱신이 사용자가
     * 고친 값을 그대로 덮어써서, 고쳐도 소용없는 것처럼 보인다(`FoodDao.upsertSeed`).
     */
    suspend fun updateFood(food: FoodItem) =
        foodDao.update(food.copy(name = food.name.trim(), source = ItemSource.USER))

    // ---------- 내 식사(식사세트) ----------

    fun observeMealPresets(): Flow<List<MealPresetWithFoods>> = mealPresetDao.observeAll()

    suspend fun saveAsMealPreset(name: String, foods: List<MealPresetFood>) =
        mealPresetDao.insertWithFoods(name, foods)

    suspend fun renameMealPreset(id: Long, name: String) = mealPresetDao.rename(id, name)

    suspend fun deleteMealPreset(id: Long) = mealPresetDao.delete(id)

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

    /**
     * 편집한 구성을 같은 이름의 루틴에 자동으로 반영한다. 반영했으면 `true`.
     *
     * 자동으로 도는 덮어쓰기라 **안 되는 경우를 넓게 잡았다**. 되돌릴 수단이 없기 때문이다:
     * - 이름이 비었거나 그 이름의 루틴이 없으면(지웠거나 이름을 바꿨다) 아무것도 안 한다
     * - **종목이 하나도 없으면 반영하지 않는다.** 다시 짜려고 전부 지운 순간 루틴까지
     *   빈 껍데기가 되면 복구할 방법이 없다. 루틴을 비우고 싶으면 [overwriteRoutine]으로
     *   직접 해야 한다
     */
    suspend fun syncRoutineByName(
        name: String,
        exercises: List<Pair<RoutineExercise, List<RoutineSet>>>
    ): Boolean {
        if (name.isBlank() || exercises.isEmpty()) return false
        val id = routineDao.findIdByName(name) ?: return false
        routineDao.replaceExercises(id, exercises)
        return true
    }

    suspend fun renameRoutine(routineId: Long, name: String) = routineDao.rename(routineId, name)

    suspend fun deleteRoutine(routineId: Long) = routineDao.delete(routineId)

    fun observeBodyRecords(): Flow<List<BodyRecord>> = bodyRecordDao.observeAll()

    suspend fun upsertBodyRecord(record: BodyRecord) = bodyRecordDao.upsert(record)

    suspend fun deleteBodyRecord(id: Long) = bodyRecordDao.delete(id)

    // ---------- 백업 스냅샷 ----------

    /**
     * **직접 등록한 음식만** 스냅샷 하나에 담는다(ROADMAP 3번 "시드 승격").
     *
     * 시드로 깔린 음식은 뺀다 — 이미 앱에 들어 있으니 다시 내보낼 이유가 없고, 이 파일의
     * 쓸모가 "다음 버전 시드에 넣을 후보"이기 때문이다.
     *
     * 포맷은 일반 백업과 같은 [Snapshot]이다. 따로 만들면 읽는 코드가 두 벌이 되고,
     * 이 파일 그대로 "골라서 가져오기"에 넣을 수도 있다.
     */
    suspend fun exportUserFoods(exportedAt: String): Snapshot = Snapshot(
        exportedAt = exportedAt,
        seedVersion = seedVersion(),
        foods = foodDao.getAll().filter { it.source == ItemSource.USER }.map { it.toDto() }
    )

    /** 지금 DB 전체를 스냅샷 하나로 뜬다. 백업 파일과 동기화가 같은 포맷을 쓴다. */
    suspend fun exportSnapshot(exportedAt: String): Snapshot = db.withTransaction {
        val catalog = exerciseDao.getAll()
        // 기록이 들고 있는 로컬 id를 기기 간에 통하는 slug로 바꿔서 내보낸다.
        val slugById = catalog.associate { it.id to it.slug }
        val foodSlugById = foodDao.getAll().associate { it.id to it.slug }

        Snapshot(
            exportedAt = exportedAt,
            seedVersion = seedVersion(),
            exercises = catalog.map { it.toDto() },
            meals = mealDao.getAllWithFoods().map { it.toDto(foodSlugById) },
            workoutDays = workoutDao.getAll().toWorkoutDayDtos(slugById),
            routines = routineDao.getAll().map { it.toDto(slugById) },
            dietTemplates = dietTemplateDao.getAll().map { it.toDto() },
            foods = foodDao.getAll().map { it.toDto() },
            bodyRecords = bodyRecordDao.getAll().map { it.toDto() },
            target = dietTargetDao.get()?.toDto(),
            mealPresets = mealPresetDao.getAll().map { it.toDto(foodSlugById) }
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
        mealPresetDao.deleteAll()

        // 카탈로그를 먼저 넣어야 기록·루틴이 slug로 이어 붙을 수 있다.
        val idBySlug = snapshot.exercises.associate { dto ->
            dto.slug to exerciseDao.insert(dto.toEntity())
        }
        // 음식도 마찬가지 — meal_food가 slug로 이 카탈로그를 참조하므로 끼니보다 먼저 넣는다.
        val foodIdBySlug = mutableMapOf<String, Long>()
        snapshot.foods.forEach { dto ->
            val entity = dto.toEntity()
            foodDao.insert(entity)
            foodDao.findBySlug(entity.slug)?.let { foodIdBySlug[entity.slug] = it.id }
        }

        snapshot.meals.groupBy { it.date }.forEach { (date, dtos) ->
            mealDao.replaceDayWithFoods(date, dtos.map { it.toEntityPair(foodIdBySlug) })
        }

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

        snapshot.bodyRecords.forEach { bodyRecordDao.upsert(it.toEntity()) }
        snapshot.target?.let { dietTargetDao.upsert(it.toEntity()) }

        snapshot.mealPresets.forEach { dto ->
            val (preset, foods) = dto.toEntityPair(foodIdBySlug)
            val id = mealPresetDao.insertPreset(preset)
            if (foods.isNotEmpty()) mealPresetDao.insertFoods(foods.map { it.copy(presetId = id) })
        }

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
        exercises: List<ExerciseDto>,
        foods: List<FoodDto> = emptyList()
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

        // 음식은 slug로 대조한다. 이미 있으면 건너뛴다 — 남의 파일이 내가 고쳐둔
        // 영양정보를 덮어쓰면 안 된다. 이름이 겹치는 건 그대로 둔다(이름은 유니크가 아니다).
        var addedFoods = 0
        foods.forEach { dto ->
            if (foodDao.findBySlug(dto.slug) != null) return@forEach
            foodDao.insert(dto.toEntity().copy(source = ItemSource.USER))
            addedFoods++
        }

        MergeResult(
            routines = addedRoutines,
            dietTemplates = addedTemplates,
            exercises = addedExercises,
            foods = addedFoods
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
