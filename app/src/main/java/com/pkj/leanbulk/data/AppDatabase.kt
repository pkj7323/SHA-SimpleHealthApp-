package com.pkj.leanbulk.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

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
        BodyRecord::class
    ],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mealDao(): MealDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun routineDao(): RoutineDao
    abstract fun dietTemplateDao(): DietTemplateDao
    abstract fun foodDao(): FoodDao
    abstract fun dietTargetDao(): DietTargetDao
    abstract fun bodyRecordDao(): BodyRecordDao

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
        internal val MIGRATIONS = emptyArray<Migration>()

        /** 마이그레이션 테스트가 참조한다. `@Database`의 version과 항상 같아야 한다. */
        internal const val VERSION = 4

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
