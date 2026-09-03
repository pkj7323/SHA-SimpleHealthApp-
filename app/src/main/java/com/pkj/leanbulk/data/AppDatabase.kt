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

@Database(
    entities = [
        MealEntry::class,
        WorkoutExercise::class,
        WorkoutSet::class,
        Routine::class,
        RoutineExercise::class,
        DietTemplate::class,
        DietTemplateMeal::class,
        FoodItem::class,
        DietTarget::class,
        BodyRecord::class,
        AppMeta::class,
        Exercise::class
    ],
    version = 8,
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
        internal val MIGRATIONS = arrayOf(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)

        /** 마이그레이션 테스트가 참조한다. `@Database`의 version과 항상 같아야 한다. */
        internal const val VERSION = 8

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
