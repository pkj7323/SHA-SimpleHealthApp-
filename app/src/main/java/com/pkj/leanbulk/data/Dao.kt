package com.pkj.leanbulk.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MealDao {
    @Query("SELECT * FROM meal_entry WHERE date = :date ORDER BY orderIndex")
    fun observeByDate(date: String): Flow<List<MealEntry>>

    @Query("SELECT * FROM meal_entry ORDER BY date DESC, orderIndex")
    fun observeAll(): Flow<List<MealEntry>>

    /** 백업 내보내기용 일회성 조회. 화면 갱신이 아니라 스냅샷을 뜨는 용도다. */
    @Query("SELECT * FROM meal_entry ORDER BY date, orderIndex")
    suspend fun getAll(): List<MealEntry>

    @Query("SELECT COUNT(*) FROM meal_entry WHERE date = :date")
    suspend fun countForDate(date: String): Int

    @Query("DELETE FROM meal_entry WHERE date = :date")
    suspend fun deleteByDate(date: String)

    @Query("DELETE FROM meal_entry")
    suspend fun deleteAll()

    @Insert
    suspend fun insertAll(entries: List<MealEntry>)

    /** 해당 날짜의 식단 기록을 통째로 새로 저장한다(덮어쓰기). */
    @Transaction
    suspend fun replaceDay(date: String, entries: List<MealEntry>) {
        deleteByDate(date)
        insertAll(entries.map { it.copy(id = 0, date = date) })
    }
}

@Dao
interface DietTemplateDao {

    @Transaction
    @Query("SELECT * FROM diet_template ORDER BY orderIndex")
    fun observeAll(): Flow<List<DietTemplateWithMeals>>

    /** 백업 내보내기용 일회성 조회. */
    @Transaction
    @Query("SELECT * FROM diet_template ORDER BY orderIndex")
    suspend fun getAll(): List<DietTemplateWithMeals>

    @Query("SELECT COUNT(*) FROM diet_template")
    suspend fun count(): Int

    /** 자식(diet_template_meal)은 외래키 CASCADE로 같이 지워진다. */
    @Query("DELETE FROM diet_template")
    suspend fun deleteAll()

    @Query("SELECT COALESCE(MAX(orderIndex), -1) + 1 FROM diet_template")
    suspend fun nextOrderIndex(): Int

    @Insert
    suspend fun insertTemplate(template: DietTemplate): Long

    @Insert
    suspend fun insertMeals(meals: List<DietTemplateMeal>)

    @Query("UPDATE diet_template SET name = :name WHERE id = :templateId")
    suspend fun rename(templateId: Long, name: String)

    @Query("DELETE FROM diet_template WHERE id = :templateId")
    suspend fun delete(templateId: Long)

    @Query("DELETE FROM diet_template_meal WHERE templateId = :templateId")
    suspend fun deleteMeals(templateId: Long)

    @Transaction
    suspend fun insertWithMeals(name: String, meals: List<DietTemplateMeal>) {
        val id = insertTemplate(DietTemplate(name = name, orderIndex = nextOrderIndex()))
        if (meals.isNotEmpty()) insertMeals(meals.map { it.copy(id = 0, templateId = id) })
    }

    /** 지금 화면의 끼니 구성을 기존 식단표에 덮어써서 저장한다. */
    @Transaction
    suspend fun replaceMeals(templateId: Long, meals: List<DietTemplateMeal>) {
        deleteMeals(templateId)
        if (meals.isNotEmpty()) insertMeals(meals.map { it.copy(id = 0, templateId = templateId) })
    }
}

@Dao
interface FoodDao {
    /** 음식 선택 목록. 숨긴 건 빼고, 자주 쓴 것부터 보여준다. */
    @Query("SELECT * FROM food_item WHERE hidden = 0 ORDER BY usedCount DESC, name")
    fun observeAll(): Flow<List<FoodItem>>

    /** 백업 내보내기용 일회성 조회. */
    @Query("SELECT * FROM food_item ORDER BY name")
    suspend fun getAll(): List<FoodItem>

