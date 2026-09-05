package com.pkj.leanbulk.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.pkj.leanbulk.data.Exercise
import com.pkj.leanbulk.data.RoutineWithExercises
import com.pkj.leanbulk.data.TrackingType
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

    var showRoutinePicker by remember { mutableStateOf(false) }
    var showAddExercise by remember { mutableStateOf(false) }
    var showSaveRoutine by remember { mutableStateOf(false) }
    var pendingRoutine by remember { mutableStateOf<RoutineWithExercises?>(null) }
    // 저장하려는 이름이 기존 루틴과 겹쳐서 덮어쓸지 묻는 중인 대상.
    var duplicateName by remember { mutableStateOf<RoutineWithExercises?>(null) }

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

    // 헤더까지 전부 한 LazyColumn 안에 넣어야 위로 같이 스크롤된다.
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                HeaderRow(
                    date = date,
                    totalVolume = draft.sumOf { it.volume },
                    savedAt = savedAt,
                    onDateChange = vm::setWorkoutDate
                )

                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 루틴 이름은 길 수 있으므로 남는 폭을 다 쓰고 넘치면 … 으로 자른다.
                    OutlinedButton(
                        onClick = { showRoutinePicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (routineName.isBlank()) "루틴 선택" else routineName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    // 연결만 떼는 버튼. 종목은 그대로 두고 자동 반영만 멈춘다.
                    if (routineName.isNotBlank()) {
                        IconButton(onClick = vm::clearRoutine) {
                            Icon(Icons.Filled.LinkOff, contentDescription = "루틴 연결 해제")
                        }
                    }
                }

                // 편집이 루틴을 조용히 바꾸는 동작이라, 지금 그러는 중인지 화면에 적어둔다.
                if (routineName.isNotBlank()) {
                    Text(
                        text = when {
                            date != today -> "지난 날짜라 '$routineName' 루틴에는 반영되지 않습니다"
                            routineSynced -> "'$routineName' 루틴에 반영됨"
                            else -> "고치면 '$routineName' 루틴에 반영됩니다"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showAddExercise = true },
                        modifier = Modifier.weight(1f)
                    ) { Text("종목 추가", maxLines = 1) }
                    OutlinedButton(
                        onClick = { showSaveRoutine = true },
                        enabled = draft.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) { Text("루틴으로 저장", maxLines = 1) }
                }

                HorizontalDivider(Modifier.padding(vertical = 12.dp))
            }
        }

        if (draft.isEmpty()) {
            item { Text("루틴을 선택하거나 종목을 추가하세요.") }
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
                    onSetChange = { setIndex, transform -> vm.updateSet(exIndex, setIndex, transform) }
                )
            }
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
        AlertDialog(
            onDismissRequest = { duplicateName = null },
            title = { Text("같은 이름의 루틴이 있습니다") },
            text = { Text("'${routine.routine.name}'의 종목 ${routine.exercises.size}개가 지금 화면의 ${draft.size}개로 바뀝니다.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.overwriteRoutine(routine.routine.id, routine.routine.name)
                    duplicateName = null
                }) { Text("덮어쓰기") }
            },
            dismissButton = { TextButton(onClick = { duplicateName = null }) { Text("취소") } }
        )
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
        AlertDialog(
            onDismissRequest = { pendingRoutine = null },
            title = { Text("지금 종목을 갈아엎을까요?") },
            text = { Text("$date 의 종목 ${draft.size}개가 '${routine.routine.name}' 내용으로 바뀝니다.") },
            confirmButton = {
                TextButton(onClick = { vm.loadRoutine(routine); pendingRoutine = null }) { Text("불러오기") }
            },
            dismissButton = { TextButton(onClick = { pendingRoutine = null }) { Text("취소") } }
        )
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
    onSetChange: (Int, (DraftSet) -> DraftSet) -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    // 화살표 두 개가 자리를 먹어서 긴 이름은 한 줄에 안 들어간다.
                    // 자르는 것보다 두 줄로 접히는 편이 낫다 — 이름 끝의 괄호 설명이 종목을 가른다.
                    Text(
                        exercise.name,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        listOfNotNull(
                            exercise.trackingType.label,
                            exercise.defaultSetting.takeIf { it.isNotBlank() }
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                // 새 종목은 늘 맨 아래에 붙는다. 원하는 자리로 끌어올리는 건 여기서 한다.
                IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "위로")
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "아래로")
                }
                IconButton(onClick = onRemoveExercise, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "종목 삭제")
                }
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

            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onAddSet) {
                    Icon(Icons.Filled.Add, contentDescription = "세트 추가")
                    Text("세트")
                }
                OutlinedButton(onClick = onRemoveSet) {
                    Icon(Icons.Filled.Remove, contentDescription = "세트 삭제")
                    Text("세트")
                }
                Box(Modifier.weight(1f))
                // 무게를 안 쓰는 종목은 볼륨이라는 개념이 없어서 0kg만 뜬다. 아예 감춘다.
                if (exercise.trackingType == TrackingType.WEIGHT_REPS ||
                    exercise.trackingType == TrackingType.WEIGHT_TIME
                ) {
                    Text("볼륨 ${exercise.volume.toInt()}kg")
                }
            }
        }
    }
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
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(SET_NUMBER_WIDTH),
            textAlign = TextAlign.Center
        )
        trackingType.columnLabels.forEach { label ->
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }
        Text(
            "완료",
            style = MaterialTheme.typography.labelSmall,
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
            "$setNumber",
            style = MaterialTheme.typography.bodyMedium,
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("오늘 할 루틴 선택") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                routines.forEach { item ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onPick(item) }, modifier = Modifier.weight(1f)) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(item.routine.name)
                                Text(
                                    "종목 ${item.exercises.size}개",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        // 지금 화면에 종목이 있어야 덮어쓸 내용이 있다.
                        if (canOverwrite) {
                            TextButton(onClick = { overwriting = item }) { Text("덮어쓰기") }
                        }
                        IconButton(onClick = { editing = item.routine.id to item.routine.name }) {
                            Icon(Icons.Filled.Edit, contentDescription = "이름 수정")
                        }
                        IconButton(onClick = { onDelete(item.routine.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "루틴 삭제")
                        }
                    }
                }
                OutlinedButton(
                    onClick = { editing = 0L to "" },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("루틴 추가")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } }
    )

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
        AlertDialog(
            onDismissRequest = { overwriting = null },
            title = { Text("'${item.routine.name}'을 덮어쓸까요?") },
            text = { Text("이 루틴의 종목 ${item.exercises.size}개가 지금 화면의 구성으로 바뀝니다.") },
            confirmButton = {
                TextButton(onClick = {
                    onOverwrite(item)
                    overwriting = null
                    onDismiss()
                }) { Text("덮어쓰기") }
            },
            dismissButton = { TextButton(onClick = { overwriting = null }) { Text("취소") } }
        )
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "루틴 추가" else "루틴 이름 수정") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("루틴 이름") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }) { Text(if (isNew) "추가" else "저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("종목 추가") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("종목 검색") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
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
                    Text("찾는 종목이 없습니다. 아래에서 직접 추가하세요.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(filtered, key = { it.id }) { exercise ->
                            TextButton(
                                onClick = { onPick(exercise) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(exercise.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${exercise.bodyPart} · ${exercise.equipment} · ${exercise.trackingType.label}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { creating = true }) { Text("직접 추가") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } }
    )
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("종목 직접 추가") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("종목 이름") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("부위", modifier = Modifier.padding(top = 12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(bodyParts.ifEmpty { listOf("기타") }) { part ->
                        FilterChip(
                            selected = bodyPart == part,
                            onClick = { bodyPart = part },
                            label = { Text(part) }
                        )
                    }
                }

                Text("기구", modifier = Modifier.padding(top = 12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(EQUIPMENTS) { item ->
                        FilterChip(
                            selected = equipment == item,
                            onClick = { equipment = item },
                            label = { Text(item) }
                        )
                    }
                }

                Text("무엇으로 재나요", modifier = Modifier.padding(top = 12.dp))
                Column {
                    TrackingType.entries.forEach { type ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = trackingType == type, onClick = { trackingType = type })
                            Text(type.label)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name, bodyPart, equipment, trackingType) }
            ) { Text("추가") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
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
