package com.pkj.leanbulk.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.pkj.leanbulk.data.Exercise
import com.pkj.leanbulk.data.RoutineWithExercises
import com.pkj.leanbulk.data.TrackingType
import com.pkj.leanbulk.ui.theme.ShaSpacing
import com.pkj.leanbulk.ui.theme.ShaTheme
import java.time.LocalDate

@Composable
fun TodayWorkoutScreen(vm: MainViewModel) {
    val date by vm.workoutDate.collectAsState()
    val routineName by vm.routineName.collectAsState()
    val draft by vm.workoutDraft.collectAsState()
    val savedAt by vm.savedAt.collectAsState()
    val routines by vm.routines.collectAsState()
    val exercises by vm.exercises.collectAsState()
    val today by vm.today.collectAsState()
    val routineSynced by vm.routineSynced.collectAsState()
    val session by vm.workoutSession.collectAsState()
    val restTimer by vm.restTimer.collectAsState()
    val now by vm.now.collectAsState()


    var showRoutinePicker by remember { mutableStateOf(false) }
    var showAddExercise by remember { mutableStateOf(false) }
    var showSaveRoutine by remember { mutableStateOf(false) }
    var pendingRoutine by remember { mutableStateOf<RoutineWithExercises?>(null) }
    // 저장하려는 이름이 기존 루틴과 겹쳐서 덮어쓸지 묻는 중인 대상.
    var duplicateName by remember { mutableStateOf<RoutineWithExercises?>(null) }
    var memoFor by remember { mutableStateOf<Int?>(null) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    /**
     * 종목을 옮긴 뒤 그 카드를 눈에 보이게 유지한다.
     *
     * 안 하면 화면 위쪽 끝의 카드를 ↑로 올리는 순간 시야 밖으로 나가서, 어디까지 올렸는지
     * 모른 채 버튼만 계속 누르게 된다.
     */
    fun moveExercise(from: Int, to: Int) {
        vm.moveExercise(from, to)
        scope.launch {
            // 옮긴 결과가 실제로 배치된 뒤라야 위치를 물어볼 수 있다.
            withFrameNanos { }
            listState.keepItemVisible(to + HEADER_ITEMS)
        }
    }

    val colors = ShaTheme.colors
    val type = ShaTheme.type

    ShaScreen {
    // 헤더까지 전부 한 LazyColumn 안에 넣어야 위로 같이 스크롤된다.
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().padding(horizontal = ShaSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(ShaSpacing.md)
    ) {
        item {
            Column {
                ShaDateHeader(
                    date = date,
                    subtitle = savedAt?.let { "저장됨 " + it } ?: "자동 저장",
                    onDateChange = vm::setWorkoutDate,
                    actionText = if (routineName.isBlank()) "루틴" else routineName,
                    onAction = { showRoutinePicker = true }
                )

                Row(horizontalArrangement = Arrangement.spacedBy(ShaSpacing.md)) {
                    StatTile(
                        label = "총 볼륨",
                        value = draft.sumOf { it.volume }.toInt().toString(),
                        unit = "kg",
                        delta = if (draft.isEmpty()) null else "종목 " + draft.size + "개",
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        label = "운동 시간",
                        value = formatClock((session?.elapsedMs(now) ?: 0L) / 1000),
                        unit = "",
                        delta = if (session?.running == true) "재는 중" else null,
                        deltaTone = if (session?.running == true) colors.primary else null,
                        modifier = Modifier.weight(1f)
                    )
                }

                WorkoutTimerButton(
                    session = session,
                    isToday = date == today,
                    onStart = vm::startWorkout,
                    onEnd = vm::endWorkout,
                    modifier = Modifier.padding(top = ShaSpacing.md)
                )

                // 편집이 루틴을 조용히 바꾸는 동작이라, 지금 그러는 중인지 화면에 적어둔다.
                if (routineName.isNotBlank()) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = ShaSpacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when {
                                date != today -> "지난 날짜라 '" + routineName + "' 루틴에는 반영되지 않습니다"
                                routineSynced -> "'" + routineName + "' 루틴에 반영됨"
                                else -> "고치면 '" + routineName + "' 루틴에 반영됩니다"
                            },
                            style = type.caption,
                            color = colors.muted,
                            modifier = Modifier.weight(1f)
                        )
                        // 연결만 뗀다. 종목은 그대로 두고 자동 반영만 멈춘다.
                        Text(
                            "연결 해제",
                            style = type.caption,
                            color = colors.primary,
                            modifier = Modifier
                                .clickable(onClick = vm::clearRoutine)
                                .padding(ShaSpacing.sm)
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = ShaSpacing.lg, bottom = ShaSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("종목", style = type.h2, color = colors.ink, modifier = Modifier.weight(1f))
                    if (draft.isNotEmpty()) {
                        Text(
                            "루틴으로 저장",
                            style = type.bodyStrong,
                            color = colors.primary,
                            modifier = Modifier
                                .clickable { showSaveRoutine = true }
                                .padding(vertical = ShaSpacing.sm)
                        )
                    }
                }
            }
        }

        if (draft.isEmpty()) {
            item {
                Text(
                    "루틴을 고르거나 종목을 더하세요.",
                    style = type.body,
                    color = colors.muted,
                    modifier = Modifier.padding(vertical = ShaSpacing.md)
                )
            }
        } else {
            itemsIndexed(draft) { exIndex, exercise ->
                ExerciseCard(
                    exercise = exercise,
                    canMoveUp = exIndex > 0,
                    canMoveDown = exIndex < draft.lastIndex,
                    onMoveUp = { moveExercise(exIndex, exIndex - 1) },
                    onMoveDown = { moveExercise(exIndex, exIndex + 1) },
                    onAddSet = { vm.addSet(exIndex) },
                    onRemoveSet = { vm.removeSet(exIndex) },
                    onRemoveExercise = { vm.removeExercise(exIndex) },
                    onSetChange = { setIndex, transform -> vm.updateSet(exIndex, setIndex, transform) },
                    onEditMemo = { memoFor = exIndex }
                )
            }
        }

        item {
            DashedAddButton(
                text = "종목 추가",
                onClick = { showAddExercise = true },
                modifier = Modifier.padding(top = ShaSpacing.xs)
            )
        }

        // 휴식 창이 마지막 카드를 가리지 않도록 아래를 비워둔다.
        item { Box(Modifier.padding(bottom = 96.dp)) }
    }

    RestTimerOverlay(
        timer = restTimer,
        nowEpochMs = now,
        onAdjust = vm::adjustRest,
        onSkip = vm::skipRest,
        onExpandedChange = vm::setRestExpanded,
        expandedModifier = Modifier.align(Alignment.BottomCenter),
        collapsedModifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
    )
    }

    memoFor?.let { index ->
        val exercise = draft.getOrNull(index)
        if (exercise == null) {
            memoFor = null
        } else {
            MemoDialog(
                title = "${exercise.name} 메모",
                initial = exercise.memo,
                onConfirm = { vm.setExerciseMemo(index, it); memoFor = null },
                onDismiss = { memoFor = null }
            )
        }
    }

    if (showRoutinePicker) {
        RoutinePickerDialog(
            routines = routines,
            // 이미 짜둔 종목이 있으면 통째로 갈아엎는 것이므로 한 번 확인한다.
            onPick = {
                if (draft.isEmpty()) vm.loadRoutine(it) else pendingRoutine = it
                showRoutinePicker = false
            },
            onAdd = vm::addRoutine,
            onRename = vm::renameRoutine,
            onDelete = vm::deleteRoutine,
            onOverwrite = { vm.overwriteRoutine(it.routine.id, it.routine.name) },
            canOverwrite = draft.isNotEmpty(),
            onDismiss = { showRoutinePicker = false }
        )
    }

    if (showSaveRoutine) {
        TextInputDialog(
            title = "루틴으로 저장",
            label = "루틴 이름",
            initial = routineName,
            confirmText = "저장",
            onConfirm = { name ->
                // 같은 이름이 있으면 새로 만들지 않고 덮어쓸지 먼저 묻는다.
                // (그냥 만들면 목록에 같은 이름이 둘 생겨서 뭐가 뭔지 알 수 없다)
                val existing = routines.firstOrNull { it.routine.name == name.trim() }
                if (existing == null) vm.saveCurrentAsRoutine(name) else duplicateName = existing
                showSaveRoutine = false
            },
            onDismiss = { showSaveRoutine = false }
        )
    }

    duplicateName?.let { routine ->
        ShaDialog(
            title = "같은 이름의 루틴이 있습니다",
            onDismiss = { duplicateName = null },
            confirmText = "덮어쓰기",
            onConfirm = {
                vm.overwriteRoutine(routine.routine.id, routine.routine.name)
                duplicateName = null
            }
        ) {
            DialogBody("'${routine.routine.name}'의 종목 ${routine.exercises.size}개가 지금 화면의 ${draft.size}개로 바뀝니다.")
        }
    }

    if (showAddExercise) {
        ExercisePickerDialog(
            exercises = exercises,
            onPick = { vm.addExercise(it); showAddExercise = false },
            onCreate = { name, part, equip, type ->
                vm.addNewExercise(name, part, equip, type)
                showAddExercise = false
            },
            onDismiss = { showAddExercise = false }
        )
    }

    pendingRoutine?.let { routine ->
        ShaDialog(
            title = "지금 종목을 갈아엎을까요?",
            onDismiss = { pendingRoutine = null },
            confirmText = "불러오기",
            onConfirm = { vm.loadRoutine(routine); pendingRoutine = null }
        ) {
            DialogBody("$date 의 종목 ${draft.size}개가 '${routine.routine.name}' 내용으로 바뀝니다.")
        }
    }
}

