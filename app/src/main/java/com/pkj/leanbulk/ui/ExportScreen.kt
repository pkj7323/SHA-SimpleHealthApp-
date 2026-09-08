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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.pkj.leanbulk.data.backup.FoodDto
import com.pkj.leanbulk.data.backup.RoutineDto
import com.pkj.leanbulk.data.backup.Snapshot
import com.pkj.leanbulk.export.ShareUtils
import com.pkj.leanbulk.export.XlsxExporter
import com.pkj.leanbulk.ui.theme.ShaSpacing
import com.pkj.leanbulk.ui.theme.ShaTheme
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

    ShaScreen {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = ShaSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(ShaSpacing.md)
    ) {
        Text(
            "내보내기",
            style = ShaTheme.type.h1,
            color = ShaTheme.colors.ink,
            modifier = Modifier.padding(top = ShaSpacing.lg, bottom = ShaSpacing.md)
        )

        ShaCard {
        Column {
        SectionHeader("엑셀 내보내기", "운동일지 · 종목목록 · 신체기록 · 식단체크 · 사용법 시트로 내보냅니다.")

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = useRange, onCheckedChange = { useRange = it })
            Text(
                "기간 지정 (끄면 전체)",
                style = ShaTheme.type.body,
                color = ShaTheme.colors.ink
            )
        }

        if (useRange) {
            Row(
                Modifier.fillMaxWidth().padding(top = ShaSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(ShaSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DatePickButton(startDate, context, Modifier.weight(1f)) { startDate = it }
                Text("~", style = ShaTheme.type.body, color = ShaTheme.colors.muted)
                DatePickButton(endDate, context, Modifier.weight(1f)) { endDate = it }
            }
        }

        Spacer(Modifier.height(ShaSpacing.md))

        ShaPrimaryButton(
            text = if (useRange) "xlsx 만들기 ($startDate ~ $endDate)" else "xlsx 만들기",
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
        )

        // 만들기 전에는 공유할 것이 없다. 눌리지 않는 버튼을 미리 보여주는 대신 감춘다.
        if (lastFile != null) {
            Row(
                Modifier.fillMaxWidth().padding(top = ShaSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(ShaSpacing.md)
            ) {
                ShaSecondaryButton(
                    text = "공유",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        lastFile?.let { context.startActivity(ShareUtils.shareIntentFor(context, it)) }
                    }
                )
                ShaSecondaryButton(
                    text = "파일로 저장",
                    modifier = Modifier.weight(1f),
                    onClick = { lastFile?.let { saveLauncher.launch(it.name) } }
                )
            }
        }

        StatusLine(status)
        }
        }

        BackupSection(vm)

        UserFoodExportSection(vm)

        Spacer(Modifier.height(ShaSpacing.xxl))
    }
    }
}

/** 구역 머리. 제목과 한 줄 설명을 늘 같은 모양으로 붙인다. */
@Composable
private fun SectionHeader(title: String, description: String) {
    Text(title, style = ShaTheme.type.h2, color = ShaTheme.colors.ink)
    Text(
        description,
        style = ShaTheme.type.caption,
        color = ShaTheme.colors.muted,
        modifier = Modifier.padding(top = 2.dp, bottom = ShaSpacing.md)
    )
}

