package com.pkj.leanbulk.ui

import android.app.DatePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pkj.leanbulk.data.BodyRecord
import com.pkj.leanbulk.data.ExerciseWithSets
import com.pkj.leanbulk.data.MealEntry
import com.pkj.leanbulk.data.backup.BackupFiles
import com.pkj.leanbulk.data.backup.DietTemplateDto
import com.pkj.leanbulk.data.backup.ExerciseDto
import com.pkj.leanbulk.data.backup.RoutineDto
import com.pkj.leanbulk.data.backup.Snapshot
import com.pkj.leanbulk.export.ShareUtils
import com.pkj.leanbulk.export.XlsxExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

@Composable
fun ExportScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today by vm.today.collectAsState()

    val meals by vm.allMeals.collectAsState()
    val workouts by vm.allWorkouts.collectAsState()
    val bodyRecords by vm.bodyRecords.collectAsState()
    val routines by vm.routines.collectAsState()
    val exercises by vm.exercises.collectAsState()
    val target by vm.target.collectAsState()

    var lastFile by remember { mutableStateOf<File?>(null) }
    var status by remember { mutableStateOf("") }

    // 켜지 않으면 지금처럼 전체 기간을 내보낸다. 켜면 시작~종료일 사이만 뽑는다.
    var useRange by remember { mutableStateOf(false) }
    var startDate by remember { mutableStateOf(today) }
    var endDate by remember { mutableStateOf(today) }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri: Uri? ->
        val file = lastFile
        if (uri != null && file != null) {
            ShareUtils.copyTo(context, file, uri)
            status = "저장 완료"
        }
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("엑셀 내보내기", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text("기존 운동일지.xlsx와 동일한 시트 구성(운동일지/종목목록/신체기록/식단체크/사용법)으로 내보냅니다.")
        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = useRange, onCheckedChange = { useRange = it })
            Text("기간 지정 (끄면 전체 기록을 내보냅니다)")
        }

        if (useRange) {
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DatePickButton(startDate, context) { startDate = it }
                Text("~")
                DatePickButton(endDate, context) { endDate = it }
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                // 사용자가 시작/종료일을 거꾸로 골랐어도 알아서 바로잡는다.
                val range = if (useRange) minOf(startDate, endDate)..maxOf(startDate, endDate) else null
                scope.launch {
                    val file = XlsxExporter.export(
                        context,
                        meals.mealsInRange(range),
                        workouts.workoutsInRange(range),
                        bodyRecords.bodyRecordsInRange(range),
                        routines,
                        exercises,
                        target
                    )
                    lastFile = file
                    status = "생성 완료: ${file.name}"
                }
            }
        ) { Text(if (useRange) "xlsx 파일 생성 ($startDate ~ $endDate)" else "xlsx 파일 생성") }

        Spacer(Modifier.height(8.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = lastFile != null,
            onClick = {
                lastFile?.let { context.startActivity(ShareUtils.shareIntentFor(context, it)) }
            }
        ) { Text("공유하기") }

        Spacer(Modifier.height(8.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = lastFile != null,
            onClick = { lastFile?.let { saveLauncher.launch(it.name) } }
        ) { Text("다른 폴더에 저장") }

        Spacer(Modifier.height(16.dp))
        Text(status)

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(24.dp))

        BackupSection(vm)
    }
}

/**
 * 백업/복원. 엑셀이 "사람이 보는 용도"라면 이쪽은 **기록을 그대로 옮기는 용도**다.
 * 엑셀은 셀 위치가 조금만 어긋나도 깨지고 되돌릴 정보가 부족해서 불러오기를 지원하지 않는다.
 */
