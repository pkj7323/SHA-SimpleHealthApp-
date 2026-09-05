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

@Database(
    entities = [
        MealEntry::class,
        WorkoutExercise::class,
        WorkoutSet::class,
        Routine::class,
        RoutineExercise::class,
        RoutineSet::class,
        DietTemplate::class,
        DietTemplateMeal::class,
        FoodItem::class,
        DietTarget::class,
        BodyRecord::class,
        AppMeta::class,
        Exercise::class
    ],
    version = 10,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mealDao(): MealDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun routineDao(): RoutineDao
    abstract fun dietTemplateDao(): DietTemplateDao
    abstract fun foodDao(): FoodDao
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
            MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10
        )

        /** 마이그레이션 테스트가 참조한다. `@Database`의 version과 항상 같아야 한다. */
        internal const val VERSION = 10

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
