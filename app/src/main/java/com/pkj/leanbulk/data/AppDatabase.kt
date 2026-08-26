package com.pkj.leanbulk.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
    exportSchema = false
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

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "leanbulk.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