@Composable
private fun HeaderRow(
    date: String,
    totalVolume: Double,
    savedAt: String?,
    onDateChange: (String) -> Unit
) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "총 볼륨 ${totalVolume.toInt()}kg",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(end = 8.dp)
        )
        OutlinedButton(onClick = {
            val current = LocalDate.parse(date)
            DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    onDateChange(LocalDate.of(year, month + 1, dayOfMonth).toString())
                },
                current.year, current.monthValue - 1, current.dayOfMonth
            ).show()
        }) { Text(date) }
        Box(Modifier.weight(1f))
        // 첫 편집 전에도 자동 저장이 켜져 있다는 걸 알 수 있게 항상 표시한다.
        Text(
            text = savedAt?.let { "저장됨 $it" } ?: "자동 저장",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ExerciseCard(
    exercise: DraftExercise,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: () -> Unit,
    onRemoveExercise: () -> Unit,
    onSetChange: (Int, (DraftSet) -> DraftSet) -> Unit,
    onEditMemo: () -> Unit
) {
    val colors = ShaTheme.colors
    val type = ShaTheme.type

    ShaCard {
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    // 화살표 두 개가 자리를 먹어서 긴 이름은 한 줄에 안 들어간다.
                    // 자르는 것보다 두 줄로 접히는 편이 낫다 — 이름 끝의 괄호 설명이 종목을 가른다.
                    Text(
                        exercise.name,
                        style = type.h2,
                        color = colors.ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        listOfNotNull(
                            exercise.trackingType.label,
                            exercise.defaultSetting.takeIf { it.isNotBlank() }
                        ).joinToString(" · "),
                        style = type.caption,
                        color = colors.muted
                    )
                }
                // 무게를 안 쓰는 종목은 볼륨이라는 개념이 없어서 0kg만 뜬다. 아예 감춘다.
                if (exercise.trackingType == TrackingType.WEIGHT_REPS ||
                    exercise.trackingType == TrackingType.WEIGHT_TIME
                ) {
                    Text(exercise.volume.toInt().toString(), style = type.data, color = colors.ink)
                    Text(
                        "kg",
                        style = type.caption,
                        color = colors.muted,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
            }

            if (exercise.memo.isNotBlank()) {
                Text(
                    "메모  " + exercise.memo,
                    style = type.caption,
                    color = colors.muted,
                    modifier = Modifier.padding(top = ShaSpacing.xs)
                )
            }

            SetColumnHeader(exercise.trackingType)

            exercise.sets.forEachIndexed { setIndex, set ->
                SetRow(
                    setNumber = setIndex + 1,
                    set = set,
                    trackingType = exercise.trackingType,
                    onChange = { transform -> onSetChange(setIndex, transform) }
                )
            }

            // 카드 아래 줄은 전부 글자 버튼이다. 이 줄에 채운 버튼을 두면 종목마다
            // 주 버튼이 하나씩 생겨서 화면에서 무엇을 먼저 눌러야 할지가 사라진다.
            Row(
                Modifier.fillMaxWidth().padding(top = ShaSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(ShaSpacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CardAction("세트 +", onAddSet)
                CardAction("세트 −", onRemoveSet)
                CardAction("메모", onEditMemo, Modifier.testTag("exercise_memo_" + exercise.name))
                Box(Modifier.weight(1f))
                // 순서 조정·삭제는 자주 쓰지 않는다. 아이콘으로 작게 흐리게 둔다.
                IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.KeyboardArrowUp, contentDescription = "위로",
                        tint = if (canMoveUp) colors.muted else colors.border
                    )
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown, contentDescription = "아래로",
                        tint = if (canMoveDown) colors.muted else colors.border
                    )
                }
                IconButton(onClick = onRemoveExercise, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "종목 삭제", tint = colors.muted)
                }
            }
        }
    }
}

