package com.pkj.leanbulk.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pkj.leanbulk.data.BodyRecord
import com.pkj.leanbulk.data.DietTarget
import com.pkj.leanbulk.data.DietTemplateWithMeals
import com.pkj.leanbulk.data.Exercise
import com.pkj.leanbulk.data.ExerciseWithSets
import com.pkj.leanbulk.data.FoodItem
import com.pkj.leanbulk.data.Macros
import com.pkj.leanbulk.data.MealEntry
import com.pkj.leanbulk.data.Repository
import com.pkj.leanbulk.data.RoutineWithExercises
import com.pkj.leanbulk.data.SeedData
import com.pkj.leanbulk.data.TrackingType
import com.pkj.leanbulk.data.backup.Snapshot
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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

class MainViewModel(private val repository: Repository) : ViewModel() {

    /** 자정을 넘기거나 앱이 다시 앞으로 나올 때마다 갱신되는 "오늘". */
    private val _today = MutableStateFlow(LocalDate.now().format(DATE_FMT))
    val today: StateFlow<String> = _today

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast

    /** 마지막으로 자동 저장된 시각. 화면에 "저장됨 14:32"로 보여준다. */
    private val _savedAt = MutableStateFlow<String?>(null)
    val savedAt: StateFlow<String?> = _savedAt

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

    // ===================== 식단 =====================

    private val _dietDate = MutableStateFlow(_today.value)
    val dietDate: StateFlow<String> = _dietDate
    private var dietDatePicked = false

    private val _dietTemplateName = MutableStateFlow("")
    val dietTemplateName: StateFlow<String> = _dietTemplateName

    private val _dietDraft = MutableStateFlow<List<DraftMeal>>(emptyList())
    val dietDraft: StateFlow<List<DraftMeal>> = _dietDraft

    // ===================== 조회용 =====================

    val allMeals: StateFlow<List<MealEntry>> =
        repository.observeAllMeals().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allWorkouts: StateFlow<List<ExerciseWithSets>> =
        repository.observeAllWorkouts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bodyRecords: StateFlow<List<BodyRecord>> =
        repository.observeBodyRecords().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val routines: StateFlow<List<RoutineWithExercises>> =
        repository.observeRoutines().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dietTemplates: StateFlow<List<DietTemplateWithMeals>> =
        repository.observeDietTemplates().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val foods: StateFlow<List<FoodItem>> =
        repository.observeFoods().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 종목 선택 목록. 사용자가 숨긴 건 빠져 있다. */
    val exercises: StateFlow<List<Exercise>> =
        repository.observeExercises().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val target: StateFlow<DietTarget?> =
        repository.observeTarget().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch { repository.applySeeds() }
        loadWorkoutDraft(_today.value)
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
        }
    }

    private fun loadWorkoutDraft(date: String) {
        viewModelScope.launch {
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
        _routineName.value = routine.routine.name
        _workoutDraft.value = routine.exercises.sortedBy { it.orderIndex }.map { it.toDraft() }
        scheduleWorkoutSave()
    }

    fun addRoutine(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.addRoutine(name.trim()) }
    }

    fun renameRoutine(routineId: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.renameRoutine(routineId, name.trim()) }
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

    /** 목록에서 안 보이게 치운다. 지우면 시드 갱신 때 되살아나므로 숨김으로 처리한다. */
    fun hideExercise(id: Long) {
        viewModelScope.launch { repository.hideExercise(id) }
    }

    fun removeExercise(index: Int) {
        _workoutDraft.value = _workoutDraft.value.filterIndexed { i, _ -> i != index }
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

    fun updateSet(exerciseIndex: Int, setIndex: Int, transform: (DraftSet) -> DraftSet) {
        _workoutDraft.value = _workoutDraft.value.mapIndexed { i, ex ->
            if (i != exerciseIndex) ex
            else ex.copy(sets = ex.sets.mapIndexed { j, s -> if (j == setIndex) transform(s) else s })
        }
        scheduleWorkoutSave()
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
        pendingWorkout = null
        _savedAt.value = LocalDateTime.now().format(TIME_FMT)
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

    private fun loadDietDraft(date: String) {
        viewModelScope.launch {
            val saved = repository.observeMeals(date).first()
            _dietDraft.value = saved.sortedBy { it.orderIndex }.map { it.toDraft() }
            if (saved.isEmpty()) _dietTemplateName.value = ""
        }
    }

    fun loadDietTemplate(template: DietTemplateWithMeals) {
        _dietTemplateName.value = template.template.name
        _dietDraft.value = template.meals.sortedBy { it.orderIndex }.map { it.toDraft() }
        scheduleDietSave()
    }

    fun addDietTemplate(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.addDietTemplate(name.trim()) }
    }

    fun renameDietTemplate(id: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { repository.renameDietTemplate(id, name.trim()) }
    }

    fun deleteDietTemplate(id: Long) {
        viewModelScope.launch { repository.deleteDietTemplate(id) }
    }

    /** 지금 화면의 끼니 구성을 식단표로 저장한다. */
    fun saveCurrentAsTemplate(name: String) {
        if (name.isBlank() || _dietDraft.value.isEmpty()) return
        viewModelScope.launch {
            repository.saveAsNewDietTemplate(name.trim(), _dietDraft.value.toTemplateMeals())
            _dietTemplateName.value = name.trim()
            _toast.value = "식단표 '${name.trim()}' 저장됨"
        }
    }

    fun overwriteTemplate(templateId: Long, templateName: String) {
        viewModelScope.launch {
            repository.overwriteDietTemplate(templateId, _dietDraft.value.toTemplateMeals())
            _toast.value = "식단표 '$templateName' 갱신됨"
        }
    }

    fun addMeal(slotName: String) {
        val name = slotName.ifBlank { "끼니 ${_dietDraft.value.size + 1}" }
        _dietDraft.value = _dietDraft.value + DraftMeal(slotName = name.trim(), menuText = "")
        scheduleDietSave()
    }

    fun removeMeal(index: Int) {
        _dietDraft.value = _dietDraft.value.filterIndexed { i, _ -> i != index }
        scheduleDietSave()
    }

    fun updateMeal(index: Int, transform: (DraftMeal) -> DraftMeal) {
        _dietDraft.value = _dietDraft.value.mapIndexed { i, m -> if (i == index) transform(m) else m }
        scheduleDietSave()
    }

    /** 음식 카탈로그에서 고른 값을 해당 끼니에 채워 넣는다. */
    fun applyFood(index: Int, food: FoodItem) {
        updateMeal(index) { it.copy(menuText = food.name, macros = food.macros) }
    }

    /** 어림값을 실제 입력값으로 확정한다. */
    fun acceptEstimate(index: Int) {
        updateMeal(index) { meal -> meal.estimate?.let { meal.copy(macros = it) } ?: meal }
    }

    /** 목록에서 치운다. 시드 음식은 지우면 다음 갱신 때 되살아나므로 숨김으로 처리한다. */
    fun hideFood(id: Long) {
        viewModelScope.launch { repository.hideFood(id) }
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
        repository.saveMealDay(s.date, s.draft.toMealEntries(s.date))
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

    fun saveTarget(kcal: Double, protein: Double, carbs: Double, fat: Double, sugar: Double) {
        viewModelScope.launch {
            repository.saveTarget(DietTarget(kcal = kcal, protein = protein, carbs = carbs, fat = fat, sugar = sugar))
            _toast.value = "목표 저장됨"
        }
    }

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
