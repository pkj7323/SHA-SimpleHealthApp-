package com.pkj.leanbulk.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 스키마를 바꾸고 마이그레이션을 빠뜨리는 사고를 막는다.
 *
 * 이제 `fallbackToDestructiveMigration()`이 없어서, 버전만 올리고 이주 코드를 안 쓰면
 * **기존 사용자의 앱이 열리다 죽는다.** 개발 중에는 앱을 지웠다 깔면 그만이라 눈치채기
 * 어렵고, 배포하고 나서야 터진다. 그래서 CI가 아니라 테스트로 잡는다.
 *
 * 깨졌을 때 할 일:
 *  1. `AppDatabase.MIGRATIONS`에 `Migration(n, n+1)`을 추가한다
 *  2. `AppDatabase.VERSION`을 `@Database(version = ...)`과 맞춘다
 *  3. 새로 생긴 `app/schemas/.../<n+1>.json`을 **커밋한다** (다음 마이그레이션의 기준선이 된다)
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    /** 내보낸 스키마가 있는 가장 낮은 버전. 이 앱은 4부터 스키마를 남기기 시작했다. */
    private val oldestExportedVersion = 4

    @Test
    fun 가장_오래된_스키마에서_현재_버전까지_이어진다() {
        helper.createDatabase(TEST_DB, oldestExportedVersion).close()

        // 중간에 빠진 마이그레이션이 있으면 여기서 예외가 난다.
        helper.runMigrationsAndValidate(
            TEST_DB,
            AppDatabase.VERSION,
            true,
            *AppDatabase.MIGRATIONS
        ).close()
    }

    /**
     * 마이그레이션의 존재 이유가 이것이다 — **기존 기록이 살아남아야 한다.**
     * 스키마 검증만 통과하고 데이터가 날아가면 파괴적 마이그레이션과 다를 게 없다.
     */
    @Test
    fun 마이그레이션이_기존_기록을_지우지_않는다() {
        helper.createDatabase(TEST_DB, oldestExportedVersion).use { db ->
            db.execSQL("INSERT INTO routine (name, orderIndex) VALUES ('내가 만든 루틴', 0)")
            db.execSQL(
                "INSERT INTO body_record (date, weightKg, fatPercent, tool, memo) " +
                    "VALUES ('2026-08-28', 74.3, 15.07, '인바디', '')"
            )
            db.execSQL(
                "INSERT INTO workout_exercise (id, date, routineName, orderIndex, exerciseName, defaultSetting, memo) " +
                    "VALUES (1, '2026-08-30', '가슴/삼두', 0, '벤치 프레스 머신 (다리 올림)', '5x5', '')"
            )
            db.execSQL(
                "INSERT INTO workout_set (exerciseId, setIndex, weightKg, reps, done) VALUES (1, 0, 62.5, 8, 1)"
            )
            // 음식은 카탈로그가 생기기 전부터 쓰면서 쌓이던 테이블이라, 기존 행이 있는 채로
            // slug를 채우고 유니크 인덱스를 걸어야 한다. 그게 6→7의 위험한 부분이다.
            db.execSQL(
                "INSERT INTO food_item (name, kcal, protein, usedCount) VALUES ('내가 적은 음식', 123.0, 45.0, 7)"
            )
            db.execSQL(
                "INSERT INTO food_item (name, kcal, protein, usedCount) VALUES ('내가 적은 음식 2', 200.0, 10.0, 3)"
            )
        }

        // 4 → 현재 버전까지 사슬을 전부 탄다.
        helper.runMigrationsAndValidate(
            TEST_DB,
            AppDatabase.VERSION,
            true,
            *AppDatabase.MIGRATIONS
        ).use { db ->
            db.query("SELECT name FROM routine").use { c ->
                assertTrue("루틴이 사라졌다", c.moveToFirst())
                assertEquals("내가 만든 루틴", c.getString(0))
            }
            db.query("SELECT weightKg FROM body_record").use { c ->
                assertTrue("신체 기록이 사라졌다", c.moveToFirst())
                assertEquals(74.3, c.getDouble(0), 0.001)
            }
            db.query("SELECT exerciseName FROM workout_exercise").use { c ->
                assertTrue("운동 기록이 사라졌다", c.moveToFirst())
                assertEquals("벤치 프레스 머신 (다리 올림)", c.getString(0))
            }
            db.query("SELECT weightKg, reps, durationSec FROM workout_set").use { c ->
                assertTrue("세트가 사라졌다", c.moveToFirst())
                assertEquals(62.5, c.getDouble(0), 0.001)
                assertEquals(8, c.getInt(1))
                assertTrue("새로 추가된 시간 컬럼은 비어 있어야 한다", c.isNull(2))
            }

            // 기존 음식 행에 slug가 채워지고, 서로 겹치지 않아야 유니크 인덱스가 선다.
            db.query("SELECT name, slug, source, kcal FROM food_item ORDER BY name").use { c ->
                val slugs = mutableSetOf<String>()
                var rows = 0
                while (c.moveToNext()) {
                    rows++
                    val slug = c.getString(1)
                    assertTrue("slug가 비어 있다: ${c.getString(0)}", slug.isNotBlank())
                    assertTrue("slug가 중복됐다: $slug", slugs.add(slug))
                    assertEquals("기존 음식은 사용자 것으로 남아야 한다", "USER", c.getString(2))
                }
                assertEquals("음식이 사라졌다", 2, rows)
            }
            db.query("SELECT kcal FROM food_item WHERE name = '내가 적은 음식'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("직접 적은 영양정보가 바뀌었다", 123.0, c.getDouble(0), 0.001)
            }
        }
    }

    /**
     * 8 → 9는 컬럼을 **지우는** 첫 마이그레이션이다. SQLite 3.19에는 `DROP COLUMN`이 없어서
     * 테이블을 새로 만들어 옮겨 담는데, 그 과정에서 행을 흘리면 카탈로그가 통째로 날아간다.
     *
     * 숨겨져 있던 종목이 **다시 보이는 것**까지가 의도한 결과다. 되돌릴 방법이 없어서
     * 기능을 없앴으므로, 숨김 상태로 갇힌 종목을 그대로 두면 사용자가 영영 못 꺼낸다.
     */
    @Test
    fun 종목_숨김_컬럼을_없애도_종목은_남는다() {
        helper.createDatabase(TEST_DB, 8).use { db ->
            db.execSQL(
                "INSERT INTO exercise (slug, name, bodyPart, equipment, trackingType, source, hidden) " +
                    "VALUES ('bench-press', '벤치 프레스', '가슴', '바벨', 'WEIGHT_REPS', 'SEED', 0)"
            )
            db.execSQL(
                "INSERT INTO exercise (slug, name, bodyPart, equipment, trackingType, source, hidden) " +
                    "VALUES ('user-내-종목', '내 종목', '코어', '맨몸', 'REPS', 'USER', 1)"
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, AppDatabase.VERSION, true, *AppDatabase.MIGRATIONS)
            .use { db ->
                db.query("SELECT slug, name, source FROM exercise ORDER BY slug").use { c ->
                    assertTrue("종목이 사라졌다", c.moveToFirst())
                    assertEquals("bench-press", c.getString(0))
                    assertTrue("숨겨져 있던 종목이 사라졌다", c.moveToNext())
                    assertEquals("내 종목", c.getString(1))
                    assertEquals("사용자가 만든 종목이 시드로 바뀌었다", "USER", c.getString(2))
                }
            }
    }

    /**
     * 9 → 10은 루틴 종목의 기본값 한 쌍을 `routine_set` 행들로 편다.
     *
     * 두 가지가 어긋나기 쉽다:
     *  - 세트를 채운 **뒤에** 부모 테이블을 갈아치우면, 외래키 CASCADE로 방금 넣은 세트가 지워진다
     *  - 종목 id가 유지돼야 세트가 제 부모를 가리킨다
     */
    @Test
    fun 루틴_기본값이_세트로_펴진다() {
        helper.createDatabase(TEST_DB, 9).use { db ->
            db.execSQL("INSERT INTO routine (id, name, orderIndex) VALUES (1, '내 루틴', 0)")
            db.execSQL(
                "INSERT INTO routine_exercise " +
                    "(id, routineId, orderIndex, exerciseName, trackingType, defaultSetting, defaultWeight, defaultReps) " +
                    "VALUES (5, 1, 0, '벤치 프레스', 'WEIGHT_REPS', '5x5', 62.5, 8)"
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, AppDatabase.VERSION, true, *AppDatabase.MIGRATIONS)
            .use { db ->
                db.query("SELECT exerciseName, defaultSetting FROM routine_exercise WHERE id = 5").use { c ->
                    assertTrue("루틴 종목이 사라졌다", c.moveToFirst())
                    assertEquals("벤치 프레스", c.getString(0))
                    assertEquals("메모가 날아갔다", "5x5", c.getString(1))
                }
                db.query(
                    "SELECT setIndex, weightKg, reps FROM routine_set " +
                        "WHERE routineExerciseId = 5 ORDER BY setIndex"
                ).use { c ->
                    var rows = 0
                    while (c.moveToNext()) {
                        assertEquals("세트 번호가 어긋났다", rows, c.getInt(0))
                        assertEquals(62.5, c.getDouble(1), 0.001)
                        assertEquals(8, c.getInt(2))
                        rows++
                    }
                    assertEquals(
                        "옛 기본값이 세트로 안 펴졌다 (CASCADE로 지워졌을 수 있다)",
                        SeedData.DEFAULT_SET_COUNT,
                        rows
                    )
                }
            }
    }

    @Test
    fun 선언한_버전과_실제_DB_버전이_같다() {
        val db = helper.createDatabase(TEST_DB, AppDatabase.VERSION)
        assertEquals(
            "AppDatabase.VERSION과 @Database(version=...)이 어긋났다",
            AppDatabase.VERSION,
            db.version
        )
        db.close()
    }

    /** 버전을 올리면서 스키마 JSON 커밋을 깜빡하는 것도 막는다. */
    @Test
    fun 현재_버전의_스키마가_내보내져_있다() {
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val schemas = assets.list("com.pkj.leanbulk.data.AppDatabase").orEmpty()

        assertTrue(
            "${AppDatabase.VERSION}.json이 없다. app/schemas/를 커밋했는지 확인할 것 " +
                "(있는 것: ${schemas.joinToString()})",
            schemas.contains("${AppDatabase.VERSION}.json")
        )
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