/** 카드 안의 작은 글자 버튼. */
@Composable
private fun CardAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text,
        style = ShaTheme.type.bodyStrong,
        color = ShaTheme.colors.primary,
        modifier = modifier.clickable(onClick = onClick).padding(vertical = ShaSpacing.sm)
    )
}

/** 세트 번호 칸 폭. 머리글과 세트 줄이 같은 값을 써야 칸이 세로로 맞는다. */
private val SET_NUMBER_WIDTH = 24.dp

/** 완료 체크 칸 폭. 위와 같은 이유. */
private val DONE_WIDTH = 32.dp

/**
 * 어떤 칸이 있는지 종목 카드에 **한 번만** 적는다.
 *
 * 세트마다 라벨을 달면 "무게(kg) 횟수"가 다섯 번 반복되면서 카드가 화면을 다 먹는다.
 * 표의 머리글처럼 위에 한 줄만 두면 아래 세트 줄은 숫자만 남아 훨씬 조밀해진다.
 */
@Composable
private fun SetColumnHeader(trackingType: TrackingType) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "세트",
            style = ShaTheme.type.caption,
            color = ShaTheme.colors.muted,
            modifier = Modifier.width(SET_NUMBER_WIDTH),
            textAlign = TextAlign.Center
        )
        trackingType.columnLabels.forEach { label ->
            Text(
                label,
                style = ShaTheme.type.caption,
                color = ShaTheme.colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            "완료",
            style = ShaTheme.type.caption,
            color = ShaTheme.colors.muted,
            modifier = Modifier.width(DONE_WIDTH),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 세트 한 줄. **종목의 추적 방식에 따라 입력 칸이 달라진다.**
 *
 * 예전에는 무게·횟수 칸만 있어서 플랭크를 넣으면 "0kg 0회"라는 무의미한 기록이 남았다.
 *
 * 세트 번호·입력 칸·완료 체크가 전부 한 줄이다. 폭이 빠듯해서(폰 기준 300dp 남짓에
 * 버튼 넷 + 칸 둘 + 체크) 증감 버튼은 32dp, 입력 칸은 40dp 높이로 줄였고 라벨은
 * [SetColumnHeader]로 뺐다. 입력 칸은 `weight(1f)`로 남는 폭을 나눠 가지므로 값이
 * 하나뿐인 종목(플랭크 등)은 그만큼 넓게 쓴다.
 */
@Composable
private fun SetRow(
    setNumber: Int,
    set: DraftSet,
    trackingType: TrackingType,
    onChange: ((DraftSet) -> DraftSet) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            setNumber.toString(),
            style = ShaTheme.type.dataSmall,
            color = ShaTheme.colors.muted,
            modifier = Modifier.width(SET_NUMBER_WIDTH),
            textAlign = TextAlign.Center
        )

        when (trackingType) {
            TrackingType.WEIGHT_REPS -> {
                WeightStepper(set, onChange, Modifier.weight(1f))
                RepsStepper(set, onChange, Modifier.weight(1f))
            }
            TrackingType.REPS -> RepsStepper(set, onChange, Modifier.weight(1f))
            TrackingType.TIME -> DurationStepper(set, onChange, Modifier.weight(1f))
            TrackingType.WEIGHT_TIME -> {
                WeightStepper(set, onChange, Modifier.weight(1f))
                DurationStepper(set, onChange, Modifier.weight(1f))
            }
            TrackingType.DISTANCE_TIME -> {
                DistanceStepper(set, onChange, Modifier.weight(1f))
                DurationStepper(set, onChange, Modifier.weight(1f))
            }
        }

        Checkbox(
            checked = set.done,
            onCheckedChange = { checked -> onChange { it.copy(done = checked) } },
            modifier = Modifier.size(DONE_WIDTH)
        )
    }
}