@Composable
private fun BackupSection(vm: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var backupFile by remember { mutableStateOf<File?>(null) }
    var status by remember { mutableStateOf("") }
    var pendingImport by remember { mutableStateOf<Snapshot?>(null) }
    var mergeFrom by remember { mutableStateOf<Snapshot?>(null) }
    var working by remember { mutableStateOf(false) }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupFiles.MIME)) { uri: Uri? ->
        val file = backupFile
        if (uri != null && file != null) {
            ShareUtils.copyTo(context, file, uri)
            status = "저장 완료"
        }
    }

    // gzip을 application/gzip으로 안 보여주는 파일 관리자가 있어서 필터를 걸지 않는다.
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            status = runCatching { withContext(Dispatchers.IO) { BackupFiles.read(context, uri) } }
                .onSuccess { pendingImport = it }
                .fold(
                    onSuccess = { "" },
                    onFailure = { "읽을 수 없는 파일입니다. 이 앱이 만든 백업 파일인지 확인해 주세요." }
                )
        }
    }

    Text("백업 · 복원", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    Text("모든 기록을 파일 하나로 저장하고 되돌립니다. 폰을 바꾸거나 기록을 옮길 때 씁니다.")
    Spacer(Modifier.height(16.dp))

    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = !working,
        onClick = {
            scope.launch {
                working = true
                status = runCatching {
                    val snapshot = vm.buildSnapshot()
                    withContext(Dispatchers.IO) { BackupFiles.writeForSharing(context, snapshot) }
                }.fold(
                    onSuccess = { backupFile = it; "생성 완료: ${it.name}" },
                    onFailure = { "백업 생성 실패: ${it.message}" }
                )
                working = false
            }
        }
    ) { Text("백업 파일 만들기") }

    Spacer(Modifier.height(8.dp))

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            modifier = Modifier.weight(1f),
            enabled = backupFile != null,
            onClick = {
                backupFile?.let {
                    context.startActivity(ShareUtils.shareIntentFor(context, it, BackupFiles.MIME, "백업 공유하기"))
                }
            }
        ) { Text("공유하기") }

        Button(
            modifier = Modifier.weight(1f),
            enabled = backupFile != null,
            onClick = { backupFile?.let { saveLauncher.launch(it.name) } }
        ) { Text("파일로 저장") }
    }

    Spacer(Modifier.height(16.dp))

    OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        enabled = !working,
        onClick = { openLauncher.launch(arrayOf("*/*")) }
    ) { Text("백업 파일 불러오기") }

    Spacer(Modifier.height(8.dp))
    Text(status)

    pendingImport?.let { snapshot ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("이 파일을 어떻게 쓸까요?") },
            text = {
                Column {
                    Text(snapshot.summarize())
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "골라서 가져오면 지금 기록은 그대로 두고 고른 것만 더합니다.\n" +
                            "전체 덮어쓰기는 지금 기록을 모두 이 내용으로 바꿉니다(직전 상태는 앱 안에 남겨둡니다).",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(12.dp))
                    // 덮어쓰기는 되돌리기 번거로운 쪽이라 눈에 덜 띄는 자리에 둔다.
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            pendingImport = null
                            scope.launch {
                                working = true
                                status = runCatching {
                                    vm.applyImport(snapshot) { current ->
                                        withContext(Dispatchers.IO) {
                                            BackupFiles.saveRollbackPoint(context, current)
                                        }
                                    }
                                }.fold(
                                    onSuccess = { "전체 불러오기 완료" },
                                    onFailure = { "불러오기 실패: ${it.message}" }
                                )
                                working = false
                            }
                        }
                    ) { Text("전체 덮어쓰기") }
                }
            },
            confirmButton = {
                TextButton(onClick = { pendingImport = null; mergeFrom = snapshot }) {
                    Text("골라서 가져오기")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text("취소") }
            }
        )
    }

    mergeFrom?.let { snapshot ->
        SelectiveMergeDialog(
            snapshot = snapshot,
            onMerge = { routines, templates, exercises ->
                mergeFrom = null
                scope.launch {
                    working = true
                    status = runCatching {
                        vm.mergeFromSnapshot(snapshot.exercises, routines, templates, exercises)
                    }.fold(
                        onSuccess = { r ->
                            "가져왔습니다 — 루틴 ${r.routines} · 식단표 ${r.dietTemplates}" +
                                if (r.exercises > 0) " · 종목 ${r.exercises}" else ""
                        },
                        onFailure = { "가져오기 실패: ${it.message}" }
                    )
                    working = false
                }
            },
            onDismiss = { mergeFrom = null }
        )
    }
}

/**
 * 남의 백업 파일에서 원하는 것만 골라 가져온다.
 *
 * 일별 기록(식단·운동·신체)은 고를 수 없다 — 남의 하루 기록을 내 기록에 섞는 건 의미가
 * 없고, 같은 날짜를 어떻게 합칠지는 레코드 단위 동기화 문제라 별개다. 그래서 **재사용할
 * 수 있는 것**(루틴 · 식단표 · 종목)만 보여준다.
 */
