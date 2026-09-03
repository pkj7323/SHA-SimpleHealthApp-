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
 *
 * **버전을 올린 직후 첫 실행은 한 번 헛돈다.** 새 스키마 JSON을 ksp가 만들기 전에 테스트
 * APK의 에셋이 이미 병합돼서, 실패 이유가 "마이그레이션 없음"이 아니라 "스키마 파일 없음"
 * (`loadSchema`)으로 나온다. 한 번 더 돌리면 제대로 된 이유가 나온다.
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