// 증감 폭은 값마다 다르다. 무게·횟수는 1씩이지만, 시간을 1초씩 올리는 건 쓸모가 없고
// 거리도 1m씩은 너무 잘다. 직접 입력은 그대로 되므로 어긋난 값도 칠 수 있다.
@Composable
private fun WeightStepper(set: DraftSet, onChange: ((DraftSet) -> DraftSet) -> Unit, modifier: Modifier) =
    NumberStepper(WEIGHT_LABEL, set.weightKg, step = 1.0, modifier = modifier) { v ->
        onChange { it.copy(weightKg = v) }
    }

@Composable
private fun RepsStepper(set: DraftSet, onChange: ((DraftSet) -> DraftSet) -> Unit, modifier: Modifier) =
    IntStepper(REPS_LABEL, set.reps, step = 1, modifier = modifier) { v -> onChange { it.copy(reps = v) } }

@Composable
private fun DurationStepper(set: DraftSet, onChange: ((DraftSet) -> DraftSet) -> Unit, modifier: Modifier) =
    IntStepper(DURATION_LABEL, set.durationSec ?: 0, step = 5, modifier = modifier) { v ->
        onChange { it.copy(durationSec = v) }
    }

@Composable
private fun DistanceStepper(set: DraftSet, onChange: ((DraftSet) -> DraftSet) -> Unit, modifier: Modifier) =
    NumberStepper(DISTANCE_LABEL, set.distanceM ?: 0.0, step = 10.0, modifier = modifier) { v ->
        onChange { it.copy(distanceM = v) }
    }

