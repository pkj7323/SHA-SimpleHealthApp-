package com.pkj.leanbulk.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration

/**
 * 4 → 5: `app_meta` 추가 (시드 버전 기록용).
 *
 * 행은 넣지 않는다. 없으면 `seedVersion`이 0으로 읽히고, 시드 실행기가 1단계를 돌린다.
 * 1단계는 "테이블이 비어 있을 때만 넣기"라 이미 시드가 깔린 기존 사용자에겐 아무 일도
 * 일어나지 않는다.
 */
internal val MIGRATION_4_5 = Migration(4, 5) { db ->
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS `app_meta` " +
            "(`id` INTEGER NOT NULL, `seedVersion` INTEGER NOT NULL, PRIMARY KEY(`id`))"
    )
}

/**
 * 5 → 6: 종목 카탈로그(`exercise`) 추가 + 기록·루틴에 카탈로그 연결과 추적 방식 컬럼 추가.
 *
 * 새 컬럼은 전부 nullable이거나 기본값이 있어서 기존 행이 그대로 살아남는다.
 * 카탈로그 내용을 채우고 기존 기록을 연결하는 건 여기가 아니라 시드 2단계가 한다
 * (마이그레이션에 데이터를 넣으면 버전마다 누적돼서 감당이 안 된다).
 */
internal val MIGRATION_5_6 = Migration(5, 6) { db ->
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS `exercise` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`slug` TEXT NOT NULL, `name` TEXT NOT NULL, `bodyPart` TEXT NOT NULL, " +
            "`equipment` TEXT NOT NULL, `trackingType` TEXT NOT NULL, `source` TEXT NOT NULL, " +
            "`hidden` INTEGER NOT NULL)"
    )
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_exercise_slug` ON `exercise` (`slug`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_name` ON `exercise` (`name`)")

    db.execSQL("ALTER TABLE `workout_set` ADD COLUMN `durationSec` INTEGER")
    db.execSQL("ALTER TABLE `workout_set` ADD COLUMN `distanceM` REAL")

    db.execSQL("ALTER TABLE `workout_exercise` ADD COLUMN `exerciseId` INTEGER")
    db.execSQL(
        "ALTER TABLE `workout_exercise` ADD COLUMN `trackingType` TEXT NOT NULL DEFAULT 'WEIGHT_REPS'"
    )

    db.execSQL("ALTER TABLE `routine_exercise` ADD COLUMN `exerciseId` INTEGER")
    db.execSQL(
        "ALTER TABLE `routine_exercise` ADD COLUMN `trackingType` TEXT NOT NULL DEFAULT 'WEIGHT_REPS'"
    )
}

/**
 * 6 → 7: 음식도 카탈로그가 된다 (slug · 분류 · source · 숨김).
 *
 * 종목 때와 달리 `food_item`에는 **이미 사용자 데이터가 들어 있다.** 카탈로그가 생기기
 * 전부터 "한 번 입력한 음식이 쌓이는" 용도로 쓰였기 때문이다. 그래서 기존 행을
 * `source = USER`로 두고 slug를 만들어 채운 뒤에야 유니크 인덱스를 걸 수 있다.
 */
