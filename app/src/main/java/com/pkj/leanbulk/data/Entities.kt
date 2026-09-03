package com.pkj.leanbulk.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * 매크로는 모를 수 있으므로 전부 nullable이다.
 * null = "모름"이고 0.0 = "정말 0g"이라 둘을 구분해서 다룬다.
 */
data class Macros(
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val sugar: Double? = null
) {
    val isEmpty: Boolean get() = kcal == null && protein == null && carbs == null && fat == null && sugar == null
}

/** 하루 한 끼니의 실제 기록. 끼니 이름은 고정이 아니라 자유롭게 추가·삭제된다. */
@Entity(tableName = "meal_entry", indices = [Index(value = ["date"])])
data class MealEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String, // yyyy-MM-dd
    val orderIndex: Int,
    val slotName: String, // 아침 / 점심 / 간식 ... 사용자가 자유롭게 지정
    val menuText: String,
    @androidx.room.Embedded val macros: Macros,
    val eaten: Boolean,
    val memo: String = ""
)

/** 저장해두고 불러오는 식단표(템플릿). 운동 루틴과 같은 개념. */
@Entity(tableName = "diet_template")
data class DietTemplate(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val orderIndex: Int
)

/** 식단표에 속한 끼니 하나. */
@Entity(
    tableName = "diet_template_meal",
    foreignKeys = [
        ForeignKey(
            entity = DietTemplate::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["templateId"])]
)
data class DietTemplateMeal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val orderIndex: Int,
    val slotName: String,
    val menuText: String,
    @androidx.room.Embedded val macros: Macros
)

data class DietTemplateWithMeals(
    @androidx.room.Embedded val template: DietTemplate,
    @Relation(parentColumn = "id", entityColumn = "templateId")
    val meals: List<DietTemplateMeal>
)

/** 한 번 입력한 음식은 여기 쌓여서 다음에 검색해 불러올 수 있다. */
@Entity(tableName = "food_item", indices = [Index(value = ["name"], unique = true)])
data class FoodItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @androidx.room.Embedded val macros: Macros,
    val usedCount: Int = 1
)

/** 하루 섭취 목표. 행이 하나뿐인 설정 테이블. */
@Entity(tableName = "diet_target")
data class DietTarget(
    @PrimaryKey val id: Int = 1,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val sugar: Double
)

/**
 * 앱이 이 DB에 대해 기억해둬야 하는 값들. 항상 한 줄(id=1)만 있다.
 *
 * **일부러 DB에 둔다.** 동기화 메타데이터(마지막 rev 등)는 DataStore에 두는데
 * 이건 반대다 — [seedVersion]은 "이 기록 뭉치에 어느 버전의 시드까지 반영됐나"라서
 * **데이터를 따라다녀야** 한다. 남의 백업을 불러오면 그쪽 시드 버전이 같이 들어오고,
 * 그래야 부족한 시드가 다시 채워진다.
 */
@Entity(tableName = "app_meta")
data class AppMeta(
    @PrimaryKey val id: Int = 1,
    val seedVersion: Int = 0
)

/** 하루 운동에 포함된 종목 하나. 세트는 workout_set 테이블에 따로 저장된다. */
@Entity(
    tableName = "workout_exercise",
    indices = [Index(value = ["date"])]
)
data class WorkoutExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val routineName: String,
    val orderIndex: Int,
    val exerciseName: String,
    val defaultSetting: String = "",
    val memo: String = ""
)

/** 종목 안의 세트 한 줄: 무게 / 횟수 / 완료 체크. */
@Entity(
    tableName = "workout_set",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutExercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["exerciseId"])]
)
data class WorkoutSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,
    val setIndex: Int,
    val weightKg: Double,
    val reps: Int,
    val done: Boolean
)

/** 종목 + 그 종목의 세트들을 한 번에 읽기 위한 조회용 타입. */
data class ExerciseWithSets(
    @androidx.room.Embedded val exercise: WorkoutExercise,
    @Relation(parentColumn = "id", entityColumn = "exerciseId")
    val sets: List<WorkoutSet>
) {
    val totalReps: Int get() = sets.filter { it.done }.sumOf { it.reps }
    val volume: Double get() = sets.filter { it.done }.sumOf { it.weightKg * it.reps }
}

/** 사용자가 관리하는 운동 루틴(= 분할). 이름 수정·추가·삭제 가능. */
@Entity(tableName = "routine")
data class Routine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val orderIndex: Int
)

/** 루틴에 속한 종목 템플릿. 루틴을 불러오면 이 값들로 세트가 채워진다. */
@Entity(
    tableName = "routine_exercise",
    foreignKeys = [
        ForeignKey(
            entity = Routine::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["routineId"])]
)
data class RoutineExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long,
    val orderIndex: Int,
    val exerciseName: String,
    val defaultSetting: String,
    val defaultWeight: Double,
    val defaultReps: Int
)

data class RoutineWithExercises(
    @androidx.room.Embedded val routine: Routine,
    @Relation(parentColumn = "id", entityColumn = "routineId")
    val exercises: List<RoutineExercise>
)

/** 2주 간격 체성분 측정 기록. */
@Entity(tableName = "body_record", indices = [Index(value = ["date"], unique = true)])
data class BodyRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val weightKg: Double,
    val muscleMassKg: Double?,
    val fatMassKg: Double?,
    val fatPercent: Double?,
    val waistCm: Double?,
    val tool: String = "",
    val memo: String = ""
)
