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

    @Query("SELECT COUNT(*) FROM meal_entry WHERE date = :date")
    suspend fun countForDate(date: String): Int

    @Query("DELETE FROM meal_entry WHERE date = :date")
    suspend fun deleteByDate(date: String)

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

    @Query("SELECT COUNT(*) FROM diet_template")
    suspend fun count(): Int

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
    @Query("SELECT * FROM food_item ORDER BY usedCount DESC, name")
    fun observeAll(): Flow<List<FoodItem>>

    @Query("SELECT * FROM food_item WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): FoodItem?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: FoodItem)

    @Update
    suspend fun update(item: FoodItem)

    @Query("DELETE FROM food_item WHERE id = :id")
    suspend fun delete(id: Long)

    /** 같은 이름이 있으면 매크로를 갱신하고 사용 횟수를 올린다. */
    @Transaction
    suspend fun remember(name: String, macros: Macros) {
        val existing = findByName(name)
        if (existing == null) {
            insert(FoodItem(name = name, macros = macros))
        } else {
            update(existing.copy(macros = macros, usedCount = existing.usedCount + 1))
        }
    }
}

@Dao
interface DietTargetDao {
    @Query("SELECT * FROM diet_target WHERE id = 1")
    fun observe(): Flow<DietTarget?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(target: DietTarget)

    @Query("SELECT COUNT(*) FROM diet_target")
    suspend fun count(): Int
}

@Dao
interface WorkoutDao {

    @Transaction
    @Query("SELECT * FROM workout_exercise WHERE date = :date ORDER BY orderIndex")
    fun observeByDate(date: String): Flow<List<ExerciseWithSets>>

    @Transaction
    @Query("SELECT * FROM workout_exercise ORDER BY date DESC, orderIndex")
    fun observeAll(): Flow<List<ExerciseWithSets>>

    @Query("SELECT COUNT(*) FROM workout_exercise WHERE date = :date")
    suspend fun countForDate(date: String): Int

    @Query("DELETE FROM workout_exercise WHERE date = :date")
    suspend fun deleteByDate(date: String)

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

    @Query("SELECT COUNT(*) FROM routine")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(orderIndex), -1) + 1 FROM routine")
    suspend fun nextOrderIndex(): Int

    @Insert
    suspend fun insertRoutine(routine: Routine): Long

    @Insert
    suspend fun insertExercises(exercises: List<RoutineExercise>)

    @Query("UPDATE routine SET name = :name WHERE id = :routineId")
    suspend fun rename(routineId: Long, name: String)

    @Query("DELETE FROM routine WHERE id = :routineId")
    suspend fun delete(routineId: Long)

    @Transaction
    suspend fun insertWithExercises(name: String, exercises: List<RoutineExercise>) {
        val id = insertRoutine(Routine(name = name, orderIndex = nextOrderIndex()))
        if (exercises.isNotEmpty()) {
            insertExercises(exercises.map { it.copy(id = 0, routineId = id) })
        }
    }
}

@Dao
interface BodyRecordDao {
    @Query("SELECT * FROM body_record ORDER BY date DESC")
    fun observeAll(): Flow<List<BodyRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: BodyRecord)
}