internal val MIGRATION_6_7 = Migration(6, 7) { db ->
    db.execSQL("ALTER TABLE `food_item` ADD COLUMN `slug` TEXT NOT NULL DEFAULT ''")
    db.execSQL("ALTER TABLE `food_item` ADD COLUMN `category` TEXT NOT NULL DEFAULT '기타'")
    db.execSQL("ALTER TABLE `food_item` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'USER'")
    db.execSQL("ALTER TABLE `food_item` ADD COLUMN `hidden` INTEGER NOT NULL DEFAULT 0")

    // 빈 slug가 여러 개면 유니크 인덱스를 못 건다. 인덱스보다 먼저 채운다.
    db.execSQL("UPDATE `food_item` SET `slug` = 'user-' || `id` WHERE `slug` = ''")
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_food_item_slug` ON `food_item` (`slug`)")
}

/**
 * 7 → 8: 섭취 목표에 목적(벌크업/유지/컷팅)과 활동량을 붙인다.
 *
 * 기존 목표 숫자는 그대로 두고 기본값만 채운다 — 사용자가 직접 맞춰둔 값을
 * 목적 도입을 이유로 덮어쓰면 안 된다.
 */
internal val MIGRATION_7_8 = Migration(7, 8) { db ->
    db.execSQL("ALTER TABLE `diet_target` ADD COLUMN `goal` TEXT NOT NULL DEFAULT 'MAINTAIN'")
    db.execSQL("ALTER TABLE `diet_target` ADD COLUMN `activityLevel` TEXT NOT NULL DEFAULT 'MEDIUM'")
}

/**
 * 8 → 9: 종목의 `hidden` 컬럼을 없앤다 (종목 숨기기 기능 폐기).
 *
 * 숨긴 종목은 **다시 보이게 된다.** 되돌리는 화면이 없어서 숨김이 사실상 삭제로 작동했고,
 * 그럴 바엔 기능 자체를 빼는 게 낫다는 판단이다.
 *
 * SQLite `DROP COLUMN`은 3.35+가 필요한데 minSdk 26은 3.19라 못 쓴다. 그래서 새 테이블을
 * 만들어 옮겨 담는 정석대로 간다. 인덱스는 옛 테이블과 함께 사라지므로 다시 만든다.
 * `CREATE TABLE` 문은 `app/schemas/9.json`의 `createSql`을 그대로 옮긴 것이다.
 */
internal val MIGRATION_8_9 = Migration(8, 9) { db ->
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS `exercise_new` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`slug` TEXT NOT NULL, `name` TEXT NOT NULL, `bodyPart` TEXT NOT NULL, " +
            "`equipment` TEXT NOT NULL, `trackingType` TEXT NOT NULL, `source` TEXT NOT NULL)"
    )
    db.execSQL(
        "INSERT INTO `exercise_new` (`id`, `slug`, `name`, `bodyPart`, `equipment`, `trackingType`, `source`) " +
            "SELECT `id`, `slug`, `name`, `bodyPart`, `equipment`, `trackingType`, `source` FROM `exercise`"
    )
    db.execSQL("DROP TABLE `exercise`")
    db.execSQL("ALTER TABLE `exercise_new` RENAME TO `exercise`")
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_exercise_slug` ON `exercise` (`slug`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_name` ON `exercise` (`name`)")
}

/**
 * 9 → 10: 루틴이 **세트를 그대로** 담는다 (`routine_set` 추가, 종목의 기본값 한 쌍 제거).
 *
 * 예전엔 `routine_exercise.defaultWeight`/`defaultReps` 하나로 세트 세 개를 찍어냈다.
 * 세트 수도 세트별 무게도 담기지 않아서, 55kg 워밍업 + 60kg 3세트로 저장해도 불러오면
 * 55kg 3세트가 됐다.
 *
 * 기존 루틴은 **옛 기본값으로 세트 [SeedData.DEFAULT_SET_COUNT]개를 만들어 채운다** —
 * 지금까지 불러올 때 만들어지던 것과 같은 모양이라, 사용자가 보기엔 달라지는 게 없다.
 * 채운 뒤에야 옛 컬럼을 지울 수 있으므로 순서를 바꾸지 말 것.
 */
internal val MIGRATION_9_10 = Migration(9, 10) { db ->
    // 1. 옛 기본값을 따로 떠 둔다. 컬럼을 지우고 나면 못 읽는데, 세트를 채우려면 필요하다.
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS `routine_exercise_defaults` " +
            "(`id` INTEGER PRIMARY KEY, `defaultWeight` REAL NOT NULL, `defaultReps` INTEGER NOT NULL)"
    )
    db.execSQL(
        "INSERT INTO `routine_exercise_defaults` (`id`, `defaultWeight`, `defaultReps`) " +
            "SELECT `id`, `defaultWeight`, `defaultReps` FROM `routine_exercise`"
    )

    // 2. 기본값 컬럼을 뺀 새 테이블로 갈아탄다. SQLite 3.19에는 DROP COLUMN이 없다(8→9와 같은 방식).
    //    `routine_set`을 아직 안 만든 시점이어야 한다 — 외래키가 켜져 있으면 부모 테이블을
    //    DROP할 때 자식 행이 CASCADE로 같이 지워지기 때문이다. 순서를 바꾸면 세트가 사라진다.
    //    CREATE 문은 app/schemas/10.json의 createSql을 그대로 옮긴 것이다.
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS `routine_exercise_new` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `routineId` INTEGER NOT NULL, " +
            "`orderIndex` INTEGER NOT NULL, `exerciseId` INTEGER, `exerciseName` TEXT NOT NULL, " +
            "`trackingType` TEXT NOT NULL DEFAULT 'WEIGHT_REPS', `defaultSetting` TEXT NOT NULL, " +
            "FOREIGN KEY(`routineId`) REFERENCES `routine`(`id`) " +
            "ON UPDATE NO ACTION ON DELETE CASCADE )"
    )
    db.execSQL(
        "INSERT INTO `routine_exercise_new` " +
            "(`id`, `routineId`, `orderIndex`, `exerciseId`, `exerciseName`, `trackingType`, `defaultSetting`) " +
            "SELECT `id`, `routineId`, `orderIndex`, `exerciseId`, `exerciseName`, `trackingType`, `defaultSetting` " +
            "FROM `routine_exercise`"
    )
    db.execSQL("DROP TABLE `routine_exercise`")
    db.execSQL("ALTER TABLE `routine_exercise_new` RENAME TO `routine_exercise`")
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_routine_exercise_routineId` ON `routine_exercise` (`routineId`)"
    )

    // 3. 세트 테이블을 만들고, 떠 둔 기본값으로 채운다.
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS `routine_set` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`routineExerciseId` INTEGER NOT NULL, `setIndex` INTEGER NOT NULL, " +
            "`weightKg` REAL NOT NULL, `reps` INTEGER NOT NULL, " +
            "`durationSec` INTEGER, `distanceM` REAL, " +
            "FOREIGN KEY(`routineExerciseId`) REFERENCES `routine_exercise`(`id`) " +
            "ON UPDATE NO ACTION ON DELETE CASCADE )"
    )
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_routine_set_routineExerciseId` " +
            "ON `routine_set` (`routineExerciseId`)"
    )
    repeat(SeedData.DEFAULT_SET_COUNT) { index ->
        db.execSQL(
            "INSERT INTO `routine_set` (`routineExerciseId`, `setIndex`, `weightKg`, `reps`) " +
                "SELECT `id`, $index, `defaultWeight`, `defaultReps` FROM `routine_exercise_defaults`"
        )
    }

    db.execSQL("DROP TABLE `routine_exercise_defaults`")
}

/**
 * 10 → 11: 끼니 매크로의 **출처**를 남긴다.
 *
 * 지금까지 세 갈래(직접 입력 · 음식 카탈로그 · 어림값 확정)가 같은 값으로 저장돼서,
 * 저장된 뒤에는 구분할 방법이 없었다. 특히 [MacroEstimator]의 어림값이 사용자가 잰
 * 값과 섞이면 나중에 그 숫자를 근거로 답하는 쪽(AI 챗)이 추정치를 사실로 세탁한다.
 *
 * **기존 행은 전부 `ENTERED`가 된다** — 그 안에 확정된 어림값이 섞여 있어도 지금 와서는
 * 갈라낼 수 없다. 매크로가 아예 비어 있던 행만 `NONE`으로 바로잡는다.
 */
internal val MIGRATION_10_11 = Migration(10, 11) { db ->
    db.execSQL(
        "ALTER TABLE `meal_entry` ADD COLUMN `macroSource` TEXT NOT NULL DEFAULT 'ENTERED'"
    )
    db.execSQL(
        "UPDATE `meal_entry` SET `macroSource` = 'NONE' WHERE " +
            "`kcal` IS NULL AND `protein` IS NULL AND `carbs` IS NULL AND " +
            "`fat` IS NULL AND `sugar` IS NULL"
    )
}

/**
 * 11 → 12: 음식이 g 단위로 계산된다 (`meal_food` 추가, `food_item`에 기준량 추가).
 *
 * 지금까지 끼니는 자유 텍스트 한 덩어리라 "이 끼니에 어떤 음식이 얼마나 들었나"가
 * 안 남았다. 그래서 음식 단위 집계도, 배수로 양을 조절하는 것도 불가능했다.
 *
 * 기존 `food_item`·`meal_entry` 행은 그대로 둔다. `baseAmountGram`은 nullable이라
 * 기본값 없이 컬럼만 추가하면 기존 행은 전부 NULL(그램 모름)로 남는다 — 시드 음식은
 * 시드 재적용(applySeeds 4단계)에서 실제로 아는 값만 채워진다.
 *
 * `CREATE TABLE`은 `app/schemas/12.json`의 `createSql`을 그대로 옮긴 것이다.
 */
internal val MIGRATION_11_12 = Migration(11, 12) { db ->
    db.execSQL("ALTER TABLE `food_item` ADD COLUMN `baseAmountGram` REAL")

    db.execSQL(
        "CREATE TABLE IF NOT EXISTS `meal_food` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `mealEntryId` INTEGER NOT NULL, " +
            "`orderIndex` INTEGER NOT NULL, `foodId` INTEGER, `name` TEXT NOT NULL, " +
            "`multiplier` REAL NOT NULL, `amountGram` REAL, " +
            "`macroSource` TEXT NOT NULL DEFAULT 'CATALOG', " +
            "`kcal` REAL, `protein` REAL, `carbs` REAL, `fat` REAL, `sugar` REAL, " +
            "FOREIGN KEY(`mealEntryId`) REFERENCES `meal_entry`(`id`) " +
            "ON UPDATE NO ACTION ON DELETE CASCADE )"
    )
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_meal_food_mealEntryId` ON `meal_food` (`mealEntryId`)"
    )
}