/** 진행 상태 한 줄. 할 말이 없으면 자리도 안 차지한다. */
@Composable
private fun StatusLine(status: String) {
    if (status.isBlank()) return
    Text(
        status,
        style = ShaTheme.type.caption,
        color = ShaTheme.colors.muted,
        modifier = Modifier.padding(top = ShaSpacing.md)
    )
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

    ShaCard {
    Column {
    SectionHeader("백업 · 복원", "모든 기록을 파일 하나로. 폰을 바꾸거나 기록을 옮길 때 씁니다.")

    ShaPrimaryButton(
        text = "백업 파일 만들기",
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
    )

    if (backupFile != null) {
        Row(
            Modifier.fillMaxWidth().padding(top = ShaSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(ShaSpacing.md)
        ) {
            ShaSecondaryButton(
                text = "공유",
                modifier = Modifier.weight(1f),
                onClick = {
                    backupFile?.let {
                        context.startActivity(ShareUtils.shareIntentFor(context, it, BackupFiles.MIME, "백업 공유하기"))
                    }
                }
            )
            ShaSecondaryButton(
                text = "파일로 저장",
                modifier = Modifier.weight(1f),
                onClick = { backupFile?.let { saveLauncher.launch(it.name) } }
            )
        }
    }

    ShaSecondaryButton(
        text = "백업 파일 불러오기",
        modifier = Modifier.fillMaxWidth().padding(top = ShaSpacing.md),
        onClick = { if (!working) openLauncher.launch(arrayOf("*/*")) }
    )

    StatusLine(status)
    }
    }

    pendingImport?.let { snapshot ->
        ShaDialog(
            title = "이 파일을 어떻게 쓸까요?",
            onDismiss = { pendingImport = null },
            confirmText = "골라서 가져오기",
            onConfirm = { pendingImport = null; mergeFrom = snapshot },
            // 전체 덮어쓰기는 되돌리기 번거로운 쪽이라 버튼 줄 위에 따로 둔다.
            destructiveText = "전체 덮어쓰기",
            onDestructive = {
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
        ) {
            Text(snapshot.summarize(), style = ShaTheme.type.body, color = ShaTheme.colors.bodyText)
            Text(
                "골라서 가져오면 지금 기록은 그대로 두고 고른 것만 더합니다. " +
                    "전체 덮어쓰기는 지금 기록을 모두 이 내용으로 바꿉니다(직전 상태는 앱 안에 남겨둡니다).",
                style = ShaTheme.type.caption,
                color = ShaTheme.colors.muted,
                modifier = Modifier.padding(top = ShaSpacing.md)
            )
        }
    }

    mergeFrom?.let { snapshot ->
        SelectiveMergeDialog(
            snapshot = snapshot,
            onMerge = { routines, templates, exercises, foods ->
                mergeFrom = null
                scope.launch {
                    working = true
                    status = runCatching {
                        vm.mergeFromSnapshot(snapshot.exercises, routines, templates, exercises, foods)
                    }.fold(
                        onSuccess = { r ->
                            "가져왔습니다 — 루틴 ${r.routines} · 식단표 ${r.dietTemplates}" +
                                (if (r.exercises > 0) " · 종목 ${r.exercises}" else "") +
                                (if (r.foods > 0) " · 음식 ${r.foods}" else "")
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
 * 직접 등록한 음식만 파일 하나로 뽑는다.
 *
 * 쓸모는 **다음 앱 버전의 시드에 넣을 후보를 모으는 것**이다(ROADMAP 3번). 쓰면서 쌓인
 * 음식을 개발자에게 파일로 넘기면 검수해서 시드에 합칠 수 있고, 그게 서버 없이 카탈로그를
 * 키우는 이 앱의 경로다. 전체 백업과 같은 포맷이라 "골라서 가져오기"로 그대로 되읽힌다.
 */
@Composable
private fun UserFoodExportSection(vm: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var file by remember { mutableStateOf<File?>(null) }
    var status by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupFiles.MIME)
    ) { uri: Uri? ->
        val made = file
        if (uri != null && made != null) {
            ShareUtils.copyTo(context, made, uri)
            status = "저장 완료"
        }
    }

    ShaCard {
    Column {
    SectionHeader("내 음식 내보내기", "직접 등록한 음식만 파일로. 다음 버전 기본 목록에 넣을 후보를 모읍니다.")

    ShaPrimaryButton(
        text = "내 음식 파일 만들기",
        modifier = Modifier.fillMaxWidth(),
        enabled = !working,
        onClick = {
            scope.launch {
                working = true
                status = runCatching {
                    val snapshot = vm.buildUserFoodSnapshot()
                    if (snapshot.foods.isEmpty()) null
                    else withContext(Dispatchers.IO) { BackupFiles.writeUserFoods(context, snapshot) }
                }.fold(
                    onSuccess = { made ->
                        file = made
                        if (made == null) "직접 등록한 음식이 아직 없습니다." else "음식 ${'$'}{made.name}"
                    },
                    onFailure = { "내보내기 실패: ${'$'}{it.message}" }
                )
                working = false
            }
        }
    )

    if (file != null) {
        Row(
            Modifier.fillMaxWidth().padding(top = ShaSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(ShaSpacing.md)
        ) {
            ShaSecondaryButton(
                text = "공유",
                modifier = Modifier.weight(1f),
                onClick = {
                    file?.let {
                        context.startActivity(ShareUtils.shareIntentFor(context, it, BackupFiles.MIME, "내 음식 공유하기"))
                    }
                }
            )
            ShaSecondaryButton(
                text = "파일로 저장",
                modifier = Modifier.weight(1f),
                onClick = { file?.let { saveLauncher.launch(it.name) } }
            )
        }
    }

    StatusLine(status)
    }
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
    onMerge: (List<RoutineDto>, List<DietTemplateDto>, List<ExerciseDto>, List<FoodDto>) -> Unit,
    onDismiss: () -> Unit
) {
    val routines = remember { mutableStateListOf<RoutineDto>() }
    val templates = remember { mutableStateListOf<DietTemplateDto>() }
    val exercises = remember { mutableStateListOf<ExerciseDto>() }
    val foods = remember { mutableStateListOf<FoodDto>() }

    // 시드 종목·음식은 내게도 이미 있으니 굳이 고르게 하지 않는다. 남이 직접 만든 것만 보여준다.
    val userExercises = remember(snapshot) { snapshot.exercises.filter { it.source == "USER" } }
    val userFoods = remember(snapshot) { snapshot.foods.filter { it.source == "USER" } }
    val nothingPicked =
        routines.isEmpty() && templates.isEmpty() && exercises.isEmpty() && foods.isEmpty()

    ShaDialog(
        title = "가져올 항목 고르기",
        onDismiss = onDismiss,
        confirmText = "가져오기",
        confirmEnabled = !nothingPicked,
        onConfirm = { onMerge(routines.toList(), templates.toList(), exercises.toList(), foods.toList()) }
    ) {
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
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
                if (userFoods.isNotEmpty()) {
                    item { SectionLabel("직접 등록한 음식") }
                    items(userFoods, key = { "f" + it.slug + it.name }) { dto ->
                        CheckRow(
                            checked = dto in foods,
                            title = dto.name,
                            subtitle = dto.category +
                                (dto.baseAmountGram?.let { " · 기준 ${it.toInt()}${dto.baseAmountUnit.lowercase()}" } ?: ""),
                            onToggle = { if (dto in foods) foods.remove(dto) else foods.add(dto) }
                        )
                    }
                }
                item {
                    Text(
                        "고른 루틴이 쓰는 종목은 자동으로 같이 들어옵니다. " +
                            "이름이 겹치면 덮어쓰지 않고 뒤에 번호를 붙입니다.",
                        style = ShaTheme.type.caption,
                        color = ShaTheme.colors.muted,
                        modifier = Modifier.padding(top = ShaSpacing.md)
                    )
                }
            }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = ShaTheme.type.caption,
        color = ShaTheme.colors.muted,
        modifier = Modifier.padding(top = ShaSpacing.md, bottom = ShaSpacing.xs)
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
            Text(
                title,
                style = ShaTheme.type.bodyStrong,
                color = ShaTheme.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(subtitle, style = ShaTheme.type.caption, color = ShaTheme.colors.muted)
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
private fun DatePickButton(
    date: String,
    context: android.content.Context,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit
) {
    ShaSecondaryButton(
        text = date,
        modifier = modifier,
        onClick = {
            val current = LocalDate.parse(date)
            DatePickerDialog(
                context,
                { _, y, m, d -> onChange(LocalDate.of(y, m + 1, d).toString()) },
                current.year, current.monthValue - 1, current.dayOfMonth
            ).show()
        }
    )
}

private fun List<MealEntry>.mealsInRange(range: ClosedRange<String>?): List<MealEntry> =
    if (range == null) this else filter { it.date in range }

private fun List<ExerciseWithSets>.workoutsInRange(range: ClosedRange<String>?): List<ExerciseWithSets> =
    if (range == null) this else filter { it.exercise.date in range }

private fun List<BodyRecord>.bodyRecordsInRange(range: ClosedRange<String>?): List<BodyRecord> =
    if (range == null) this else filter { it.date in range }
