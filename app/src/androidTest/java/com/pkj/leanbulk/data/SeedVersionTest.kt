package com.pkj.leanbulk.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    /**
     * 직접 등록한 음식을 나중에 고칠 수 있어야 한다. **시드 음식을 고치면 사용자 것으로
     * 돌린다** — 안 그러면 다음 시드 갱신이 고친 값을 그대로 덮어써서, 고쳐도 소용없게 된다.
     */
    @Test
    fun 음식을_고치면_다음_시드_갱신이_덮어쓰지_않는다() = runBlocking {
        repo.applySeeds()
        val chicken = requireNotNull(db.foodDao().findBySlug("chicken-breast-100g"))

        assertEquals(
            Repository.UpdateFoodResult.Ok,
            repo.updateFood(chicken.copy(macros = Macros(kcal = 999.0), baseAmount = 123.0))
        )

        // 시드를 처음부터 다시 돌려도(버전을 되돌린 상황) 고친 값이 살아남아야 한다.
        db.appMetaDao().upsert(AppMeta(seedVersion = 0))
        repo.applySeeds()

        val after = requireNotNull(db.foodDao().findBySlug("chicken-breast-100g"))
        assertEquals("고친 값이 시드로 되돌아갔다", 999.0, after.macros.kcal!!, 0.001)
        assertEquals(123.0, after.baseAmount!!, 0.001)
        assertEquals(ItemSource.USER, after.source)
    }

    /** 이름이 유니크라 겹치는 이름으로는 못 고친다. 조용히 실패하지 말고 거절해야 한다. */
    @Test
    fun 이름이_겹치면_고치지_않는다() = runBlocking {
        repo.applySeeds()
        val chicken = requireNotNull(db.foodDao().findBySlug("chicken-breast-100g"))
        val rice = requireNotNull(db.foodDao().findBySlug("rice-brown-200g"))

        assertEquals(
            Repository.UpdateFoodResult.NameTaken(hidden = false),
            repo.updateFood(chicken.copy(name = rice.name))
        )
        assertEquals("거절했는데 이름이 바뀌었다", "닭가슴살", requireNotNull(db.foodDao().findBySlug("chicken-breast-100g")).name)
    }

    /** 우유·이온음료는 ml로 재는 것이라 시드 5단계가 단위를 바로잡아야 한다. */
    @Test
    fun 액체_시드는_ml로_들어온다() = runBlocking {
        repo.applySeeds()

        assertEquals(AmountUnit.ML, requireNotNull(db.foodDao().findBySlug("milk-200ml")).baseAmountUnit)
        assertEquals(AmountUnit.G, requireNotNull(db.foodDao().findBySlug("chicken-breast-100g")).baseAmountUnit)
    }

    @Test
    fun 첫_실행에_시드가_깔리고_버전이_기록된다() = runBlocking {
        assertEquals("아직 아무것도 안 깔린 상태여야 한다", 0, repo.seedVersion())

        repo.applySeeds()

        assertTrue("기본 루틴이 안 깔렸다", repo.observeRoutines().first().isNotEmpty())
        assertTrue("기본 식단표가 안 깔렸다", db.dietTemplateDao().getAll().isNotEmpty())
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
            .map { it.exercise }
            .filter { it.exerciseId == null }
        assertTrue(
            "카탈로그에 못 붙은 루틴 종목이 있다: ${unlinked.map { it.exerciseName }}",
            unlinked.isEmpty()
        )
    }

    /**
     * 시드 루틴의 세트 수가 `defaultSetting` 글과 맞아야 한다.
     *
     * 예전에는 무엇을 적어놨든 불러오면 전부 세 세트로 펴졌다.
     */
    @Test
    fun 시드_루틴이_적힌_세트_수를_그대로_갖는다() = runBlocking<Unit> {
        repo.applySeeds()

        val chest = repo.observeRoutines().first().first { it.routine.name == "가슴 & 삼두 & 코어" }
        val bench = chest.exercises.first { it.exercise.exerciseName == "벤치 프레스 머신 (다리 올림)" }
        assertEquals("'40kg / 12회 / 4세트'인데 세트 수가 다르다", 4, bench.sets.size)
        assertEquals(listOf(40.0, 40.0, 40.0, 40.0), bench.sets.map { it.weightKg })

        val warmup = chest.exercises.first { it.exercise.exerciseName == "시티드 프레스 머신 (견갑 예열)" }
        assertEquals("'20kg / 20회 / 2세트'인데 세트 수가 다르다", 2, warmup.sets.size)
    }

    /** 시간형 종목을 고르면 추적 방식이 따라와야 입력 칸이 바뀐다. */
    @Test
    fun 시간형_종목의_추적방식이_루틴에도_전달된다() = runBlocking {
        repo.applySeeds()

        val carry = repo.observeRoutines().first()
            .flatMap { it.exercises }
            .map { it.exercise }
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

    // ===================== 음식 카탈로그 (시드 3단계) =====================

    @Test
    fun 음식_카탈로그가_깔린다() = runBlocking {
        repo.applySeeds()

        val foods = repo.observeFoods().first()
        assertEquals("음식 카탈로그 수", SeedData.foodCatalog.size, foods.size)
        assertTrue(
            "분류가 여러 개여야 필터가 의미 있다",
            foods.map { it.category }.distinct().size > 3
        )
    }

    /**
     * 음식은 종목과 달리 **이름도 유니크**하다. 카탈로그가 생기기 전부터 쓰면서 쌓인
     * 사용자 음식과 이름이 겹치면 시드를 건너뛰어야 한다 — 직접 적어둔 영양정보가
     * 더 정확할 수 있고, 무엇보다 삽입이 실패하면 안 된다.
     */
    @Test
    fun 이름이_겹치면_사용자_음식을_지킨다() = runBlocking {
        // 시드가 깔리기 전에 사용자가 같은 이름으로 먼저 쌓아둔 상황.
        // 시드 카탈로그의 이름은 "닭가슴살"이다(제공량을 뗐다 — DIET-REDESIGN.md).
        db.foodDao().remember("닭가슴살", Macros(kcal = 999.0, protein = 99.0))

        repo.applySeeds()

        val mine = repo.observeFoods().first().first { it.name == "닭가슴살" }
        assertEquals("사용자가 적은 값이 시드로 덮어써졌다", 999.0, mine.macros.kcal!!, 0.0)
        assertEquals(ItemSource.USER, mine.source)
    }

    /**
     * 음식 이름에서 제공량을 떼고 [FoodItem.baseAmount]으로 옮긴 시드 4단계.
     * 실제로 잰 값이 있는 것만 채워지고, 없는 건 지어내지 않아야 한다.
     */
    @Test
    fun 음식_기준량이_아는_것만_채워진다() = runBlocking {
        repo.applySeeds()
        val foods = repo.observeFoods().first()

        val chicken = foods.first { it.name == "닭가슴살" }
        assertEquals("100g으로 적혀 있던 음식은 기준량을 알아야 한다", 100.0, chicken.baseAmount!!, 0.0)

        val gimbap = foods.first { it.name == "김밥" }
        assertNull("\"1줄\"은 그램을 몰라서 지어내면 안 된다", gimbap.baseAmount)
    }

    /**
     * 시드 재적용이 이름뿐 아니라 기준량도 갱신하는지. 이 앱 전체가 재적용을 "멱등"이라고
     * 가정하므로(`applySeeds`의 각 단계는 여러 번 돌아도 결과가 같아야 한다), 시드
     * 버전을 올리면서 이름을 바꾼 경우가 실제로 반영되는지 확인해야 한다.
     */
    @Test
    fun 시드_재적용시_이름과_기준량이_갱신된다() = runBlocking {
        repo.applySeeds()
        val before = repo.observeFoods().first().first { it.slug == "chicken-breast-100g" }
        assertEquals("닭가슴살", before.name)
        assertEquals(100.0, before.baseAmount!!, 0.0)

        db.appMetaDao().upsert(AppMeta(seedVersion = 0))
        repo.applySeeds()

        val after = repo.observeFoods().first().first { it.slug == "chicken-breast-100g" }
        assertEquals("재적용해도 SEED 항목은 같은 값으로 남아야 한다", "닭가슴살", after.name)
        assertEquals(100.0, after.baseAmount!!, 0.0)
    }

    @Test
    fun 숨긴_시드_음식은_시드를_다시_돌려도_숨은_채로_있다() = runBlocking {
        repo.applySeeds()
        val target = repo.observeFoods().first().first { it.source == ItemSource.SEED }
        repo.removeFood(target)

        db.appMetaDao().upsert(AppMeta(seedVersion = 0))
        repo.applySeeds()

        val after = repo.observeFoods().first().first { it.id == target.id }
        assertTrue("숨긴 음식이 시드 갱신으로 되살아났다", after.hidden)
    }

    /**
     * 숨긴 음식이 **목록 흐름에서 사라지면 안 된다.** 사라지면 이름은 계속 차지한 채
     * (`food_item.name`이 유니크) 화면에서 손댈 수 없어서, 같은 이름으로 새로 만들지도
     * 되돌리지도 못하는 상태에 갇힌다. 실제로 사용자가 여기 걸렸다.
     */
    @Test
    fun 숨긴_음식도_목록에는_남아_되돌릴_수_있다() = runBlocking {
        repo.applySeeds()
        val target = repo.observeFoods().first().first { it.source == ItemSource.SEED }

        repo.removeFood(target)

        val hidden = repo.observeFoods().first().firstOrNull { it.id == target.id }
        assertNotNull("숨겼다고 목록에서 통째로 빠지면 되돌릴 방법이 없다", hidden)
        assertTrue(requireNotNull(hidden).hidden)

        // 이름이 겹친다고 알려줄 때, 상대가 숨겨져 있다는 사실까지 알려줘야 한다.
        val other = repo.observeFoods().first().first { it.id != target.id }
        assertEquals(
            Repository.UpdateFoodResult.NameTaken(hidden = true),
            repo.updateFood(other.copy(name = target.name))
        )

        repo.unhideFood(target.id)
        assertFalse(repo.observeFoods().first().first { it.id == target.id }.hidden)
    }

    /** 직접 등록한 음식은 숨기는 게 아니라 진짜로 지운다 — 지워도 시드가 되살리지 않는다. */
    @Test
    fun 직접_등록한_음식은_지우면_사라진다() = runBlocking {
        repo.applySeeds()
        val mine = repo.addUserFood("내가 만든 음식", "간편식", Macros(kcal = 100.0), null, AmountUnit.G)

        repo.removeFood(mine)

        assertTrue(repo.observeFoods().first().none { it.id == mine.id })
        // 이름이 풀렸으니 같은 이름으로 다시 만들 수 있어야 한다.
        repo.addUserFood("내가 만든 음식", "간편식", Macros(kcal = 200.0), null, AmountUnit.G)
        assertEquals(1, repo.observeFoods().first().count { it.name == "내가 만든 음식" })
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