    @Query("SELECT * FROM food_item WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): FoodItem?

    @Query("SELECT * FROM food_item WHERE slug = :slug LIMIT 1")
    suspend fun findBySlug(slug: String): FoodItem?

    @Query("UPDATE food_item SET hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: Long, hidden: Boolean)

    @Query("DELETE FROM food_item")
    suspend fun deleteAll()

    /**
     * 시드 음식을 slug 기준으로 넣거나 갱신한다. [ExerciseDao.upsertSeed]와 같은 규칙이다.
     *
     * 음식엔 함정이 하나 더 있다 — **이름도 유니크**라서, 사용자가 이미 같은 이름으로
     * 쌓아둔 음식이 있으면 slug가 달라도 삽입이 실패한다. 그런 경우는 사용자 것을
     * 그대로 두고 시드를 건너뛴다(직접 적어둔 영양정보가 더 정확할 수 있다).
     */
    @Transaction
    suspend fun upsertSeed(seed: FoodItem) {
        val bySlug = findBySlug(seed.slug)
        if (bySlug != null) {
            if (bySlug.source == ItemSource.SEED) {
                update(bySlug.copy(name = seed.name, category = seed.category, macros = seed.macros))
            }
            return
        }
        if (findByName(seed.name) != null) return
        insert(seed)
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: FoodItem)

    @Update
    suspend fun update(item: FoodItem)

    @Query("DELETE FROM food_item WHERE id = :id")
    suspend fun delete(id: Long)

    /**
     * 같은 이름이 있으면 매크로를 갱신하고 사용 횟수를 올린다.
     * 없으면 새로 쌓는다 — 이때 slug를 붙여줘야 유니크 인덱스에 걸리지 않는다.
     */
    @Transaction
    suspend fun remember(name: String, macros: Macros) {
        val existing = findByName(name)
        if (existing == null) {
            insert(
                FoodItem(
                    slug = "user-${System.currentTimeMillis()}-${name.hashCode()}",
                    name = name,
                    macros = macros,
                    source = ItemSource.USER
                )
            )
        } else {
            update(existing.copy(macros = macros, usedCount = existing.usedCount + 1))
        }
    }
}

@Dao
interface DietTargetDao {
    @Query("SELECT * FROM diet_target WHERE id = 1")
    fun observe(): Flow<DietTarget?>

    /** 백업 내보내기용 일회성 조회. */
    @Query("SELECT * FROM diet_target WHERE id = 1")
    suspend fun get(): DietTarget?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(target: DietTarget)

    @Query("SELECT COUNT(*) FROM diet_target")
    suspend fun count(): Int
}

@Dao
interface ExerciseDao {

    /** 종목 선택 목록용. */
    @Query("SELECT * FROM exercise ORDER BY bodyPart, name")
    fun observeAll(): Flow<List<Exercise>>

    /** 백업 내보내기용. */
    @Query("SELECT * FROM exercise ORDER BY slug")
    suspend fun getAll(): List<Exercise>

    @Query("SELECT * FROM exercise WHERE slug = :slug LIMIT 1")
    suspend fun findBySlug(slug: String): Exercise?

    @Query("SELECT COUNT(*) FROM exercise")
    suspend fun count(): Int

    @Insert
    suspend fun insert(exercise: Exercise): Long

    @Update
    suspend fun update(exercise: Exercise)

    @Query("DELETE FROM exercise")
    suspend fun deleteAll()

    /**
     * 시드 종목을 slug 기준으로 넣거나 갱신한다.
     *
     * SQLite UPSERT(`ON CONFLICT ... DO UPDATE`)는 3.24+가 필요한데 minSdk 26은 3.19라
     * 못 쓴다. 그래서 조회 후 분기한다.
     *
     * 지키는 것: 사용자가 직접 만든 항목(`source = USER`)은 slug가 겹쳐도 **덮어쓰지 않는다**.
     */
    @Transaction
    suspend fun upsertSeed(seed: Exercise) {
        val existing = findBySlug(seed.slug)
        when {
            existing == null -> insert(seed)
            existing.source == ItemSource.SEED -> update(
                existing.copy(
                    name = seed.name,
                    bodyPart = seed.bodyPart,
                    equipment = seed.equipment,
                    trackingType = seed.trackingType
                )
            )
            // USER 항목이면 손대지 않는다.
        }
    }

    /**
     * 이름이 정확히 같은 카탈로그 종목에 기존 루틴을 연결한다.
     *
     * 시드 종목이 기존 시드 루틴의 이름을 그대로 쓰기 때문에 이 완전일치가 성립한다.
     * 못 찾으면 `exerciseId`는 null로 남고 추적 방식도 원래 값을 유지한다.
     */
    @Query(
        """
        UPDATE routine_exercise SET
            exerciseId = (SELECT e.id FROM exercise e WHERE e.name = routine_exercise.exerciseName),
            trackingType = COALESCE(
                (SELECT e.trackingType FROM exercise e WHERE e.name = routine_exercise.exerciseName),
                trackingType
            )
        WHERE exerciseId IS NULL
        """
    )
    suspend fun linkRoutineExercises()

    /** 과거 운동 기록도 같은 방식으로 연결한다. */
    @Query(
        """
        UPDATE workout_exercise SET
            exerciseId = (SELECT e.id FROM exercise e WHERE e.name = workout_exercise.exerciseName),
            trackingType = COALESCE(
                (SELECT e.trackingType FROM exercise e WHERE e.name = workout_exercise.exerciseName),
                trackingType
            )
        WHERE exerciseId IS NULL
        """
    )
    suspend fun linkWorkoutExercises()
}

@Dao
interface AppMetaDao {
    @Query("SELECT seedVersion FROM app_meta WHERE id = 1")
    suspend fun seedVersion(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meta: AppMeta)
}

@Dao
interface WorkoutDao {

    @Transaction
    @Query("SELECT * FROM workout_exercise WHERE date = :date ORDER BY orderIndex")
    fun observeByDate(date: String): Flow<List<ExerciseWithSets>>

    @Transaction
    @Query("SELECT * FROM workout_exercise ORDER BY date DESC, orderIndex")
    fun observeAll(): Flow<List<ExerciseWithSets>>

    /** 백업 내보내기용 일회성 조회. */
    @Transaction
    @Query("SELECT * FROM workout_exercise ORDER BY date, orderIndex")
    suspend fun getAll(): List<ExerciseWithSets>

    @Query("SELECT COUNT(*) FROM workout_exercise WHERE date = :date")
    suspend fun countForDate(date: String): Int

    @Query("DELETE FROM workout_exercise WHERE date = :date")
    suspend fun deleteByDate(date: String)

    /** 자식(workout_set)은 외래키 CASCADE로 같이 지워진다. */
    @Query("DELETE FROM workout_exercise")
    suspend fun deleteAll()

    @Insert
    suspend fun insertExercise(exercise: WorkoutExercise): Long

    @Insert
    suspend fun insertSets(sets: List<WorkoutSet>)

    /** 해당 날짜의 기존 기록을 지우고 새로 저장한다(덮어쓰기). */
    @Transaction
    suspend fun replaceDay(date: String, exercises: List<Pair<WorkoutExercise, List<WorkoutSet>>>) {
        deleteByDate(date)
        exercises.forEach { (exercise, sets) ->
            val newId = insertExercise(exercise.copy(id = 0, date = date))
            insertSets(sets.map { it.copy(id = 0, exerciseId = newId) })
        }
    }
}

@Dao
interface RoutineDao {

    @Transaction
    @Query("SELECT * FROM routine ORDER BY orderIndex")
    fun observeAll(): Flow<List<RoutineWithExercises>>

    /** 백업 내보내기용 일회성 조회. */
    @Transaction
    @Query("SELECT * FROM routine ORDER BY orderIndex")
    suspend fun getAll(): List<RoutineWithExercises>

    @Query("SELECT COUNT(*) FROM routine")
    suspend fun count(): Int

    /**
     * 같은 이름의 다른 루틴이 있는지. 이름은 사용자가 루틴을 알아보는 유일한 단서라
     * 겹치면 목록에서 어느 쪽인지 구분할 방법이 없다.
     *
     * [exceptId]는 이름 수정 때 자기 자신을 빼기 위한 것이다.
     */
    @Query("SELECT COUNT(*) FROM routine WHERE name = :name AND id != :exceptId")
    suspend fun countByName(name: String, exceptId: Long = 0): Int

    /** 자식(routine_exercise)은 외래키 CASCADE로 같이 지워진다. */
    @Query("DELETE FROM routine")
    suspend fun deleteAll()

    @Query("SELECT COALESCE(MAX(orderIndex), -1) + 1 FROM routine")
    suspend fun nextOrderIndex(): Int

    @Insert
    suspend fun insertRoutine(routine: Routine): Long

    @Insert
    suspend fun insertExercises(exercises: List<RoutineExercise>)

    @Insert
    suspend fun insertExercise(exercise: RoutineExercise): Long

    @Insert
    suspend fun insertSets(sets: List<RoutineSet>)

    /**
     * 종목과 그 세트를 함께 넣는다. 세트는 부모 id를 알아야 하므로 종목을 하나씩 넣고
     * 돌려받은 id를 붙인다.
     */
    @Transaction
    suspend fun insertExercisesWithSets(
        routineId: Long,
        exercises: List<Pair<RoutineExercise, List<RoutineSet>>>
    ) {
        exercises.forEach { (exercise, sets) ->
            val id = insertExercise(exercise.copy(id = 0, routineId = routineId))
            if (sets.isNotEmpty()) {
                insertSets(sets.map { it.copy(id = 0, routineExerciseId = id) })
            }
        }
    }

    @Query("UPDATE routine SET name = :name WHERE id = :routineId")
    suspend fun rename(routineId: Long, name: String)

    @Query("DELETE FROM routine WHERE id = :routineId")
    suspend fun delete(routineId: Long)

    @Transaction
    suspend fun insertWithExercises(
        name: String,
        exercises: List<Pair<RoutineExercise, List<RoutineSet>>>
    ) {
        val id = insertRoutine(Routine(name = name, orderIndex = nextOrderIndex()))
        insertExercisesWithSets(id, exercises)
    }

    /** 세트(`routine_set`)는 외래키 CASCADE로 같이 지워진다. */
    @Query("DELETE FROM routine_exercise WHERE routineId = :routineId")
    suspend fun deleteExercises(routineId: Long)

    /** 지금 화면의 종목 구성을 기존 루틴에 덮어써서 저장한다. */
    @Transaction
    suspend fun replaceExercises(
        routineId: Long,
        exercises: List<Pair<RoutineExercise, List<RoutineSet>>>
    ) {
        deleteExercises(routineId)
        insertExercisesWithSets(routineId, exercises)
    }
}

@Dao
interface BodyRecordDao {
    @Query("SELECT * FROM body_record ORDER BY date DESC")
    fun observeAll(): Flow<List<BodyRecord>>

    /** 백업 내보내기용 일회성 조회. */
    @Query("SELECT * FROM body_record ORDER BY date")
    suspend fun getAll(): List<BodyRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: BodyRecord)

    @Query("DELETE FROM body_record WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM body_record")
    suspend fun deleteAll()
}
