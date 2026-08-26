package com.pkj.leanbulk.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pkj.leanbulk.export.ShareUtils
import com.pkj.leanbulk.export.XlsxExporter
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun ExportScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val meals by vm.allMeals.collectAsState()
    val workouts by vm.allWorkouts.collectAsState()
    val bodyRecords by vm.bodyRecords.collectAsState()
    val routines by vm.routines.collectAsState()
    val target by vm.target.collectAsState()

    var lastFile by remember { mutableStateOf<File?>(null) }
    var status by remember { mutableStateOf("") }

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

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                scope.launch {
                    val file = XlsxExporter.export(context, meals, workouts, bodyRecords, routines, target)
                    lastFile = file
                    status = "생성 완료: ${file.name}"
                }
            }
        ) { Text("xlsx 파일 생성") }

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
