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

    // ===================== 종목 카탈로그 (시드 2단계) =====================

    @Test
    fun 카탈로그가_깔리고_기존_루틴이_이름으로_연결된다() = runBlocking {
        repo.applySeeds()

        val catalog = repo.observeExercises().first()
        assertEquals("카탈로그 종목 수", SeedData.exerciseCatalog.size, catalog.size)

        // 시드 루틴의 종목명이 카탈로그 이름과 같으므로 전부 연결돼야 한다.
        val unlinked = repo.observeRoutines().first()
            .flatMap { it.exercises }
            .filter { it.exerciseId == null }
        assertTrue(
            "카탈로그에 못 붙은 루틴 종목이 있다: ${unlinked.map { it.exerciseName }}",
            unlinked.isEmpty()
        )
    }

    /** 시간형 종목을 고르면 추적 방식이 따라와야 입력 칸이 바뀐다. */
    @Test
    fun 시간형_종목의_추적방식이_루틴에도_전달된다() = runBlocking {
        repo.applySeeds()

        val carry = repo.observeRoutines().first()
            .flatMap { it.exercises }
            .firstOrNull { it.exerciseName == "수트케이스 캐리" }

        assertTrue("수트케이스 캐리가 시드 루틴에 있어야 한다", carry != null)
        assertEquals(TrackingType.WEIGHT_TIME, carry!!.trackingType)
    }

    @Test
    fun 카탈로그도_여러_번_돌려도_중복되지_않는다() = runBlocking {
        repo.applySeeds()
        val afterFirst = repo.observeExercises().first().size

        db.appMetaDao().upsert(AppMeta(seedVersion = 0)) // 밀린 시드가 있는 상황을 흉내
        repo.applySeeds()

        assertEquals("카탈로그가 중복으로 쌓였다", afterFirst, repo.observeExercises().first().size)
    }

    /**
     * 숨김이 필요한 이유를 못 박는다. SEED 항목은 지워도 다음 시드 실행 때 slug로
     * 되살아나므로, 숨김 상태가 유지돼야 사용자가 치운 종목이 다시 안 나타난다.
     */
    @Test
    fun 숨긴_종목은_시드를_다시_돌려도_숨은_채로_있다() = runBlocking {
        repo.applySeeds()
        val target = repo.observeExercises().first().first()
        repo.hideExercise(target.id)

        val afterHide = repo.observeExercises().first()
        assertTrue("숨긴 종목이 목록에서 빠져야 한다", afterHide.none { it.id == target.id })

        db.appMetaDao().upsert(AppMeta(seedVersion = 0))
        repo.applySeeds()

        assertTrue(
            "숨긴 종목이 시드 갱신으로 되살아났다",
            repo.observeExercises().first().none { it.id == target.id }
        )
    }

    @Test
    fun 사용자가_추가한_종목은_시드_갱신에_안_지워진다() = runBlocking {
        repo.applySeeds()
        val mine = repo.addUserExercise("내가 만든 종목", "코어", "맨몸", TrackingType.TIME)

        db.appMetaDao().upsert(AppMeta(seedVersion = 0))
        repo.applySeeds()

        val found = repo.observeExercises().first().firstOrNull { it.id == mine.id }
        assertTrue("사용자 종목이 사라졌다", found != null)
        assertEquals(TrackingType.TIME, found!!.trackingType)
        assertEquals(ItemSource.USER, found.source)
    }

    /**
     * 반대 방향 — 옛 백업(시드 버전이 낮음)을 불러오면 밀린 시드가 채워져야 한다.
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
