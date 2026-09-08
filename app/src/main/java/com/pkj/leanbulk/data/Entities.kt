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

    /**
     * 배수만큼 늘린 매크로. `null`("모름")은 배수를 곱해도 그대로 `null`이다 —
     * 모르는 값에 배수를 곱해서 새로운 숫자를 만들어내면 안 된다.
     */
    fun scaledBy(multiplier: Double): Macros = Macros(
        kcal = kcal?.times(multiplier),
        protein = protein?.times(multiplier),
        carbs = carbs?.times(multiplier),
        fat = fat?.times(multiplier),
        sugar = sugar?.times(multiplier)
    )
}

/**
 * 이 매크로가 **어디서 온 값인가.**
 *
 * 셋을 구분하지 않으면 [MacroEstimator]가 메뉴 이름만 보고 만든 어림값이 사용자가 직접
 * 잰 값과 같은 무게로 취급된다. 합계에 섞이고, 나중에 AI가 읽으면 **추정치를 사실로
 * 세탁한다.** 화면에서 "어림값 확정"을 누르는 순간 지금까지는 구분이 사라졌다.
 */
enum class MacroSource {
    /** 매크로가 비어 있음 (모름) */
    NONE,
    /** 사용자가 직접 숫자를 넣음 */
    ENTERED,
    /** 음식 카탈로그에서 채움 — 카탈로그 기준량 × 배수로 계산된 값 */
    CATALOG,
    /** 메뉴 이름으로 어림한 값을 확정한 것 */
    ESTIMATED,
    /** AI가 방금 제안한 음식으로 기록한 값 — 모델이 기억으로 뱉은 숫자라 가장 약하다 */
    AI_SUGGESTED
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
    /**
     * 예전에 있던 "먹었다" 체크. 끼니 칸이 고정되면서 화면에서 없앴고 새 기록은 늘 `true`다
     * (담았으면 먹은 것). 옛 기록에는 `false`가 남아 있을 수 있어 컬럼은 그대로 둔다.
     */
    val eaten: Boolean,
    /** 이 끼니에 사용자가 적어둔 글. 음식 줄과 달리 영양 합계에는 안 들어간다. */
    val memo: String = "",
    @ColumnInfo(defaultValue = "ENTERED") val macroSource: MacroSource = MacroSource.ENTERED
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
    // 이름은 유니크가 아니다. 같은 이름을 여러 개 두는 걸 막으면(닭가슴살 두 종류처럼)
    // 사용자가 원하는 대로 목록을 꾸릴 수 없고, 이름이 겹친다는 이유로 수정이 막힌다.
    // 기기 간 정체성은 [FoodItem.slug]가 맡는다.
    indices = [Index(value = ["name"]), Index(value = ["slug"], unique = true)]
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
    /** 검색 없이 목록 위쪽에서 바로 찾게 한다. [MealPreset](여러 음식+수량 묶음)과는 다른 기능이다. */
    @ColumnInfo(defaultValue = "0") val favorite: Boolean = false,
    /**
     * 이 음식 "1"에 해당하는 양. 단위는 [baseAmountUnit]이고, [Macros]는 이 양을 기준으로 한다.
     *
     * **실제로 잰 값을 알 때만 채운다.** "김밥 1줄" 같은 항목은 양을 모르므로 null로
     * 남긴다 — 여기서 숫자를 지어내면 이 앱이 지키는 "모르면 모른다"는 원칙이 스키마
     * 단계에서부터 깨진다. null이면 배수만으로 기록하고 양 표시는 하지 않는다.
     *
     * 컬럼 이름에 `Gram`이 남아 있는 건 g만 있던 시절의 잔재다. 이름을 바꾸려면 테이블을
     * 새로 만들어 옮겨 담아야 하는데(SQLite 3.19에 RENAME COLUMN이 없다), 눈에 보이지도
     * 않는 이름 때문에 기록을 옮기는 위험을 지지 않는다.
     */
    @ColumnInfo(name = "baseAmountGram") val baseAmount: Double? = null,
    /** [baseAmount]의 단위. 우유·음료처럼 ml/L로 재는 것이 있어서 g 하나로는 부족하다. */
    @ColumnInfo(defaultValue = "G") val baseAmountUnit: AmountUnit = AmountUnit.G
)

/**
 * 기준량의 단위.
 *
 * **서로 환산하지 않는다.** 1L 우유가 1000g이 아니듯(밀도가 1이 아니다) 물 말고는 ml↔g가
 * 안 맞고, 여기서 환산 계수를 지어내면 이 앱이 지키는 "모르면 모른다"가 깨진다.
 * 사용자가 적은 숫자에 사용자가 고른 단위를 그대로 붙여 보여줄 뿐이다.
 */
enum class AmountUnit(val label: String) { G("g"), ML("ml"), L("L") }

/**
 * 끼니 안의 음식 한 줄.
 *
 * 매크로는 [FoodItem]의 기준값 × [multiplier]를 그 순간 계산해 **스냅샷으로 저장한다.**
 * 카탈로그 값을 나중에 고쳐도 과거 기록이 흔들리면 안 된다 — 운동 기록이 그 시점의
 * `trackingType`을 복사해두는 것과 같은 이유([WorkoutExercise.trackingType]).
 */
