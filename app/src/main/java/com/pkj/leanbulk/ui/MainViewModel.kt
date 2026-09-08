package com.pkj.leanbulk.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pkj.leanbulk.data.AmountUnit
import com.pkj.leanbulk.data.BodyRecord
import com.pkj.leanbulk.data.DietTarget
import com.pkj.leanbulk.data.Exercise
import com.pkj.leanbulk.data.ExerciseWithSets
import com.pkj.leanbulk.data.FoodItem
import com.pkj.leanbulk.data.MacroSource
import com.pkj.leanbulk.data.Macros
import com.pkj.leanbulk.data.MealEntry
import com.pkj.leanbulk.data.MealPresetFood
import com.pkj.leanbulk.data.MealPresetWithFoods
import com.pkj.leanbulk.data.Repository
import com.pkj.leanbulk.data.RoutineWithExercises
import com.pkj.leanbulk.data.SeedData
import com.pkj.leanbulk.data.TrackingType
import com.pkj.leanbulk.data.WorkoutSession
import com.pkj.leanbulk.data.backup.DietTemplateDto
import com.pkj.leanbulk.data.backup.ExerciseDto
import com.pkj.leanbulk.data.backup.FoodDto
import com.pkj.leanbulk.data.backup.RoutineDto
import com.pkj.leanbulk.data.backup.Snapshot
import com.pkj.leanbulk.notify.NoRestAlarms
import com.pkj.leanbulk.notify.RestAlarms
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.stateIn
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** 편집이 멈춘 뒤 이만큼 지나면 저장한다. */
private const val SAVE_DELAY_MS = 700L

