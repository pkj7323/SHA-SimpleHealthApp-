package com.pkj.leanbulk.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pkj.leanbulk.data.AppMeta
import com.pkj.leanbulk.data.BodyRecord
import com.pkj.leanbulk.data.DietTarget
import com.pkj.leanbulk.data.DietTemplate
import com.pkj.leanbulk.data.DietTemplateMeal
import com.pkj.leanbulk.data.Exercise
import com.pkj.leanbulk.data.FoodItem
import com.pkj.leanbulk.data.Macros
import com.pkj.leanbulk.data.MealEntry
import com.pkj.leanbulk.data.MealFood
import com.pkj.leanbulk.data.Routine
import com.pkj.leanbulk.data.RoutineExercise
import com.pkj.leanbulk.data.RoutineSet
import com.pkj.leanbulk.data.WorkoutExercise
import com.pkj.leanbulk.data.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Modifier

/**
 * 엔티티에 필드를 추가하고 스냅샷 DTO 반영을 깜빡하는 사고를 막는다.
 *
 * 엔티티가 바뀌면 이 테스트가 깨진다. 깨졌을 때 할 일은 두 가지다:
 *  1. 새 필드를 `Snapshot.kt`의 해당 DTO와 `SnapshotCodec`의 양방향 변환에 추가한다
 *  2. 아래 기대 목록을 갱신한다
 *
 * 2번만 하고 1번을 빼먹으면 `SnapshotRoundTripTest`가 잡는다. 두 테스트가 서로를 받쳐준다.
 */
@RunWith(AndroidJUnit4::class)
class SnapshotCompletenessTest {

    @Test
    fun 엔티티_필드가_바뀌면_스냅샷_DTO도_손봐야_한다() {
        assertFields<Macros>("kcal", "protein", "carbs", "fat", "sugar")

        assertFields<MealEntry>("id", "date", "orderIndex", "slotName", "menuText", "macros", "eaten", "memo",
            "macroSource")
        assertFields<MealFood>(
            "id", "mealEntryId", "orderIndex", "foodId", "name", "multiplier", "amountGram",
            "macros", "macroSource"
        )

        assertFields<WorkoutExercise>(
            "id", "date", "routineName", "orderIndex", "exerciseId", "exerciseName",
            "trackingType", "defaultSetting", "memo"
        )
        assertFields<WorkoutSet>(
            "id", "exerciseId", "setIndex", "weightKg", "reps", "done", "durationSec", "distanceM"
        )

        assertFields<Routine>("id", "name", "orderIndex")
        assertFields<RoutineExercise>(
            "id", "routineId", "orderIndex", "exerciseId", "exerciseName", "trackingType",
            "defaultSetting"
        )
        assertFields<RoutineSet>(
            "id", "routineExerciseId", "setIndex", "weightKg", "reps", "durationSec", "distanceM"
        )

        assertFields<DietTemplate>("id", "name", "orderIndex")
        assertFields<DietTemplateMeal>("id", "templateId", "orderIndex", "slotName", "menuText", "macros")

        assertFields<FoodItem>(
            "id", "slug", "name", "category", "macros", "usedCount", "source", "hidden",
            "baseAmountGram"
        )
        assertFields<AppMeta>("id", "seedVersion")
        assertFields<Exercise>(
            "id", "slug", "name", "bodyPart", "equipment", "trackingType", "source"
        )
        assertFields<DietTarget>(
            "id", "kcal", "protein", "carbs", "fat", "sugar", "goal", "activityLevel"
        )
        assertFields<BodyRecord>(
            "id", "date", "weightKg", "muscleMassKg", "fatMassKg", "fatPercent", "waistCm", "tool", "memo"
        )
    }

    /**
     * 컴파일러가 넣는 합성 필드(`$stable` 등)는 빼고 실제 데이터 필드만 본다.
     */
    private inline fun <reified T> assertFields(vararg expected: String) {
        val actual = T::class.java.declaredFields
            .filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) }
            .map { it.name }
            .toSet()

        assertEquals(
            "${T::class.java.simpleName}의 필드가 바뀌었다. Snapshot.kt의 DTO와 SnapshotCodec의 변환도 같이 고칠 것.",
            expected.toSet(),
            actual
        )
    }
}