@Composable
private fun SelectiveMergeDialog(
    snapshot: Snapshot,
    onMerge: (List<RoutineDto>, List<DietTemplateDto>, List<ExerciseDto>) -> Unit,
    onDismiss: () -> Unit
) {
    val routines = remember { mutableStateListOf<RoutineDto>() }
    val templates = remember { mutableStateListOf<DietTemplateDto>() }
    val exercises = remember { mutableStateListOf<ExerciseDto>() }

    // 시드 종목은 내게도 이미 있으니 굳이 고르게 하지 않는다. 남이 직접 만든 것만 보여준다.
    val userExercises = remember(snapshot) { snapshot.exercises.filter { it.source == "USER" } }
    val nothingPicked = routines.isEmpty() && templates.isEmpty() && exercises.isEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("가져올 항목 고르기") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                if (snapshot.routines.isNotEmpty()) {
                    item { SectionLabel("루틴") }
                    items(snapshot.routines, key = { it.name + it.orderIndex }) { dto ->
                        CheckRow(
                            checked = dto in routines,
                            title = dto.name,
                            subtitle = "종목 ${dto.exercises.size}개",
                            onToggle = { if (dto in routines) routines.remove(dto) else routines.add(dto) }
                        )
                    }
                }
                if (snapshot.dietTemplates.isNotEmpty()) {
                    item { SectionLabel("식단표") }
                    items(snapshot.dietTemplates, key = { "t" + it.name + it.orderIndex }) { dto ->
                        CheckRow(
                            checked = dto in templates,
                            title = dto.name,
                            subtitle = "끼니 ${dto.meals.size}개",
                            onToggle = { if (dto in templates) templates.remove(dto) else templates.add(dto) }
                        )
                    }
                }
                if (userExercises.isNotEmpty()) {
                    item { SectionLabel("직접 만든 종목") }
                    items(userExercises, key = { "e" + it.slug }) { dto ->
                        CheckRow(
                            checked = dto in exercises,
                            title = dto.name,
                            subtitle = "${dto.bodyPart} · ${dto.equipment}",
                            onToggle = { if (dto in exercises) exercises.remove(dto) else exercises.add(dto) }
                        )
                    }
                }
                item {
                    Text(
                        "고른 루틴이 쓰는 종목은 자동으로 같이 들어옵니다. " +
                            "이름이 겹치면 덮어쓰지 않고 뒤에 번호를 붙입니다.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !nothingPicked,
                onClick = { onMerge(routines.toList(), templates.toList(), exercises.toList()) }
            ) { Text("가져오기") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun CheckRow(checked: Boolean, title: String, subtitle: String, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** 확인 다이얼로그에 보여줄 요약. 뭘 덮어쓰는지 알고 누르게 한다. */
private fun Snapshot.summarize(): String = buildString {
    appendLine("만든 시각: ${exportedAt.replace('T', ' ').take(16)}")
    appendLine("식단  ${meals.map { it.date }.distinct().size}일 · ${meals.size}끼니")
    appendLine("운동  ${workoutDays.size}일 · ${workoutDays.sumOf { it.exercises.size }}종목")
    appendLine("신체기록  ${bodyRecords.size}건")
    append("루틴 ${routines.size} · 식단표 ${dietTemplates.size} · 음식 ${foods.size}")
}

@Composable
private fun DatePickButton(date: String, context: android.content.Context, onChange: (String) -> Unit) {
    OutlinedButton(onClick = {
        val current = LocalDate.parse(date)
        DatePickerDialog(
            context,
            { _, y, m, d -> onChange(LocalDate.of(y, m + 1, d).toString()) },
            current.year, current.monthValue - 1, current.dayOfMonth
        ).show()
    }) { Text(date) }
}

private fun List<MealEntry>.mealsInRange(range: ClosedRange<String>?): List<MealEntry> =
    if (range == null) this else filter { it.date in range }

private fun List<ExerciseWithSets>.workoutsInRange(range: ClosedRange<String>?): List<ExerciseWithSets> =
    if (range == null) this else filter { it.exercise.date in range }

private fun List<BodyRecord>.bodyRecordsInRange(range: ClosedRange<String>?): List<BodyRecord> =
    if (range == null) this else filter { it.date in range }