class MainViewModel(
    private val repository: Repository,
    /** 휴식 끝을 알려줄 물건. 앱이 뒤에 있거나 죽어도 울려야 해서 시스템 알람에 맡긴다. */
    private val restAlarms: RestAlarms = NoRestAlarms
) : ViewModel() {

    /** 자정을 넘기거나 앱이 다시 앞으로 나올 때마다 갱신되는 "오늘". */
    private val _today = MutableStateFlow(LocalDate.now().format(DATE_FMT))
    val today: StateFlow<String> = _today

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast

    /** 마지막으로 자동 저장된 시각. 화면에 "저장됨 14:32"로 보여준다. */
    private val _savedAt = MutableStateFlow<String?>(null)
    val savedAt: StateFlow<String?> = _savedAt

    /** 마지막 자동 저장이 지금 루틴에도 반영됐는지. */
    private val _routineSynced = MutableStateFlow(false)
    val routineSynced: StateFlow<Boolean> = _routineSynced

    private var workoutSaveJob: Job? = null
    private var pendingWorkout: WorkoutSnapshot? = null
    private var dietSaveJob: Job? = null
    private var pendingDiet: DietSnapshot? = null

    private data class WorkoutSnapshot(
        val date: String,
        val draft: List<DraftExercise>,
        val routineName: String
    )

    private data class DietSnapshot(val date: String, val draft: List<DraftMeal>)

    // ===================== 운동 =====================

    private val _workoutDate = MutableStateFlow(_today.value)
    val workoutDate: StateFlow<String> = _workoutDate
    private var workoutDatePicked = false

    private val _routineName = MutableStateFlow("")
    val routineName: StateFlow<String> = _routineName

    private val _workoutDraft = MutableStateFlow<List<DraftExercise>>(emptyList())
    val workoutDraft: StateFlow<List<DraftExercise>> = _workoutDraft

    /** 보고 있는 날짜의 운동 시작·종료 기록. 없으면 아직 시작 안 한 날이다. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val workoutSession: StateFlow<WorkoutSession?> = _workoutDate
        .flatMapLatest { repository.observeWorkoutSession(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _restTimer = MutableStateFlow<RestTimer?>(null)
    val restTimer: StateFlow<RestTimer?> = _restTimer

    /** 다음 휴식의 길이(초). −/+로 고친 값이 여기 남아 다음 세트에 그대로 쓰인다. */
    private val _restSeconds = MutableStateFlow(com.pkj.leanbulk.data.DEFAULT_REST_SECONDS)
    val restSeconds: StateFlow<Int> = _restSeconds

    /**
     * 매초 갱신되는 "지금". 경과 시간과 남은 시간은 이 값과 저장된 시각의 차이로 매번
     * 계산한다 — 남은 초를 세어 내려가면 앱이 뒤로 가 있는 동안 시간이 멈춘다.
     */
    private val _now = MutableStateFlow(System.currentTimeMillis())
    val now: StateFlow<Long> = _now

    /**
     * 시계를 계속 세야 하는지.
     *
     * [workoutSession]을 그대로 보지 않고 따로 들고 있는 이유는 **Room 흐름이 한 박자 늦기
     * 때문이다.** 시작을 누른 직후엔 아직 옛 값(없음)이 흐르고 있어서, 그걸 보고 판단하면
     * 시계가 첫 틱에 곧바로 멈춘다.
     */
    private val workoutRunning = MutableStateFlow(false)

    private var tickerJob: Job? = null

    // ===================== 식단 =====================

    private val _dietDate = MutableStateFlow(_today.value)
    val dietDate: StateFlow<String> = _dietDate
    private var dietDatePicked = false

    private val _dietDraft = MutableStateFlow<List<DraftMeal>>(emptyList())
    val dietDraft: StateFlow<List<DraftMeal>> = _dietDraft

    // ===================== 조회용 =====================

    val allMeals: StateFlow<List<MealEntry>> =
        repository.observeAllMeals().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allWorkouts: StateFlow<List<ExerciseWithSets>> =
        repository.observeAllWorkouts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 히스토리에서 그날 운동을 얼마나 했는지 같이 보여주려고 전부 들고 있는다. */
    val allWorkoutSessions: StateFlow<List<WorkoutSession>> =
        repository.observeAllWorkoutSessions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bodyRecords: StateFlow<List<BodyRecord>> =
        repository.observeBodyRecords().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val routines: StateFlow<List<RoutineWithExercises>> =
        repository.observeRoutines().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val foods: StateFlow<List<FoodItem>> =
        repository.observeFoods().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mealPresets: StateFlow<List<MealPresetWithFoods>> =
        repository.observeMealPresets().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 종목 선택 목록. */
    val exercises: StateFlow<List<Exercise>> =
        repository.observeExercises().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val target: StateFlow<DietTarget?> =
        repository.observeTarget().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            repository.applySeeds()
            _restSeconds.value = repository.restSeconds()
        }
        loadWorkoutDraft(_today.value)
        resumeTickerIfRunning()
        loadDietDraft(_today.value)
        startMidnightWatcher()
    }

    // ===================== 날짜 =====================

    /** 앱을 켜둔 채 자정을 넘겨도 날짜가 넘어가도록 다음 자정까지 기다렸다가 갱신한다. */
    private fun startMidnightWatcher() {
        viewModelScope.launch {
            while (true) {
                val now = LocalDateTime.now()
                val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
                delay(Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1_000) + 1_000)
                refreshToday()
            }
        }
    }

    /** 앱이 백그라운드에서 돌아왔을 때도 호출된다. 날짜가 바뀌었으면 오늘 기준을 옮긴다. */
    fun refreshToday() {
        // 뒤에 있는 동안 시계가 멈춰 있었을 수 있다. 돌고 있던 운동이면 다시 세게 한다.
        resumeTickerIfRunning()

        val current = LocalDate.now().format(DATE_FMT)
        if (current == _today.value) return
        _today.value = current

        // 직접 고른 날짜가 아니라면 새 오늘로 따라간다.
        if (!workoutDatePicked) {
            _workoutDate.value = current
            loadWorkoutDraft(current)
        }
        if (!dietDatePicked) {
            _dietDate.value = current
            loadDietDraft(current)
        }
    }

    // ===================== 운동 편집 =====================

    fun setWorkoutDate(date: String) {
        workoutDatePicked = date != _today.value
        viewModelScope.launch {
            flushWorkoutSave() // 날짜를 옮기기 전에 편집 중이던 내용을 먼저 확정 저장한다.
            _workoutDate.value = date
            loadWorkoutDraft(date)
            // 다른 날로 옮기면 그날 휴식은 남의 얘기다. 그 날짜가 돌고 있으면 시계만 잇는다.
            _restTimer.value = null
            resumeTickerIfRunning()
        }
    }

    /** 앱을 다시 켰을 때 돌고 있던 운동 시계를 이어서 세게 한다. */
    private fun resumeTickerIfRunning() {
        viewModelScope.launch {
            workoutRunning.value = repository.workoutSession(_workoutDate.value)?.running == true
            if (workoutRunning.value) ensureTicker()
        }
    }

    private fun loadWorkoutDraft(date: String) {
        viewModelScope.launch {
            // 다른 날·다른 루틴을 여는 순간 "반영됨" 표시는 옛말이 된다.
            _routineSynced.value = false
            val saved = repository.observeWorkout(date).first()
            if (saved.isEmpty()) {
                _workoutDraft.value = emptyList()
                _routineName.value = ""
            } else {
                _workoutDraft.value = saved.map { it.toDraft() }
                _routineName.value = saved.first().exercise.routineName
            }
        }
    }

    fun loadRoutine(routine: RoutineWithExercises) {
        _routineSynced.value = false
        _routineName.value = routine.routine.name
        _workoutDraft.value = routine.exercises.sortedBy { it.exercise.orderIndex }.map { it.toDraft() }
        scheduleWorkoutSave()
    }

    /**
     * 빈 루틴을 만든다.
     *
     * 이름이 겹치면 만들지 않는다 — 목록에 같은 이름이 둘 있으면 어느 쪽을 고르는 건지
     * 알 수 없다. 덮어쓰려는 것이라면 [overwriteRoutine]이 따로 있다.
     */
    /**
     * 지금 기록에서 루틴 연결만 뗀다. **종목은 그대로 둔다** — 루틴에 반영되는 게 싫을 뿐
     * 오늘 한 운동을 지우려는 게 아니다.
     *
     * 떼고 나면 자동 반영도 멈춘다([syncRoutine]이 이름으로 루틴을 찾기 때문).
     */
    fun clearRoutine() {
        if (_routineName.value.isBlank()) return
        _routineName.value = ""
        _routineSynced.value = false
        scheduleWorkoutSave()
    }

    fun addRoutine(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            if (repository.routineNameExists(trimmed)) {
                _toast.value = "'$trimmed' 루틴이 이미 있습니다"
                return@launch
            }
            repository.addRoutine(trimmed)
        }
    }

    /** 지금 화면의 종목 구성을 새 루틴으로 저장한다. 이름이 겹치면 거절한다([addRoutine]과 같은 이유). */
    fun saveCurrentAsRoutine(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank() || _workoutDraft.value.isEmpty()) return
        viewModelScope.launch {
            if (repository.routineNameExists(trimmed)) {
                _toast.value = "'$trimmed' 루틴이 이미 있습니다"
                return@launch
            }
            repository.saveAsNewRoutine(trimmed, _workoutDraft.value.toRoutineExercises())
            _routineName.value = trimmed
            _toast.value = "루틴 '$trimmed' 저장됨"
        }
    }

    /** 지금 화면의 종목 구성으로 기존 루틴을 덮어쓴다. */
    fun overwriteRoutine(routineId: Long, routineName: String) {
        if (_workoutDraft.value.isEmpty()) return
        viewModelScope.launch {
            repository.overwriteRoutine(routineId, _workoutDraft.value.toRoutineExercises())
            _routineName.value = routineName
            _toast.value = "루틴 '$routineName' 갱신됨"
        }
    }

    fun renameRoutine(routineId: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            if (repository.routineNameExists(trimmed, exceptId = routineId)) {
                _toast.value = "'$trimmed' 루틴이 이미 있습니다"
                return@launch
            }
            repository.renameRoutine(routineId, trimmed)
        }
    }

    fun deleteRoutine(routineId: Long) {
        viewModelScope.launch { repository.deleteRoutine(routineId) }
    }

    /** 카탈로그에서 고른 종목을 오늘 운동에 넣는다. 추적 방식이 같이 딸려온다. */
    fun addExercise(exercise: Exercise) {
        _workoutDraft.value = _workoutDraft.value + exercise.toDraft()
        scheduleWorkoutSave()
    }

    /**
     * 카탈로그에 없는 종목을 직접 등록해서 바로 넣는다.
     * 등록한 종목은 카탈로그에 남아 다음부터 검색된다.
     */
    fun addNewExercise(name: String, bodyPart: String, equipment: String, trackingType: TrackingType) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val created = repository.addUserExercise(name, bodyPart, equipment, trackingType)
            _workoutDraft.value = _workoutDraft.value + created.toDraft()
            scheduleWorkoutSave()
        }
    }

    fun removeExercise(index: Int) {
        _workoutDraft.value = _workoutDraft.value.filterIndexed { i, _ -> i != index }
        scheduleWorkoutSave()
    }

    /**
     * 종목 순서를 한 칸 옮긴다.
     *
     * 추가는 항상 맨 아래에 붙으므로, 원하는 자리로 **끌어올리는 수단**이 없으면
     * 중간에 넣을 방법이 아예 없다. 저장할 때 `orderIndex`는 리스트 순서에서
     * 다시 매겨지므로([toPersistables]) 여기서는 리스트만 바꾸면 된다.
     */
    fun moveExercise(from: Int, to: Int) {
        val current = _workoutDraft.value
        if (from !in current.indices || to !in current.indices || from == to) return
        _workoutDraft.value = current.toMutableList().apply { add(to, removeAt(from)) }
        scheduleWorkoutSave()
    }

    /** 세트 추가: 바로 위 세트의 무게/횟수를 그대로 복사한다. */
    fun addSet(exerciseIndex: Int) {
        _workoutDraft.value = _workoutDraft.value.mapIndexed { i, ex ->
            if (i != exerciseIndex) ex
            else ex.copy(sets = ex.sets + (ex.sets.lastOrNull() ?: DraftSet(0.0, 10)).copy(done = false))
        }
        scheduleWorkoutSave()
    }

    fun removeSet(exerciseIndex: Int) {
        _workoutDraft.value = _workoutDraft.value.mapIndexed { i, ex ->
            if (i != exerciseIndex || ex.sets.size <= 1) ex else ex.copy(sets = ex.sets.dropLast(1))
        }
        scheduleWorkoutSave()
    }

    /** 종목 메모를 고친다. 빈 글자를 주면 메모를 지우는 것이다. */
    fun setExerciseMemo(exerciseIndex: Int, memo: String) {
        _workoutDraft.value = _workoutDraft.value.mapIndexed { i, ex ->
            if (i == exerciseIndex) ex.copy(memo = memo.trim()) else ex
        }
        scheduleWorkoutSave()
    }

    fun updateSet(exerciseIndex: Int, setIndex: Int, transform: (DraftSet) -> DraftSet) {
        val before = _workoutDraft.value.getOrNull(exerciseIndex)?.sets?.getOrNull(setIndex)
        _workoutDraft.value = _workoutDraft.value.mapIndexed { i, ex ->
            if (i != exerciseIndex) ex
            else ex.copy(sets = ex.sets.mapIndexed { j, s -> if (j == setIndex) transform(s) else s })
        }
        scheduleWorkoutSave()

        // 세트를 막 끝냈을 때만 휴식을 건다. 체크를 푸는 것(잘못 눌렀을 때)이나 무게·횟수를
        // 고치는 것으로 타이머가 도로 켜지면 안 된다.
        val after = _workoutDraft.value.getOrNull(exerciseIndex)?.sets?.getOrNull(setIndex)
        if (before != null && after != null && !before.done && after.done) {
            startRest(_workoutDraft.value[exerciseIndex].name)
        }
    }

    // ---------- 운동 시간 ----------

    /**
     * 운동을 시작하거나, 멈춰 둔 것을 이어서 한다.
     *
     * 이어서 할 땐 **멈춰 있던 시간은 안 센다** — 지금부터 새 구간을 시작하고 이미 잰 시간에
     * 이어 붙인다([WorkoutSession.resumedAt]). 처음 시작한 시각은 기록이라 다시 쓰지 않는다.
     */
    fun startWorkout() {
        val date = _workoutDate.value
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val existing = repository.workoutSession(date)
            repository.saveWorkoutSession(
                existing?.resumedAt(now)
                    ?: WorkoutSession(date = date, startedAtEpochMs = now, resumedAtEpochMs = now)
            )
            workoutRunning.value = true
            ensureTicker()
        }
    }

    /** 운동 시계를 멈춘다. 돌고 있던 휴식도 같이 접는다 — 멈췄는데 쉬라고 세고 있을 이유가 없다. */
    fun endWorkout() {
        val date = _workoutDate.value
        viewModelScope.launch {
            val existing = repository.workoutSession(date) ?: return@launch
            repository.saveWorkoutSession(existing.pausedAt(System.currentTimeMillis()))
            workoutRunning.value = false
            skipRest()
        }
    }

    // ---------- 휴식 타이머 ----------

    private fun startRest(exerciseName: String) {
        val seconds = _restSeconds.value
        val endsAt = System.currentTimeMillis() + seconds * 1000L
        _restTimer.value = RestTimer(
            exerciseName = exerciseName,
            endsAtEpochMs = endsAt,
            totalSec = seconds
        )
        restAlarms.schedule(endsAt, exerciseName)
        ensureTicker()
    }

    /**
     * 도는 중인 타이머를 [deltaSec]만큼 늘리거나 줄인다. 고친 길이는 [restSeconds]에도
     * 남아 다음 세트부터 그대로 쓰인다 — 매번 같은 만큼 더 쉬는데 매번 눌러야 하면 안 된다.
     *
     * 0초 밑으로는 안 내려간다. 마이너스 휴식은 뜻이 없고, 줄여서 0이 되면 그건 건너뛴 것이다.
     */
    fun adjustRest(deltaSec: Int) {
        val timer = _restTimer.value ?: return
        val remaining = ((timer.endsAtEpochMs - System.currentTimeMillis()) / 1000.0).toInt()
        val newRemaining = (remaining + deltaSec).coerceAtLeast(0)
        _restSeconds.value = (_restSeconds.value + deltaSec).coerceAtLeast(REST_STEP_SEC)
        viewModelScope.launch { repository.setRestSeconds(_restSeconds.value) }

        if (newRemaining == 0) {
            skipRest()
            return
        }
        val endsAt = System.currentTimeMillis() + newRemaining * 1000L
        _restTimer.value = timer.copy(
            endsAtEpochMs = endsAt,
            totalSec = maxOf(timer.totalSec, newRemaining)
        )
        // 알람도 같이 옮긴다. 안 그러면 화면은 1:50인데 30초 뒤에 울린다.
        restAlarms.schedule(endsAt, timer.exerciseName)
    }

    /** 휴식을 그만둔다. 다 쉬었든 안 쉬었든 화면에서 치우는 건 같은 일이다. */
    fun skipRest() {
        _restTimer.value = null
        restAlarms.cancel()
    }

    /** 창을 내려도 타이머는 계속 돈다 — 접는 건 보기 싫다는 뜻이지 그만두겠다는 뜻이 아니다. */
    fun setRestExpanded(expanded: Boolean) {
        _restTimer.value = _restTimer.value?.copy(expanded = expanded)
    }

    /**
     * 매초 [now]를 갱신하는 코루틴. **셀 게 있을 때만 돈다** — 운동도 안 하고 쉬지도 않는데
     * 1초마다 화면을 다시 그리면 배터리만 먹는다.
     */
    private fun ensureTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = viewModelScope.launch {
            while (true) {
                val nowMs = System.currentTimeMillis()
                _now.value = nowMs

                // 다 쉬었으면 화면에서만 치운다. **알리는 건 여기서 안 한다** —
                // 앱이 뒤에 있으면 이 코루틴은 돌지도 않으므로, 울리는 일은 전부
                // 시스템 알람([RestAlarms])이 맡는다. 여기서 또 울리면 두 번 울린다.
                val timer = _restTimer.value
                if (timer != null && nowMs >= timer.endsAtEpochMs) _restTimer.value = null

                if (_restTimer.value == null && !workoutRunning.value) break
                delay(1000)
            }
        }
    }

    /**
     * 편집이 멈추고 잠깐 뒤에 저장한다.
     * 글자 하나 칠 때마다 DB에 쓰면 느려지므로 마지막 편집만 반영한다.
     */
    private fun scheduleWorkoutSave() {
        val snapshot = WorkoutSnapshot(_workoutDate.value, _workoutDraft.value, _routineName.value)
        pendingWorkout = snapshot
        workoutSaveJob?.cancel()
        workoutSaveJob = viewModelScope.launch {
            delay(SAVE_DELAY_MS)
            writeWorkout(snapshot)
        }
    }

    /** 날짜를 옮기기 전처럼, 대기 중인 저장을 즉시 끝내야 할 때 쓴다. */
    private suspend fun flushWorkoutSave() {
        workoutSaveJob?.cancel()
        pendingWorkout?.let { writeWorkout(it) }
    }

    private suspend fun writeWorkout(s: WorkoutSnapshot) {
        repository.saveWorkoutDay(s.date, s.draft.toPersistables(s.date, s.routineName))
        syncRoutine(s)
        pendingWorkout = null
        _savedAt.value = LocalDateTime.now().format(TIME_FMT)
    }

    /**
     * 오늘 고친 구성을 그 루틴에 그대로 반영한다. 다음에 그 루틴을 불러오면 최신 상태가 나온다.
     *
     * **오늘 날짜일 때만 반영한다.** 지난 기록을 들춰보다 오타 하나 고친 것으로 지금 쓰는
     * 루틴이 몇 주 전 무게로 되돌아가면 안 된다. 반영 여부는 [routineSynced]로 화면에 알린다
     * — 조용히 남의 데이터를 고치는 동작이라 사용자가 모르면 안 된다.
     */
    private suspend fun syncRoutine(s: WorkoutSnapshot) {
        if (s.date != _today.value) {
            _routineSynced.value = false
            return
        }
        _routineSynced.value = repository.syncRoutineByName(s.routineName, s.draft.toRoutineExercises())
    }

    // ===================== 식단 편집 =====================

    fun setDietDate(date: String) {
        dietDatePicked = date != _today.value
        viewModelScope.launch {
            flushDietSave() // 날짜를 옮기기 전에 편집 중이던 내용을 먼저 확정 저장한다.
            _dietDate.value = date
            loadDietDraft(date)
        }
    }

    /**
     * 아침·점심·저녁·간식 네 칸은 저장된 기록이 있든 없든 **항상** 띄운다(DIET-REDESIGN.md §9.1).
     * 아무것도 안 담으면 [scheduleDietSave]가 안 불려서 DB에는 안 남는다 — 그냥 열어만 봐도
     * 빈 끼니가 생기면 안 되기 때문이다. 5번째부터는 "끼니 추가"(+ 버튼)로 만든다.
     */
    private fun loadDietDraft(date: String) {
        viewModelScope.launch {
            val saved = repository.observeMealsWithFoods(date).first()
            _dietDraft.value = saved.sortedBy { it.entry.orderIndex }.map { it.toDraft() }.intoFixedSlots()
        }
    }

    /** 5번째 이후의 끼니(야식 등)를 만든다. 기본 네 칸은 [intoFixedSlots]가 늘 채워둔다. */
    fun addMeal(slotName: String) {
        val name = slotName.ifBlank { "끼니 ${_dietDraft.value.size + 1}" }
        _dietDraft.value = _dietDraft.value + DraftMeal(slotName = name.trim())
        scheduleDietSave()
    }

    /** 추가로 만든 끼니만 지운다. 고정 네 칸은 화면에서 삭제 버튼 자체를 안 준다. */
    fun removeMeal(index: Int) {
        if (index < FIXED_MEAL_SLOTS.size) return
        _dietDraft.value = _dietDraft.value.filterIndexed { i, _ -> i != index }
        scheduleDietSave()
    }

    private fun updateMeal(index: Int, transform: (DraftMeal) -> DraftMeal) {
        _dietDraft.value = _dietDraft.value.mapIndexed { i, m -> if (i == index) transform(m) else m }
        scheduleDietSave()
    }

    /**
     * 끼니 메모를 고친다. 빈 글자를 주면 메모를 지우는 것이다.
     *
     * 음식 줄과 달리 [DraftMeal.recomputeFromFoods]를 거치지 않는다 — 메모는 영양 합계의
     * 재료가 아니라 그 옆에 따로 붙는 글이다.
     */
    fun setMealMemo(index: Int, memo: String) {
        updateMeal(index) { it.copy(memo = memo.trim()) }
    }

    fun updateFood(food: FoodItem) {
        viewModelScope.launch { repository.updateFood(food) }
    }

    /** 고른 음식들을 목록에서 지운다. 이미 기록한 끼니는 그대로 남는다. */
    fun deleteFoods(ids: List<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.deleteFoods(ids)
            _toast.value = "음식 ${ids.size}개 지움"
        }
    }

    fun addFoodToMeal(index: Int, food: FoodItem, multiplier: Double) {
        updateMeal(index) { meal ->
            meal.copy(foods = meal.foods + food.toDraftMealFood(multiplier)).recomputeFromFoods()
        }
    }

    fun removeFoodFromMeal(mealIndex: Int, foodIndex: Int) {
        updateMeal(mealIndex) { meal ->
            meal.copy(foods = meal.foods.filterIndexed { i, _ -> i != foodIndex }).recomputeFromFoods()
        }
    }

    /** 음식 줄 순서를 옮긴다. 운동 종목 순서 조정과 같은 이유 — 담는 순서는 늘 맨 아래다. */
    fun moveFoodInMeal(mealIndex: Int, from: Int, to: Int) {
        updateMeal(mealIndex) { meal ->
            if (from !in meal.foods.indices || to !in meal.foods.indices || from == to) meal
            else meal.copy(foods = meal.foods.toMutableList().apply { add(to, removeAt(from)) }).recomputeFromFoods()
        }
    }

    fun toggleFoodFavorite(food: FoodItem) {
        viewModelScope.launch { repository.setFoodFavorite(food.id, !food.favorite) }
    }

    /** "직접 등록"으로 카탈로그에 새 음식을 만든다. 만든 음식은 곧바로 수량을 정해 담을 수 있게 돌려준다. */
    suspend fun addNewFood(
        name: String,
        category: String,
        macros: Macros,
        baseAmount: Double?,
        baseAmountUnit: AmountUnit
    ): FoodItem = repository.addUserFood(name, category, macros, baseAmount, baseAmountUnit)

    /**
     * "내 식사"(식사세트)를 끼니에 통째로 담는다.
     *
     * `foodId`가 살아 있으면 **그 순간 카탈로그 값으로 다시 계산한다**(DIET-REDESIGN.md §9.3) —
     * 저장해둔 뒤 카탈로그 값이 바뀌었을 수 있어서다. 카탈로그에서 지워졌으면(`foodId`가 이제
     * 없거나 못 찾으면) 세트에 저장해둔 매크로 스냅샷을 그대로 쓴다.
     */
    fun addPresetToMeal(mealIndex: Int, preset: MealPresetWithFoods) {
        val catalogById = foods.value.associateBy { it.id }
        val newFoods = preset.foods.sortedBy { it.orderIndex }.map { f ->
            val catalogFood = f.foodId?.let { catalogById[it] }
            if (catalogFood != null) {
                catalogFood.toDraftMealFood(f.multiplier)
            } else {
                DraftMealFood(
                    foodId = null,
                    name = f.name,
                    multiplier = f.multiplier,
                    amount = f.amount,
                    amountUnit = f.amountUnit,
                    macros = f.macros,
                    macroSource = f.macroSource
                )
            }
        }
        updateMeal(mealIndex) { meal -> meal.copy(foods = meal.foods + newFoods).recomputeFromFoods() }
    }

    /** 지금 끼니에 담긴 음식 줄 그대로를 "내 식사"로 저장한다. */
    fun saveMealAsPreset(mealIndex: Int, name: String) {
        val trimmed = name.trim()
        val meal = _dietDraft.value.getOrNull(mealIndex) ?: return
        if (trimmed.isBlank() || meal.foods.isEmpty()) return
        viewModelScope.launch {
            repository.saveAsMealPreset(
                trimmed,
                meal.foods.mapIndexed { index, f ->
                    MealPresetFood(
                        presetId = 0,
                        orderIndex = index,
                        foodId = f.foodId,
                        name = f.name,
                        multiplier = f.multiplier,
                        amount = f.amount,
                        amountUnit = f.amountUnit,
                        macros = f.macros,
                        macroSource = f.macroSource
                    )
                }
            )
            _toast.value = "내 식사 '$trimmed' 저장됨"
        }
    }

    fun renameMealPreset(id: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch { repository.renameMealPreset(id, trimmed) }
    }

    fun deleteMealPreset(id: Long) {
        viewModelScope.launch { repository.deleteMealPreset(id) }
    }

    private fun scheduleDietSave() {
        val snapshot = DietSnapshot(_dietDate.value, _dietDraft.value)
        pendingDiet = snapshot
        dietSaveJob?.cancel()
        dietSaveJob = viewModelScope.launch {
            delay(SAVE_DELAY_MS)
            writeDiet(snapshot)
        }
    }

    private suspend fun flushDietSave() {
        dietSaveJob?.cancel()
        pendingDiet?.let { writeDiet(it) }
    }

    private suspend fun writeDiet(s: DietSnapshot) {
        repository.saveMealDay(s.date, s.draft.toMealEntryPairs(s.date))
        pendingDiet = null
        _savedAt.value = LocalDateTime.now().format(TIME_FMT)
    }

    /** 앱이 백그라운드로 갈 때 대기 중인 저장을 잃지 않도록 즉시 확정한다. */
    fun flushPendingSaves() {
        viewModelScope.launch {
            flushDietSave()
            flushWorkoutSave()
        }
    }

    // ===================== 신체 기록 =====================

    /**
     * 체지방률은 직접 적지 않았으면 체중·체지방량으로 계산해준다.
     * 둘 중 하나라도 비어 있거나 체중이 0이면 계산하지 않고 비워둔다.
     */
    fun saveBodyRecord(record: BodyRecord) {
        val percent = record.fatPercent
            ?: record.fatMassKg?.takeIf { record.weightKg > 0 }?.let { it / record.weightKg * 100 }
        viewModelScope.launch {
            repository.upsertBodyRecord(record.copy(fatPercent = percent))
            _toast.value = "${record.date} 신체 기록 저장됨"
        }
    }

    fun deleteBodyRecord(id: Long) {
        viewModelScope.launch { repository.deleteBodyRecord(id) }
    }

    // ===================== 목표 =====================

    fun saveTarget(target: DietTarget) {
        viewModelScope.launch {
            repository.saveTarget(target)
            _toast.value = "목표 저장됨"
        }
    }

    /**
     * 목표 화면이 "권장값"을 보여주는 데 쓰는 최근 체중.
     *
     * 없으면 null이고, 그때는 권장값을 계산할 수 없다고 알려준다 — 아무 체중이나
     * 가정해서 숫자를 만들어내면 그럴듯하지만 틀린 목표가 된다.
     */
    val latestWeightKg: StateFlow<Double?> = bodyRecords
        .map { records -> records.maxByOrNull { it.date }?.weightKg }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun consumeToast() {
        _toast.value = null
    }

    // ===================== 백업 / 복원 =====================

    /** 지금 DB 전체를 스냅샷으로 뜬다. 파일로 굽는 건 화면 쪽에서 한다. */
    suspend fun buildSnapshot(): Snapshot =
        repository.exportSnapshot(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))

    /**
     * 스냅샷으로 기록을 통째로 바꾼다.
     *
     * 순서가 중요해서 한 함수로 묶었다. 특히 **대기 중인 자동 저장을 먼저 버려야 한다** —
     * 편집 중이던 draft는 DB와 무관한 메모리 상태라, 그냥 두면 불러오기가 끝난 0.7초 뒤에
     * 옛 draft가 저장되면서 방금 불러온 기록을 덮어쓴다.
     *
     * [saveRollbackPoint]가 실패하면 되돌릴 수단이 없는 채로 덮어쓰게 되므로,
     * 예외를 삼키지 않고 그대로 올려서 불러오기 자체를 중단시킨다.
     */
    /**
     * 백업 파일에서 고른 것만 더한다. 되돌림점을 안 남기는 이유는 **아무것도 지우지 않기**
     * 때문이다 — 마음에 안 들면 들어온 루틴을 지우면 그만이다.
     */
    suspend fun mergeFromSnapshot(
        catalog: List<ExerciseDto>,
        routines: List<RoutineDto>,
        dietTemplates: List<DietTemplateDto>,
        exercises: List<ExerciseDto>,
        foods: List<FoodDto>
    ): Repository.MergeResult =
        repository.mergeFromSnapshot(catalog, routines, dietTemplates, exercises, foods)

    /** 직접 등록한 음식만 담은 스냅샷. 다음 버전 시드에 넣을 후보를 뽑아내는 용도다. */
    suspend fun buildUserFoodSnapshot(): Snapshot =
        repository.exportUserFoods(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))

    suspend fun applyImport(snapshot: Snapshot, saveRollbackPoint: suspend (Snapshot) -> Unit) {
        workoutSaveJob?.cancel()
        dietSaveJob?.cancel()
        pendingWorkout = null
        pendingDiet = null

        saveRollbackPoint(buildSnapshot())
        repository.importSnapshot(snapshot)

        // 화면이 들고 있는 draft를 새 데이터로 다시 채운다.
        // (Activity.recreate()로는 안 된다 — ViewModel이 설정 변경처럼 살아남아 draft가 그대로다)
        loadWorkoutDraft(_workoutDate.value)
        loadDietDraft(_dietDate.value)
        _savedAt.value = null

        _toast.value = "불러오기 완료"
    }
}