@Entity(
    tableName = "meal_food",
    foreignKeys = [
        ForeignKey(
            entity = MealEntry::class,
            parentColumns = ["id"],
            childColumns = ["mealEntryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["mealEntryId"])]
)
data class MealFood(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mealEntryId: Long,
    val orderIndex: Int,
    /** 카탈로그 항목이 지워져도 과거 기록은 남아야 하므로 nullable([WorkoutExercise.exerciseName]과 같은 패턴). */
    val foodId: Long? = null,
    val name: String,
    val multiplier: Double,
    /** [FoodItem.baseAmount]가 null이면 이것도 null — 배수만 있고 양은 표시하지 않는다. */
    @ColumnInfo(name = "amountGram") val amount: Double? = null,
    /** 단위도 같이 스냅샷한다 — 카탈로그에서 지워진 음식 줄도 제 단위로 보여야 한다. */
    @ColumnInfo(defaultValue = "G") val amountUnit: AmountUnit = AmountUnit.G,
    @androidx.room.Embedded val macros: Macros,
    @ColumnInfo(defaultValue = "CATALOG") val macroSource: MacroSource = MacroSource.CATALOG
)

/**
 * 뭔가 담긴 끼니인지. 끼니 칸이 아침·점심·저녁·간식으로 고정되면서 **빈 칸도 같이 저장되므로**
 * (한 끼니를 고치면 그날 네 칸이 통째로 저장된다) 화면·내보내기가 이걸로 걸러야 한다.
 * 안 그러면 아무것도 안 먹은 칸이 "0kcal 끼니"로 세어진다.
 */
val MealEntry.hasContent: Boolean
    get() = menuText.isNotBlank() || !macros.isEmpty || memo.isNotBlank()

/** 끼니 + 그 안의 음식 줄들. */
data class MealEntryWithFoods(
    @androidx.room.Embedded val entry: MealEntry,
    @Relation(parentColumn = "id", entityColumn = "mealEntryId")
    val foods: List<MealFood>
)

/** 카탈로그 값 × 배수로 음식 줄 하나를 만든다. */
fun FoodItem.toMealFood(mealEntryId: Long, orderIndex: Int, multiplier: Double): MealFood = MealFood(
    mealEntryId = mealEntryId,
    orderIndex = orderIndex,
    foodId = id,
    name = name,
    multiplier = multiplier,
    amount = baseAmount?.times(multiplier),
    amountUnit = baseAmountUnit,
    macros = macros.scaledBy(multiplier),
    macroSource = MacroSource.CATALOG
)

/**
 * 자주 먹는 음식 조합을 저장해뒀다가 한 번에 끼니에 담는다("내 식사" — DIET-REDESIGN.md §9.3).
 *
 * [DietTemplate](식단표)과 단위가 다르다. 식단표는 하루 전체(여러 끼니)의 틀이고, 이건
 * **끼니 하나에 들어가는 음식 조합**이다.
 */
@Entity(tableName = "meal_preset")
data class MealPreset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val orderIndex: Int
)

/** 세트 안의 음식 한 줄. [MealFood]와 같은 모양이다. */
@Entity(
    tableName = "meal_preset_food",
    foreignKeys = [
        ForeignKey(
            entity = MealPreset::class,
            parentColumns = ["id"],
            childColumns = ["presetId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["presetId"])]
)
data class MealPresetFood(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val presetId: Long,
    val orderIndex: Int,
    val foodId: Long? = null,
    val name: String,
    val multiplier: Double,
    @ColumnInfo(name = "amountGram") val amount: Double? = null,
    @ColumnInfo(defaultValue = "G") val amountUnit: AmountUnit = AmountUnit.G,
    @androidx.room.Embedded val macros: Macros,
    @ColumnInfo(defaultValue = "CATALOG") val macroSource: MacroSource = MacroSource.CATALOG
)

data class MealPresetWithFoods(
    @androidx.room.Embedded val preset: MealPreset,
    @Relation(parentColumn = "id", entityColumn = "presetId")
    val foods: List<MealPresetFood>
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
    val seedVersion: Int = 0,
    /**
     * 세트를 체크했을 때 띄우는 휴식 타이머의 길이(초).
     *
     * 타이머에서 −/+로 고치면 그 값이 다음 세트의 기본이 된다. 사람마다, 종목마다 쉬는
     * 시간이 다른데 앱을 켤 때마다 90초로 돌아가면 매번 다시 맞춰야 한다.
     */
    @ColumnInfo(defaultValue = "90") val restSeconds: Int = 90
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
    val source: ItemSource
)

/**
 * 하루 운동을 언제 시작해서 언제 끝냈는지. 날짜 하나에 한 줄.
 *
 * 종목([WorkoutExercise])이 아니라 따로 두는 이유는 종목을 지웠다 다시 담아도
 * "몇 시에 시작해서 얼마나 했는지"는 그대로여야 하기 때문이다.
 *
 * **경과 시간은 저장하지 않는다.** 시작 시각과 지금(끝냈으면 [endedAtEpochMs])의 차이로
 * 매번 계산한다. 숫자로 굳혀두면 앱이 뒤로 가 있는 동안 시간이 멈춘다.
 */
@Entity(tableName = "workout_session")
data class WorkoutSession(
    @PrimaryKey val date: String,
    /**
     * **처음** 시작을 누른 시각. "몇 시에 운동을 시작했나"라는 기록이라 멈췄다 이어서 해도
     * 안 바뀐다. 경과 시간을 이걸로 재면 안 된다 — 쉰 시간까지 같이 세어진다.
     */
    val startedAtEpochMs: Long,
    /**
     * 멈춰 있던 구간까지 빼고 **실제로 잰 시간의 합**(밀리초).
     *
     * 멈출 때 그 구간 길이를 여기 더하고, 이어서 할 땐 [resumedAtEpochMs]만 새로 잡는다.
     * 이렇게 안 하면 잠깐 멈춰 두고 한 시간 뒤에 이어서 했을 때 그 한 시간이 운동 시간이 된다.
     */
    @ColumnInfo(defaultValue = "0") val accumulatedMs: Long = 0,
    /** 지금 도는 구간이 시작된 시각. **null이면 멈춰 있는 것**이다. */
    val resumedAtEpochMs: Long? = null,
    /** 마지막으로 멈춘 시각. 이어서 하면 다시 null이 된다. */
    val endedAtEpochMs: Long? = null
) {
    val running: Boolean get() = resumedAtEpochMs != null

    /** [nowEpochMs] 기준 지금까지 잰 시간(밀리초). 멈춰 있으면 [accumulatedMs] 그대로다. */
    fun elapsedMs(nowEpochMs: Long): Long =
        accumulatedMs + (resumedAtEpochMs?.let { (nowEpochMs - it).coerceAtLeast(0) } ?: 0)

    /**
     * 멈춰 있는 운동의 총 시간(밀리초). 도는 중이면 null이다.
     *
     * "지금까지 몇 분"과 "그날 몇 분 했다"는 다른 질문이다. 지난 기록을 훑는 화면에서
     * 볼 때마다 늘어나는 숫자를 기록처럼 보여주면 안 되므로 여기서 갈라둔다.
     */
    val finishedMs: Long? get() = if (running) null else accumulatedMs

    /** 지금 시각 기준으로 시계를 멈춘 상태. 돌고 있던 구간을 [accumulatedMs]에 접어 넣는다. */
    fun pausedAt(nowEpochMs: Long): WorkoutSession = copy(
        accumulatedMs = elapsedMs(nowEpochMs),
        resumedAtEpochMs = null,
        endedAtEpochMs = nowEpochMs
    )

    /** 지금 시각부터 다시 센다. 이미 잰 시간([accumulatedMs])은 그대로 두고 이어 붙인다. */
    fun resumedAt(nowEpochMs: Long): WorkoutSession = copy(
        resumedAtEpochMs = nowEpochMs,
        endedAtEpochMs = null
    )
}

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
    /**
     * 그날 이 종목에 대해 적어둔 글("허리 조심", "다음엔 5kg 올리기").
     *
     * [RoutineExercise.defaultSetting](루틴의 기본 세팅)과 다르다 — 저건 "이렇게 할 것"이라
     * 루틴을 불러올 때마다 따라오지만, 이건 **그날 있었던 일**이라 날짜에만 붙는다.
     */
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

/**
 * 루틴에 속한 종목 템플릿. 세트는 [RoutineSet]에 따로 있다.
 *
 * 예전에는 여기 `defaultWeight`/`defaultReps` 한 쌍만 두고 루틴을 불러올 때 그 값으로
 * 똑같은 세트 세 개를 만들었다. 그래서 **세트 수도, 세트마다 다른 무게도 못 담았다** —
 * 55kg 워밍업 + 60kg 세 세트로 저장해도 불러오면 55kg 세 세트가 됐다.
 * 오늘의 기록(`workout_exercise` + `workout_set`)과 같은 모양으로 맞췄다.
 */
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
    val defaultSetting: String
)

/** 루틴 종목의 세트 하나. `workout_set`과 같은 칸을 쓰되 완료 여부(`done`)는 없다. */
@Entity(
    tableName = "routine_set",
    foreignKeys = [
        ForeignKey(
            entity = RoutineExercise::class,
            parentColumns = ["id"],
            childColumns = ["routineExerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["routineExerciseId"])]
)
data class RoutineSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineExerciseId: Long,
    val setIndex: Int,
    val weightKg: Double,
    val reps: Int,
    val durationSec: Int? = null,
    val distanceM: Double? = null
)

/** 루틴 종목 + 그 세트들. */
data class RoutineExerciseWithSets(
    @androidx.room.Embedded val exercise: RoutineExercise,
    @Relation(parentColumn = "id", entityColumn = "routineExerciseId")
    val sets: List<RoutineSet>
)

data class RoutineWithExercises(
    @androidx.room.Embedded val routine: Routine,
    @Relation(entity = RoutineExercise::class, parentColumn = "id", entityColumn = "routineId")
    val exercises: List<RoutineExerciseWithSets>
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