/**
 * 12 → 13: 즐겨찾기 + 식사세트(DIET-REDESIGN.md §9).
 *
 * `food_item.favorite`는 nullable이 아니라 기본값 0이 필요하다(검색 없이 즐겨찾기 탭에서
 * 걸러 보여줘야 하는데, 기존 행이 NULL이면 그 필터링 조건이 애매해진다).
 *
 * `meal_preset_food`는 `meal_food`와 똑같은 모양이라 CREATE 문도 같은 컬럼 순서를 따른다
 * (Room이 `macroSource`를 매크로 필드보다 앞에 두는 것도 동일 — `app/schemas/13.json` 참고).
 */
internal val MIGRATION_12_13 = Migration(12, 13) { db ->
    db.execSQL("ALTER TABLE `food_item` ADD COLUMN `favorite` INTEGER NOT NULL DEFAULT 0")

    db.execSQL(
        "CREATE TABLE IF NOT EXISTS `meal_preset` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
            "`orderIndex` INTEGER NOT NULL)"
    )
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS `meal_preset_food` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `presetId` INTEGER NOT NULL, " +
            "`orderIndex` INTEGER NOT NULL, `foodId` INTEGER, `name` TEXT NOT NULL, " +
            "`multiplier` REAL NOT NULL, `amountGram` REAL, " +
            "`macroSource` TEXT NOT NULL DEFAULT 'CATALOG', " +
            "`kcal` REAL, `protein` REAL, `carbs` REAL, `fat` REAL, `sugar` REAL, " +
            "FOREIGN KEY(`presetId`) REFERENCES `meal_preset`(`id`) " +
            "ON UPDATE NO ACTION ON DELETE CASCADE )"
    )
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_meal_preset_food_presetId` ON `meal_preset_food` (`presetId`)"
    )
}

/**
 * 13 → 14: 기준량에 단위가 붙는다(g / ml / L).
 *
 * 지금까지는 전부 g로 취급했으므로 기존 행은 **전부 `G`가 맞다** — 기본값으로 채우면 끝이고
 * 값 자체는 건드리지 않는다. 우유 200ml를 `200`으로 적어둔 사람이 있을 수 있지만, 그건
 * 사용자만 아는 사실이라 여기서 추측해 바꾸면 없던 데이터를 지어내는 셈이 된다.
 *
 * 양을 담는 컬럼 이름은 `amountGram`/`baseAmountGram` 그대로 둔다. Kotlin 쪽에서는
 * `@ColumnInfo(name = ...)`로 `amount`/`baseAmount`라 부르므로 코드는 정직하고,
 * 컬럼 이름을 바꾸려면 SQLite 3.19에 RENAME COLUMN이 없어 테이블을 통째로 옮겨 담아야 한다.
 */
internal val MIGRATION_13_14 = Migration(13, 14) { db ->
    db.execSQL("ALTER TABLE `food_item` ADD COLUMN `baseAmountUnit` TEXT NOT NULL DEFAULT 'G'")
    db.execSQL("ALTER TABLE `meal_food` ADD COLUMN `amountUnit` TEXT NOT NULL DEFAULT 'G'")
    db.execSQL("ALTER TABLE `meal_preset_food` ADD COLUMN `amountUnit` TEXT NOT NULL DEFAULT 'G'")
}

/**
 * 14 → 15: 음식의 `hidden` 컬럼을 없애고(숨기기 기능 폐기) 이름의 유니크 인덱스를 푼다.
 *
 * 숨기기는 **삭제로 오해되는데 되돌릴 수가 없었다.** 게다가 숨긴 행이 유니크한 이름을 계속
 * 붙들고 있어서, 같은 이름으로 새로 만들지도 고치지도 못하는 상태에 갇혔다. 종목에서 이미
 * 같은 이유로 없앴던 기능이다(8→9). 이제 음식도 그냥 지운다.
 *
 * **숨겨져 있던 음식은 다시 보이게 된다.** 8→9와 같은 판단이다 — 되돌릴 수단 없이 갇혀
 * 있느니 보이는 게 낫고, 필요 없으면 이제 진짜로 지울 수 있다.
 *
 * 이름 유니크를 푸는 이유는 같은 이름의 음식을 여러 개 두고 싶다는 요구 때문이다
 * (닭가슴살 두 종류처럼). 기기 간 정체성은 slug가 맡으므로 이름이 겹쳐도 문제가 없다.
 *
 * SQLite 3.19에는 DROP COLUMN이 없어서 테이블을 새로 만들어 옮겨 담는다(8→9와 같은 방식).
 * `food_item`을 가리키는 외래키는 없고(`meal_food.foodId`는 그냥 nullable 컬럼이다)
 * CREATE 문은 `app/schemas/15.json`의 createSql을 그대로 옮긴 것이다.
 */
internal val MIGRATION_14_15 = Migration(14, 15) { db ->
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS `food_item_new` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `slug` TEXT NOT NULL DEFAULT '', " +
            "`name` TEXT NOT NULL, `category` TEXT NOT NULL DEFAULT '기타', " +
            "`usedCount` INTEGER NOT NULL, `source` TEXT NOT NULL DEFAULT 'USER', " +
            "`favorite` INTEGER NOT NULL DEFAULT 0, `baseAmountGram` REAL, " +
            "`baseAmountUnit` TEXT NOT NULL DEFAULT 'G', " +
            "`kcal` REAL, `protein` REAL, `carbs` REAL, `fat` REAL, `sugar` REAL)"
    )
    db.execSQL(
        "INSERT INTO `food_item_new` " +
            "(`id`, `slug`, `name`, `category`, `usedCount`, `source`, `favorite`, " +
            "`baseAmountGram`, `baseAmountUnit`, `kcal`, `protein`, `carbs`, `fat`, `sugar`) " +
            "SELECT `id`, `slug`, `name`, `category`, `usedCount`, `source`, `favorite`, " +
            "`baseAmountGram`, `baseAmountUnit`, `kcal`, `protein`, `carbs`, `fat`, `sugar` " +
            "FROM `food_item`"
    )
    db.execSQL("DROP TABLE `food_item`")
    db.execSQL("ALTER TABLE `food_item_new` RENAME TO `food_item`")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_item_name` ON `food_item` (`name`)")
    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_food_item_slug` ON `food_item` (`slug`)")
}

/**
 * 운동 시작·종료 시각을 담을 자리와, 휴식 타이머의 기본 길이.
 *
 * 둘 다 새로 생기는 값이라 옮겨 담을 옛 데이터가 없다 — 테이블 하나를 만들고 컬럼 하나를
 * 붙이면 끝이다. `app_meta`는 항상 한 줄뿐이라 기본값 90이 그대로 그 줄의 값이 된다.
 */
internal val MIGRATION_15_16 = Migration(15, 16) { db ->
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS `workout_session` (" +
            "`date` TEXT NOT NULL, `startedAtEpochMs` INTEGER NOT NULL, " +
            "`endedAtEpochMs` INTEGER, PRIMARY KEY(`date`))"
    )
    db.execSQL("ALTER TABLE `app_meta` ADD COLUMN `restSeconds` INTEGER NOT NULL DEFAULT 90")
}

@Database(
    entities = [
        MealEntry::class,
        WorkoutExercise::class,
        WorkoutSession::class,
        WorkoutSet::class,
        Routine::class,
        RoutineExercise::class,
        RoutineSet::class,
        DietTemplate::class,
        DietTemplateMeal::class,
        FoodItem::class,
        MealFood::class,
        MealPreset::class,
        MealPresetFood::class,
        DietTarget::class,
        BodyRecord::class,
        AppMeta::class,
        Exercise::class
    ],
    version = 16,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mealDao(): MealDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun routineDao(): RoutineDao
    abstract fun dietTemplateDao(): DietTemplateDao
    abstract fun foodDao(): FoodDao
    abstract fun mealPresetDao(): MealPresetDao
    abstract fun dietTargetDao(): DietTargetDao
    abstract fun bodyRecordDao(): BodyRecordDao
    abstract fun appMetaDao(): AppMetaDao
    abstract fun exerciseDao(): ExerciseDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /**
         * **파괴적 마이그레이션을 쓰지 않는다.**
         *
         * 예전에는 `fallbackToDestructiveMigration()`이 붙어 있어서 스키마를 바꿀 때마다
         * 기록이 통째로 초기화됐다. 앞으로 할 일(카탈로그 계층, 시간형 세트, 목표 프로필)이
         * 전부 스키마 변경이라 그대로 두면 작업할 때마다 데이터를 잃는다.
         *
         * 그래서 버전을 올릴 때는 반드시 [MIGRATIONS]에 이주 코드를 하나 추가해야 한다.
         * 빠뜨리면 앱이 열리다 죽으므로 조용히 넘어가지 않는다.
         */
        internal val MIGRATIONS = arrayOf(
            MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
            MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14,
            MIGRATION_14_15, MIGRATION_15_16
        )

        /** 마이그레이션 테스트가 참조한다. `@Database`의 version과 항상 같아야 한다. */
        internal const val VERSION = 16

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "leanbulk.db"
                ).addMigrations(*MIGRATIONS).build().also { instance = it }
            }
    }
}
