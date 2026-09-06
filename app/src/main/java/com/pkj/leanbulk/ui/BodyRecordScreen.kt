package com.pkj.leanbulk.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.pkj.leanbulk.data.BodyRecord
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * 체중/체성분 기록 화면. 2주 간격 측정을 전제로 하므로 날짜를 골라 한 건씩 남긴다.
 */
@Composable
fun BodyRecordScreen(vm: MainViewModel) {
    val today by vm.today.collectAsState()
    val records by vm.bodyRecords.collectAsState()

    var editing by remember { mutableStateOf<BodyRecord?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                BodyRecordForm(
                    initial = editing,
                    today = today,
                    onSave = { vm.saveBodyRecord(it); editing = null },
                    onCancelEdit = { editing = null }
                )
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text("기록 목록", style = MaterialTheme.typography.titleMedium)
            }
        }

        if (records.isEmpty()) {
            item { Text("아직 기록이 없습니다. 위에서 체중을 입력해 첫 기록을 남겨보세요.") }
        } else {
            items(records, key = { it.id }) { record ->
                RecordCard(
                    record = record,
                    onEdit = { editing = record },
                    onDelete = { vm.deleteBodyRecord(record.id) }
                )
            }
        }
    }
}

@Composable
private fun BodyRecordForm(
    initial: BodyRecord?,
    today: String,
    onSave: (BodyRecord) -> Unit,
    onCancelEdit: () -> Unit
) {
    // initial이 바뀌면(= 수정 버튼을 누르면) 입력칸을 그 기록 값으로 다시 채운다.
    var date by remember(initial) { mutableStateOf(initial?.date ?: today) }
    var weight by remember(initial) { mutableStateOf(initial?.weightKg) }
    var muscle by remember(initial) { mutableStateOf(initial?.muscleMassKg) }
    var fatMass by remember(initial) { mutableStateOf(initial?.fatMassKg) }
    var fatPercent by remember(initial) { mutableStateOf(initial?.fatPercent) }
    var waist by remember(initial) { mutableStateOf(initial?.waistCm) }
    var tool by remember(initial) { mutableStateOf(initial?.tool ?: "") }
    var memo by remember(initial) { mutableStateOf(initial?.memo ?: "") }

    // 체지방량/체지방률 중 방금 직접 입력한 쪽을 "기준"으로 삼아 나머지 하나를 실시간으로 채운다.
    var fatDrivenBy by remember(initial) {
        mutableStateOf<FatField?>(
            when {
                initial?.fatPercent != null -> FatField.PERCENT
                initial?.fatMassKg != null -> FatField.MASS
                else -> null
            }
        )
    }

    fun onWeightChange(value: Double?) {
        weight = value
        val w = value
        if (w != null && w > 0) {
            when (fatDrivenBy) {
                FatField.PERCENT -> fatPercent?.let { fatMass = round1(w * it / 100) }
                FatField.MASS -> fatMass?.let { fatPercent = round1(it / w * 100) }
                null -> Unit
            }
        }
    }

    fun onFatMassChange(value: Double?) {
        fatMass = value
        fatDrivenBy = FatField.MASS
        val w = weight
        if (value != null && w != null && w > 0) fatPercent = round1(value / w * 100)
    }

    fun onFatPercentChange(value: Double?) {
        fatPercent = value
        fatDrivenBy = FatField.PERCENT
        val w = weight
        if (value != null && w != null && w > 0) fatMass = round1(w * value / 100)
    }

    val context = LocalContext.current

    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = {
                val current = LocalDate.parse(date)
                DatePickerDialog(
                    context,
                    { _, y, m, d -> date = LocalDate.of(y, m + 1, d).toString() },
                    current.year, current.monthValue - 1, current.dayOfMonth
                ).show()
            }) { Text(date) }

            Box(Modifier.weight(1f))

            if (initial != null) {
                OutlinedButton(onClick = onCancelEdit) { Text("취소") }
                Box(Modifier.padding(horizontal = 4.dp))
            }
            OutlinedButton(
                enabled = (weight ?: 0.0) > 0,
                onClick = {
                    onSave(
                        BodyRecord(
                            id = initial?.id ?: 0,
                            date = date,
                            weightKg = weight ?: 0.0,
                            muscleMassKg = muscle,
                            fatMassKg = fatMass,
                            fatPercent = fatPercent,
                            waistCm = waist,
                            tool = tool.trim(),
                            memo = memo.trim()
                        )
                    )
                    // 다음 입력을 위해 비운다.
                    weight = null
                    muscle = null
                    fatMass = null
                    fatPercent = null
                    fatDrivenBy = null
                    waist = null
                    memo = ""
                }
            ) { Text(if (initial == null) "저장" else "수정 저장") }
        }

        Text(
            "체중만 있어도 저장됩니다. 나머지는 모르면 비워두세요.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp)
        )

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NullableNumberField("체중(kg)", weight, onValueChange = ::onWeightChange)
            NullableNumberField("골격근량", muscle) { muscle = it }
            NullableNumberField("체지방량", fatMass, onValueChange = ::onFatMassChange)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NullableNumberField("체지방률%", fatPercent, onValueChange = ::onFatPercentChange)
            NullableNumberField("허리(cm)", waist) { waist = it }
        }

        // 체지방량/체지방률 중 하나만 입력해도 체중을 기준으로 나머지가 실시간으로 계산된다.
        if (weight != null && weight!! > 0 && fatDrivenBy != null) {
            Text(
                if (fatDrivenBy == FatField.PERCENT) "체지방량은 체지방률 기준으로 자동 계산됩니다"
                else "체지방률은 체지방량 기준으로 자동 계산됩니다",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        OutlinedTextField(
            value = tool,
            onValueChange = { tool = it },
            label = { Text("측정도구 (예: 인바디, 갤럭시워치)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        OutlinedTextField(
            value = memo,
            onValueChange = { memo = it },
            label = { Text("메모") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
    }
}

@Composable
private fun RecordCard(record: BodyRecord, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(record.date, fontWeight = FontWeight.Bold)
                Text(
                    "체중 ${fmt(record.weightKg)}kg" + buildString {
                        record.muscleMassKg?.let { append(" · 골격근 ${fmt(it)}kg") }
                        record.fatMassKg?.let { append(" · 체지방 ${fmt(it)}kg") }
                        record.fatPercent?.let { append(" (${it.roundToInt()}%)") }
                    }
                )
                val extra = listOfNotNull(
                    record.waistCm?.let { "허리 ${fmt(it)}cm" },
                    record.tool.takeIf { it.isNotBlank() },
                    record.memo.takeIf { it.isNotBlank() }
                )
                if (extra.isNotEmpty()) {
                    Text(extra.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                }
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "수정") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "삭제") }
        }
    }
}

private fun fmt(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

/** 체지방량/체지방률 중 사용자가 방금 직접 입력한 쪽. 나머지는 이 값을 기준으로 계산된다. */
private enum class FatField { MASS, PERCENT }

private fun round1(value: Double): Double = (value * 10).roundToInt() / 10.0
