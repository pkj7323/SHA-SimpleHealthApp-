package com.pkj.leanbulk.data

import androidx.room.ColumnInfo
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

/**
 * 음식 카탈로그. 시드로 깔린 것과 사용자가 쓰면서 쌓인 것이 한 테이블에 같이 산다.
 *
 * 종목([Exercise])과 같은 구조지만 **다른 점이 하나 있다** — 이 테이블은 카탈로그가
 * 생기기 전부터 "한 번 입력한 음식이 쌓이는" 용도로 쓰이고 있었다. 그래서 기존 행은
 * 마이그레이션에서 `source = USER`로 채워지고 slug도 그때 만들어 붙인다.
 *
 * 이름에 제공량이 들어간다("닭가슴살 100g"). 수량을 따로 두지 않는 이유는 이 앱의
 * 한 끼니가 음식 하나가 아니라 자유 텍스트("현미밥 180g + 닭가슴살 150g + 나물반찬")
 * 이기 때문이다. 음식 목록은 그 칸을 채워주는 도우미지 끼니의 구성 요소가 아니다.
 */
@Entity(
    tableName = "food_item",
    indices = [Index(value = ["name"], unique = true), Index(value = ["slug"], unique = true)]
)
data class FoodItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "") val slug: String = "",
    val name: String,
    /** 밥·곡물 / 육류 / 생선·해산물 / 계란·유제품 / 두부·콩 / 채소 / 과일 / 견과 / 간편식 / 음료 */
    @ColumnInfo(defaultValue = "기타") val category: String = "기타",
    @androidx.room.Embedded val macros: Macros,
    val usedCount: Int = 1,
    @ColumnInfo(defaultValue = "USER") val source: ItemSource = ItemSource.USER,
    /** 지우지 않고 숨긴다 — 지우면 다음 시드 갱신 때 slug로 되살아난다. */
    @ColumnInfo(defaultValue = "0") val hidden: Boolean = false
)

/** 지금 뭘 하려는 중인지. 목표 칼로리를 얼마나 올리고 내릴지가 여기서 갈린다. */
enum class GoalType { BULK, MAINTAIN, CUT }

/** 운동 강도·생활 패턴. 유지 칼로리 추정의 계수가 된다. */
enum class ActivityLevel { LOW, MEDIUM, HIGH }

/**
 * 하루 섭취 목표. 행이 하나뿐인 설정 테이블.
 *
 * [goal]과 [activityLevel]은 **권장값을 계산하는 재료이자 기록**이다. 저장된 숫자를
 * 자동으로 따라 바꾸지는 않는다 — 체중을 잴 때마다 목표가 조용히 달라지면 사용자가
 * 자기 목표를 신뢰할 수 없다. 대신 목표 화면에서 권장값을 보여주고 채울지 고르게 한다.
 *
 * 나중에 AI 챗이 "지금 벌크업 중"이라는 맥락을 여기서 읽는다.
 */
@Entity(tableName = "diet_target")
data class DietTarget(
    @PrimaryKey val id: Int = 1,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val sugar: Double,
    @ColumnInfo(defaultValue = "MAINTAIN") val goal: GoalType = GoalType.MAINTAIN,
    @ColumnInfo(defaultValue = "MEDIUM") val activityLevel: ActivityLevel = ActivityLevel.MEDIUM
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

/**
 * 종목을 무엇으로 재는지. 종목마다 붙어 있고, 고르는 순간 입력 칸이 이걸 따라 바뀐다.
 *
 * 예전에는 무게×횟수 칸밖에 없어서 플랭크·파머스캐리 같은 건 `defaultSetting`에
 * "30~40초"처럼 **글자로만 적히고 실제로는 기록되지 않았다.**
 */
enum class TrackingType {
    /** 벤치프레스 등 대부분 */
    WEIGHT_REPS,
    /** 푸시업·크런치처럼 맨몸으로 횟수만 */
    REPS,
    /** 플랭크처럼 버틴 시간만 */
    TIME,
    /** 파머스캐리·수트케이스 캐리처럼 무게를 들고 버틴 시간 */
    WEIGHT_TIME,
    /** 러닝·사이클 등 유산소 */
    DISTANCE_TIME
}

/** 앱이 깔아준 항목인지, 사용자가 직접 추가한 것인지. 시드 갱신은 [SEED]만 건드린다. */
enum class ItemSource { SEED, USER }

/**
 * 운동 종목 카탈로그. 기록에서 이름 대신 여기서 골라 넣는다.
 *
 * [slug]가 기기 간 정체성이다. auto id는 기기마다 달라서 의미가 없으므로 로컬 전용으로만
 * 쓰고, 시드 갱신과 백업 파일 대조는 전부 slug 기준으로 한다. 덕분에 앱을 업데이트해
 * 시드를 늘려도 기존 기록의 참조가 깨지지 않는다.
 */
@Entity(
    tableName = "exercise",
    indices = [Index(value = ["slug"], unique = true), Index(value = ["name"])]
)
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val slug: String,
    val name: String,
    /** 가슴 / 등 / 어깨 / 하체 / 팔 / 코어 / 유산소 */
    val bodyPart: String,
    /** 바벨 / 덤벨 / 머신 / 케이블 / 맨몸 / 기타 */
    val equipment: String,
    val trackingType: TrackingType,
    val source: ItemSource,
    /**
     * 사용자가 안 쓰겠다고 치운 종목.
     *
     * **지우지 않고 숨기는 이유**: SEED 항목을 정말로 삭제하면 다음 시드 실행 때
     * slug upsert로 되살아난다. 숨김 상태는 시드 갱신이 건드리지 않는다.
     */
    val hidden: Boolean = false
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
    /**
     * 카탈로그를 안 거치고 직접 입력할 수도 있어서 nullable이다.
     * [exerciseName]을 함께 남기는 이유는 카탈로그 항목이 바뀌거나 지워져도
     * **과거 기록은 그대로여야** 하기 때문(xlsx 내보내기도 이름 기준으로 동작한다).
     */
    val exerciseId: Long? = null,
    val exerciseName: String,
    /**
     * 기록할 당시의 추적 방식을 복사해 둔다.
     * 나중에 카탈로그에서 종목의 추적 방식을 고쳐도 과거 기록의 해석이 흔들리면 안 된다.
     *
     * `defaultValue`를 명시하는 이유: NOT NULL 컬럼을 ALTER TABLE로 추가하려면 SQLite가
     * 기본값을 요구한다. 엔티티에도 같이 적어두지 않으면 마이그레이션 후 스키마 검증에서
     * 기본값이 어긋난다.
     */
    @ColumnInfo(defaultValue = "WEIGHT_REPS")
    val trackingType: TrackingType = TrackingType.WEIGHT_REPS,
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
    val done: Boolean,
    /** 시간형 종목에서만 쓴다. 무게×횟수 종목에서는 null. */
    val durationSec: Int? = null,
    /** 유산소 종목에서만 쓴다(미터). */
    val distanceM: Double? = null
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
    val exerciseId: Long? = null,
    val exerciseName: String,
    @ColumnInfo(defaultValue = "WEIGHT_REPS")
    val trackingType: TrackingType = TrackingType.WEIGHT_REPS,
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
