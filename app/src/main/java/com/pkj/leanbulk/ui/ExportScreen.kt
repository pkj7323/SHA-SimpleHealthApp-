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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pkj.leanbulk.data.BodyRecord
import com.pkj.leanbulk.data.ExerciseWithSets
import com.pkj.leanbulk.data.MealEntry
import com.pkj.leanbulk.export.ShareUtils
import com.pkj.leanbulk.export.XlsxExporter
import kotlinx.coroutines.launch
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

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
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
    }
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