private const val WEIGHT_LABEL = "무게(kg)"
private const val REPS_LABEL = "횟수"
private const val DURATION_LABEL = "시간(초)"
private const val DISTANCE_LABEL = "거리(m)"

/**
 * 이 추적 방식이 쓰는 칸 이름. 머리글([SetColumnHeader])과 세트 줄의 스테퍼가 **같은 상수를
 * 나눠 쓴다** — 따로 적으면 칸 순서를 바꿀 때 머리글만 안 바뀌어 엉뚱한 이름이 붙는다.
 */
private val TrackingType.columnLabels: List<String>
    get() = when (this) {
        TrackingType.WEIGHT_REPS -> listOf(WEIGHT_LABEL, REPS_LABEL)
        TrackingType.REPS -> listOf(REPS_LABEL)
        TrackingType.TIME -> listOf(DURATION_LABEL)
        TrackingType.WEIGHT_TIME -> listOf(WEIGHT_LABEL, DURATION_LABEL)
        TrackingType.DISTANCE_TIME -> listOf(DISTANCE_LABEL, DURATION_LABEL)
    }

@Composable
private fun RoutinePickerDialog(
    routines: List<RoutineWithExercises>,
    onPick: (RoutineWithExercises) -> Unit,
    onAdd: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onOverwrite: (RoutineWithExercises) -> Unit,
    canOverwrite: Boolean,
    onDismiss: () -> Unit
) {
    // null이 아니면 이름 편집 중인 루틴. id = 0L이면 새 루틴 추가.
    var editing by remember { mutableStateOf<Pair<Long, String>?>(null) }
    // 덮어쓰기는 되돌릴 수 없다(옛 종목 구성이 사라진다). 그래서 한 번 확인한다.
    var overwriting by remember { mutableStateOf<RoutineWithExercises?>(null) }

    val colors = ShaTheme.colors
    val type = ShaTheme.type

    ShaDialog(title = "오늘 할 루틴 선택", onDismiss = onDismiss, dismissText = "닫기") {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                routines.forEach { item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(item) }
                            .padding(vertical = ShaSpacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(item.routine.name, style = type.bodyStrong, color = colors.ink)
                            Text(
                                "종목 ${item.exercises.size}개",
                                style = type.caption,
                                color = colors.muted
                            )
                        }
                        // 지금 화면에 종목이 있어야 덮어쓸 내용이 있다.
                        if (canOverwrite) {
                            Text(
                                "덮어쓰기",
                                style = type.caption,
                                color = colors.primary,
                                modifier = Modifier
                                    .clickable { overwriting = item }
                                    .padding(ShaSpacing.md)
                            )
                        }
                        IconButton(onClick = { editing = item.routine.id to item.routine.name }) {
                            Icon(Icons.Filled.Edit, contentDescription = "이름 수정", tint = colors.muted)
                        }
                        IconButton(onClick = { onDelete(item.routine.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "루틴 삭제", tint = colors.muted)
                        }
                    }
                }
                DashedAddButton(
                    text = "루틴 추가",
                    onClick = { editing = 0L to "" },
                    modifier = Modifier.padding(top = ShaSpacing.md)
                )
        }
    }

    editing?.let { (routineId, currentName) ->
        RoutineNameDialog(
            initialName = currentName,
            isNew = routineId == 0L,
            onConfirm = { newName ->
                if (routineId == 0L) onAdd(newName) else onRename(routineId, newName)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }

    overwriting?.let { item ->
        ShaDialog(
            title = "'${item.routine.name}'을 덮어쓸까요?",
            onDismiss = { overwriting = null },
            confirmText = "덮어쓰기",
            onConfirm = {
                onOverwrite(item)
                overwriting = null
                onDismiss()
            }
        ) {
            DialogBody("이 루틴의 종목 ${item.exercises.size}개가 지금 화면의 구성으로 바뀝니다.")
        }
    }
}

/** 리스트 맨 위의 헤더 아이템 수. 종목 i는 리스트에서 `i + HEADER_ITEMS`번째다. */
private const val HEADER_ITEMS = 1

/**
 * 이미 다 보이면 가만히 두고, 잘려 있으면 딱 보일 만큼만 움직인다.
 *
 * `animateScrollToItem`은 대상 카드를 항상 화면 맨 위로 끌어올려서, 화면 안에서 한 칸
 * 내려가는 것처럼 안 움직여도 되는 경우까지 화면이 튄다.
 */
private suspend fun LazyListState.keepItemVisible(index: Int) {
    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index }
        ?: return animateScrollToItem(index)

    val viewportHeight = info.viewportEndOffset - info.viewportStartOffset
    val delta = when {
        // 카드가 화면보다 크면 아래를 맞출 수 없다. 위를 맞춰 시작 부분을 보여준다.
        item.size > viewportHeight -> item.offset - info.viewportStartOffset
        item.offset < info.viewportStartOffset -> item.offset - info.viewportStartOffset
        item.offset + item.size > info.viewportEndOffset ->
            item.offset + item.size - info.viewportEndOffset
        else -> 0
    }
    if (delta != 0) animateScrollBy(delta.toFloat())
}

