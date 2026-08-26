package com.pkj.leanbulk.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pkj.leanbulk.data.RoutineWithExercises
import java.time.LocalDate

@Composable
fun TodayWorkoutScreen(vm: MainViewModel) {
    val date by vm.workoutDate.collectAsState()
    val routineName by vm.routineName.collectAsState()
    val draft by vm.workoutDraft.collectAsState()
    val savedAt by vm.savedAt.collectAsState()
    val routines by vm.routines.collectAsState()

    var showRoutinePicker by remember { mutableStateOf(false) }
    var showAddExercise by remember { mutableStateOf(false) }
    var pendingRoutine by remember { mutableStateOf<RoutineWithExercises?>(null) }

    // 헤더까지 전부 한 LazyColumn 안에 넣어야 위로 같이 스크롤된다.
    LazyColumn(
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

                // 루틴 이름은 길 수 있으므로 한 줄 전체를 쓰고 넘치면 … 으로 자른다.
                OutlinedButton(
                    onClick = { showRoutinePicker = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text(
                        text = if (routineName.isBlank()) "루틴 선택" else routineName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }

                OutlinedButton(
                    onClick = { showAddExercise = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) { Text("종목 추가") }

                HorizontalDivider(Modifier.padding(vertical = 12.dp))
            }
        }

        if (draft.isEmpty()) {
            item { Text("루틴을 선택하거나 종목을 추가하세요.") }
        } else {
            itemsIndexed(draft) { exIndex, exercise ->
                ExerciseCard(
                    exercise = exercise,
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
            onDismiss = { showRoutinePicker = false }
        )
    }

    if (showAddExercise) {
        AddExerciseDialog(
            onAdd = { vm.addExercise(it); showAddExercise = false },
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
    onAddSet: () -> Unit,
    onRemoveSet: () -> Unit,
    onRemoveExercise: () -> Unit,
    onSetChange: (Int, (DraftSet) -> DraftSet) -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(exercise.name, fontWeight = FontWeight.Bold)
                    if (exercise.defaultSetting.isNotBlank()) {
                        Text(exercise.defaultSetting, style = MaterialTheme.typography.bodySmall)
                    }
                }
                IconButton(onClick = onRemoveExercise) {
                    Icon(Icons.Filled.Delete, contentDescription = "종목 삭제")
                }
            }

            exercise.sets.forEachIndexed { setIndex, set ->
                SetRow(
                    setNumber = setIndex + 1,
                    set = set,
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
                Text("볼륨 ${exercise.volume.toInt()}kg")
            }
        }
    }
}

@Composable
private fun SetRow(setNumber: Int, set: DraftSet, onChange: ((DraftSet) -> DraftSet) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("세트 $setNumber", modifier = Modifier.width(52.dp))
        NumberField("무게(kg)", set.weightKg) { newWeight -> onChange { it.copy(weightKg = newWeight) } }
        IntField("횟수", set.reps) { newReps -> onChange { it.copy(reps = newReps ?: it.reps) } }
        Checkbox(checked = set.done, onCheckedChange = { checked -> onChange { it.copy(done = checked) } })
    }
}

@Composable
private fun RoutinePickerDialog(
    routines: List<RoutineWithExercises>,
    onPick: (RoutineWithExercises) -> Unit,
    onAdd: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    // null이 아니면 이름 편집 중인 루틴. id = 0L이면 새 루틴 추가.
    var editing by remember { mutableStateOf<Pair<Long, String>?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("오늘 할 루틴 선택") },
        text = {
            Column {
                routines.forEach { item ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onPick(item) }, modifier = Modifier.weight(1f)) {
                            Text(item.routine.name, modifier = Modifier.fillMaxWidth())
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

@Composable
private fun AddExerciseDialog(onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("종목 추가") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("종목 이름") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onAdd(name) }) { Text("추가") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}
