package com.pkj.leanbulk.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 시드 적용 규칙을 못 박는다.
 *
 * 예전 `ensureXxxSeeded()`는 "테이블이 비어 있으면 넣기"라 최초 1회만 동작했다.
 * 이제는 적용된 시드 버전을 DB에 기록해두고 밀린 단계만 돌린다.
 *
 * 지키려는 두 가지가 서로 반대 방향이라 둘 다 테스트한다:
 *  - 앱을 업데이트해 시드를 늘리면 **기존 사용자에게도 들어가야** 한다
 *  - 그러면서 **사용자가 고치거나 지운 건 되살아나면 안 된다**
 */
@RunWith(AndroidJUnit4::class)
class SeedVersionTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: Repository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repo = Repository(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun 첫_실행에_시드가_깔리고_버전이_기록된다() = runBlocking {
        assertEquals("아직 아무것도 안 깔린 상태여야 한다", 0, repo.seedVersion())

        repo.applySeeds()

        assertTrue("기본 루틴이 안 깔렸다", repo.observeRoutines().first().isNotEmpty())
        assertTrue("기본 식단표가 안 깔렸다", repo.observeDietTemplates().first().isNotEmpty())
        assertTrue("기본 목표가 안 깔렸다", repo.observeTarget().first() != null)
        assertEquals(SeedData.VERSION, repo.seedVersion())
    }

    @Test
    fun 여러_번_돌려도_중복되지_않는다() = runBlocking {
        repo.applySeeds()
        val afterFirst = repo.observeRoutines().first().size

        repo.applySeeds()
        repo.applySeeds()

        assertEquals("시드가 중복으로 쌓였다", afterFirst, repo.observeRoutines().first().size)
    }

    /**
     * 제일 중요한 보장. 기본 루틴이 마음에 안 들어 지웠는데 앱을 열 때마다 되살아나면
     * 쓸 수가 없다.
     */
    @Test
    fun 사용자가_지운_기본_루틴이_되살아나지_않는다() = runBlocking {
        repo.applySeeds()

        val routines = repo.observeRoutines().first()
        repo.deleteRoutine(routines.first().routine.id)
        val afterDelete = repo.observeRoutines().first().size

        repo.applySeeds()

        assertEquals("지운 루틴이 다시 깔렸다", afterDelete, repo.observeRoutines().first().size)
    }

    /**
     * 반대 방향 — 옛 백업(시드 버전이 낮음)을 불러오면 밀린 시드가 채워져야 한다.
     * 여기서는 아직 1단계뿐이라 "다시 돌아간다"는 사실만 확인한다.
     */
    @Test
    fun 시드_버전이_낮으면_다시_적용된다() = runBlocking {
        repo.applySeeds()
        assertEquals(SeedData.VERSION, repo.seedVersion())

        // 옛 백업을 불러온 상황을 흉내낸다.
        db.appMetaDao().upsert(AppMeta(seedVersion = 0))
        assertEquals(0, repo.seedVersion())

        repo.applySeeds()

        assertEquals("밀린 시드를 돌린 뒤 버전이 갱신돼야 한다", SeedData.VERSION, repo.seedVersion())
    }
}