@Composable
private fun RoutineNameDialog(
    initialName: String,
    isNew: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    ShaDialog(
        title = if (isNew) "루틴 추가" else "루틴 이름 수정",
        onDismiss = onDismiss,
        confirmText = if (isNew) "추가" else "저장",
        confirmEnabled = name.isNotBlank(),
        onConfirm = { onConfirm(name) }
    ) {
        ShaTextField(label = "루틴 이름", value = name, onValueChange = { name = it })
    }
}

/**
 * 카탈로그에서 종목을 검색해서 고른다.
 *
 * 예전에는 이름을 매번 타이핑했다. 그러면 오타로 같은 종목이 여러 개로 갈라지고,
 * 무엇보다 **추적 방식(무게×횟수 / 시간 / 거리)을 알 수 없어서 입력 칸을 바꿔줄 수가 없었다.**
 * 목록에 없으면 직접 등록할 수 있고, 등록한 종목은 카탈로그에 남아 다음부터 검색된다.
 */
@Composable
private fun ExercisePickerDialog(
    exercises: List<Exercise>,
    onPick: (Exercise) -> Unit,
    onCreate: (String, String, String, TrackingType) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var bodyPart by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }

    val bodyParts = remember(exercises) { exercises.map { it.bodyPart }.distinct() }
    val filtered = exercises.filter {
        (bodyPart == null || it.bodyPart == bodyPart) && it.name.contains(query, ignoreCase = true)
    }

    if (creating) {
        NewExerciseDialog(
            initialName = query,
            bodyParts = bodyParts,
            onConfirm = { n, part, equip, type -> onCreate(n, part, equip, type); creating = false },
            onDismiss = { creating = false }
        )
        return
    }

    ShaDialog(
        title = "종목 추가",
        onDismiss = onDismiss,
        dismissText = "닫기",
        confirmText = "직접 추가",
        onConfirm = { creating = true }
    ) {
                ShaTextField(
                    label = "",
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "종목 검색"
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        FilterChip(
                            selected = bodyPart == null,
                            onClick = { bodyPart = null },
                            label = { Text("전체") }
                        )
                    }
                    items(bodyParts) { part ->
                        FilterChip(
                            selected = bodyPart == part,
                            onClick = { bodyPart = if (bodyPart == part) null else part },
                            label = { Text(part) }
                        )
                    }
                }

                if (filtered.isEmpty()) {
                    DialogBody("찾는 종목이 없습니다. 아래 \"직접 추가\"로 만드세요.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(filtered, key = { it.id }) { exercise ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(exercise) }
                                    .padding(vertical = ShaSpacing.md)
                            ) {
                                Text(
                                    exercise.name,
                                    style = ShaTheme.type.bodyStrong,
                                    color = ShaTheme.colors.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${exercise.bodyPart} · ${exercise.equipment} · ${exercise.trackingType.label}",
                                    style = ShaTheme.type.caption,
                                    color = ShaTheme.colors.muted
                                )
                            }
                        }
                    }
                }
    }
}

/** 다이얼로그 본문 한 줄. 색·크기를 매번 적지 않게 묶어둔다. */
@Composable
private fun DialogBody(text: String) {
    Text(text, style = ShaTheme.type.body, color = ShaTheme.colors.bodyText)
}

/** 카탈로그에 없는 종목을 등록한다. 추적 방식을 고르는 게 핵심이다. */
@Composable
private fun NewExerciseDialog(
    initialName: String,
    bodyParts: List<String>,
    onConfirm: (String, String, String, TrackingType) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var bodyPart by remember { mutableStateOf(bodyParts.firstOrNull() ?: "기타") }
    var equipment by remember { mutableStateOf("기타") }
    var trackingType by remember { mutableStateOf(TrackingType.WEIGHT_REPS) }

    ShaDialog(
        title = "종목 직접 추가",
        onDismiss = onDismiss,
        confirmText = "추가",
        confirmEnabled = name.isNotBlank(),
        onConfirm = { onConfirm(name, bodyPart, equipment, trackingType) }
    ) {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                ShaTextField(label = "종목 이름", value = name, onValueChange = { name = it })

                DialogLabel("부위")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(bodyParts.ifEmpty { listOf("기타") }) { part ->
                        FilterChip(
                            selected = bodyPart == part,
                            onClick = { bodyPart = part },
                            label = { Text(part) }
                        )
                    }
                }

                DialogLabel("기구")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(EQUIPMENTS) { item ->
                        FilterChip(
                            selected = equipment == item,
                            onClick = { equipment = item },
                            label = { Text(item) }
                        )
                    }
                }

                DialogLabel("무엇으로 재나요")
                Column {
                    TrackingType.entries.forEach { type ->
                        Row(
                            Modifier.fillMaxWidth().clickable { trackingType = type },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = trackingType == type, onClick = { trackingType = type })
                            Text(type.label, style = ShaTheme.type.body, color = ShaTheme.colors.ink)
                        }
                    }
                }
        }
    }
}

/** 다이얼로그 안 작은 제목. 라벨은 항상 작고 흐리게 — 값이 주인공이다. */
@Composable
private fun DialogLabel(text: String) {
    Text(
        text,
        style = ShaTheme.type.caption,
        color = ShaTheme.colors.muted,
        modifier = Modifier.padding(top = ShaSpacing.lg, bottom = ShaSpacing.sm)
    )
}

private val EQUIPMENTS = listOf("바벨", "덤벨", "머신", "케이블", "맨몸", "기타")

/** 목록·선택지에 보여줄 짧은 설명. */
val TrackingType.label: String
    get() = when (this) {
        TrackingType.WEIGHT_REPS -> "무게 × 횟수"
        TrackingType.REPS -> "횟수"
        TrackingType.TIME -> "시간"
        TrackingType.WEIGHT_TIME -> "무게 + 시간"
        TrackingType.DISTANCE_TIME -> "거리 + 시간"
    }
