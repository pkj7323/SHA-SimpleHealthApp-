package com.pkj.leanbulk.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.pkj.leanbulk.data.BodyRecord
import com.pkj.leanbulk.ui.theme.ShaSpacing
import com.pkj.leanbulk.ui.theme.ShaTheme
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
    // 탭을 옮겼다 와도 보던 방식이 유지되게 rememberSaveable로 둔다.
    var asChart by rememberSaveable { mutableStateOf(false) }
    val colors = ShaTheme.colors
    val type = ShaTheme.type

    ShaScreen {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ShaSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(ShaSpacing.md)
        ) {
            item {
                Text(
                    "신체",
                    style = type.h1,
                    color = colors.ink,
                    modifier = Modifier.padding(top = ShaSpacing.lg, bottom = ShaSpacing.md)
                )
            }

            // 기록이 없으면 보여줄 값 자체가 없다. 0으로 채운 타일은 "0kg"이라는 거짓말이 된다.
            if (records.isNotEmpty()) {
                item { LatestStats(records) }
            }

            item {
                BodyRecordForm(
                    initial = editing,
                    today = today,
                    onSave = { vm.saveBodyRecord(it); editing = null },
                    onCancelEdit = { editing = null }
                )
            }

            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = ShaSpacing.lg, bottom = ShaSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("기록", style = type.h2, color = colors.ink, modifier = Modifier.weight(1f))
                    if (records.isNotEmpty()) {
                        SubTabBar(
                            tabs = listOf("목록", "그래프"),
                            selected = if (asChart) 1 else 0,
                            onSelect = { asChart = it == 1 }
                        )
                    }
                }
            }

            if (records.isEmpty()) {
                item {
                    Text(
                        "아직 기록이 없습니다. 위에서 체중을 적어 첫 기록을 남겨보세요.",
                        style = type.body,
                        color = colors.muted,
                        modifier = Modifier.padding(vertical = ShaSpacing.md)
                    )
                }
            } else if (asChart) {
                val series = metricSeries(records)
                if (series.isEmpty()) {
                    item {
                        Text(
                            "그래프를 그리려면 같은 항목을 세 번 이상 기록해야 합니다.",
                            style = type.body,
                            color = colors.muted,
                            modifier = Modifier.padding(vertical = ShaSpacing.md)
                        )
                    }
                } else {
                    items(series, key = { it.label }) { MetricChartCard(it) }
                }
            } else {
                items(records, key = { it.id }) { record ->
                    RecordCard(
                        record = record,
                        onEdit = { editing = record },
                        onDelete = { vm.deleteBodyRecord(record.id) }
                    )
                }
            }

            item { Box(Modifier.padding(bottom = ShaSpacing.xl)) }
        }
    }
}

/**
 * 맨 위 숫자 세 칸. [records]는 최신이 앞이다.
 *
 * 델타는 **바로 앞 기록과의 차이**다. 체성분은 2주에 한 번 재는 값이라 "어제보다"라는
 * 개념이 없고, 지난번과 비교하는 것이 사용자가 실제로 궁금해하는 것이다.
 */
@Composable
private fun LatestStats(records: List<BodyRecord>) {
    val colors = ShaTheme.colors
    val latest = records.first()
    val previous = records.getOrNull(1)

    Row(horizontalArrangement = Arrangement.spacedBy(ShaSpacing.md)) {
        // 단위를 안 붙인다. 3분할 타일에 "72.3kg"은 안 들어가서 "k"로 잘리고,
        // 라벨이 이미 무엇인지 말하며 아래 델타가 단위를 들고 있다(칼로리 타일과 같은 판단).
        StatTile(
            label = "체중",
            value = fmt(latest.weightKg),
            unit = "",
            delta = deltaText(latest.weightKg, previous?.weightKg, "kg"),
            modifier = Modifier.weight(1f)
        )
        StatTile(
            label = "골격근량",
            value = latest.muscleMassKg?.let(::fmt) ?: "?",
            unit = "",
            delta = deltaText(latest.muscleMassKg, previous?.muscleMassKg, "kg"),
            deltaTone = toneForGain(latest.muscleMassKg, previous?.muscleMassKg, colors.primary),
            modifier = Modifier.weight(1f)
        )
        StatTile(
            label = "체지방률",
            value = latest.fatPercent?.let(::fmt) ?: "?",
            unit = "%",
            delta = deltaText(latest.fatPercent, previous?.fatPercent, "%"),
            modifier = Modifier.weight(1f)
        )
    }
}

/** 비교할 값이 없으면 아무 말도 안 한다 — 첫 기록에 "+0"은 사실이 아니다. */
private fun deltaText(now: Double?, before: Double?, unit: String): String? {
    if (now == null || before == null) return null
    val diff = (now - before)
    if (kotlin.math.abs(diff) < 0.05) return "그대로"
    val sign = if (diff > 0) "+" else "−"
    return sign + fmt(kotlin.math.abs(round1(diff))) + unit
}

/** 근육은 늘어난 게 좋은 소식이다. 체중·체지방은 목적에 따라 다르므로 색을 안 준다. */
private fun toneForGain(now: Double?, before: Double?, good: Color): Color? =
    if (now != null && before != null && now > before) good else null

/**
 * 한 항목의 시간 흐름. [points]는 오래된 것이 앞이다.
 *
 * 항목마다 카드를 따로 그린다. 체중과 체지방률을 한 그림에 겹치면 축이 달라서
 * (kg vs %) 어느 쪽 기울기도 사실이 아니게 된다.
 */
private data class MetricSeries(
    val label: String,
    val unit: String,
    val points: List<Pair<String, Double>>
)

/**
 * 그래프로 그릴 만한 항목만 골라낸다.
 *
 * **점이 셋 미만인 항목은 아예 뺀다.** 두 점을 이은 직선은 추세가 아니라 그냥 선이고,
 * 그걸 그래프로 보여주면 없는 경향을 읽게 만든다. 항목마다 따로 세는 이유는 체중만
 * 열 번 적고 허리는 한 번 잰 경우가 흔하기 때문이다.
 */
private fun metricSeries(records: List<BodyRecord>): List<MetricSeries> {
    val old = records.reversed() // 화면에 온 목록은 최신이 앞이다.
    fun series(label: String, unit: String, pick: (BodyRecord) -> Double?) = MetricSeries(
        label = label,
        unit = unit,
        points = old.mapNotNull { r -> pick(r)?.let { r.date to it } }
    )
    return listOf(
        series("체중", "kg") { it.weightKg },
        series("골격근량", "kg") { it.muscleMassKg },
        series("체지방량", "kg") { it.fatMassKg },
        series("체지방률", "%") { it.fatPercent },
        series("허리둘레", "cm") { it.waistCm }
    ).filter { it.points.size >= 3 }
}

@Composable
private fun MetricChartCard(series: MetricSeries) {
    val colors = ShaTheme.colors
    val type = ShaTheme.type
    val values = series.points.map { it.second }
    val min = values.min()
    val max = values.max()
    // 값이 다 같으면 나눗셈이 0으로 간다. 그때는 가운데 수평선으로 그린다.
    val span = (max - min).takeIf { it > 0.0001 }
    val first = values.first()
    val last = values.last()

    ShaCard {
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(series.label, style = type.h2, color = colors.ink, modifier = Modifier.weight(1f))
                Text(fmt(last), style = type.data, color = colors.ink)
                Text(
                    series.unit,
                    style = type.caption,
                    color = colors.muted,
                    modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
                )
            }
            Text(
                // 첫 기록 대비 얼마나 움직였는지. 그래프의 기울기를 숫자로 한 번 더 말해준다.
                deltaText(last, first, series.unit)?.let { it + " (처음 대비)" } ?: "변화 없음",
                style = type.caption,
                color = colors.muted
            )

            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .padding(top = ShaSpacing.md)
                    .testTag("metric_chart_" + series.label)
            ) {
                val stepX = if (values.size > 1) size.width / (values.size - 1) else 0f
                fun yOf(value: Double): Float {
                    val ratio = span?.let { (value - min) / it } ?: 0.5
                    // 위아래로 살짝 여백을 둔다. 최고·최저점이 선 굵기만큼 잘려 보이지 않게.
                    return (size.height - 8f) - (ratio.toFloat() * (size.height - 16f))
                }

                val path = Path()
                values.forEachIndexed { i, v ->
                    val x = i * stepX
                    val y = yOf(v)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color = colors.primaryFill, style = Stroke(width = 5f))

                // 점을 다 찍되 마지막만 크게. 잰 횟수가 몇 번인지 보이면서도 지금 값이 도드라진다.
                values.forEachIndexed { i, v ->
                    drawCircle(
                        color = colors.primaryFill,
                        radius = if (i == values.lastIndex) 9f else 4f,
                        center = Offset(i * stepX, yOf(v))
                    )
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = ShaSpacing.sm)) {
                Text(shortDate(series.points.first().first), style = type.caption, color = colors.muted)
                Box(Modifier.weight(1f))
                Text(
                    fmt(min) + " ~ " + fmt(max) + series.unit,
                    style = type.caption,
                    color = colors.muted
                )
                Box(Modifier.weight(1f))
                Text(shortDate(series.points.last().first), style = type.caption, color = colors.muted)
            }
        }
    }
}

/** "2026-09-08" → "9.8". 그래프 양 끝 라벨은 좁아서 연도까지 넣으면 서로 붙는다. */
private fun shortDate(iso: String): String {
    val parts = iso.split("-")
    if (parts.size != 3) return iso
    return parts[1].trimStart('0') + "." + parts[2].trimStart('0')
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

    val colors = ShaTheme.colors
    val type = ShaTheme.type
    val context = LocalContext.current

    ShaCard {
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (initial == null) "새 기록" else "기록 수정",
                    style = type.h2,
                    color = colors.ink,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    date,
                    style = type.bodyStrong,
                    color = colors.primary,
                    modifier = Modifier
                        .clickable {
                            val current = LocalDate.parse(date)
                            DatePickerDialog(
                                context,
                                { _, y, m, d -> date = LocalDate.of(y, m + 1, d).toString() },
                                current.year, current.monthValue - 1, current.dayOfMonth
                            ).show()
                        }
                        .padding(ShaSpacing.sm)
                )
            }

            Text(
                "체중만 있어도 저장됩니다. 나머지는 모르면 비워두세요.",
                style = type.caption,
                color = colors.muted,
                modifier = Modifier.padding(bottom = ShaSpacing.md)
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ShaSpacing.md)
            ) {
                NullableNumberField("체중(kg)", weight, Modifier.weight(1f), ::onWeightChange)
                NullableNumberField("골격근량", muscle, Modifier.weight(1f)) { muscle = it }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = ShaSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(ShaSpacing.md)
            ) {
                NullableNumberField("체지방량", fatMass, Modifier.weight(1f), ::onFatMassChange)
                NullableNumberField("체지방률%", fatPercent, Modifier.weight(1f), ::onFatPercentChange)
                NullableNumberField("허리(cm)", waist, Modifier.weight(1f)) { waist = it }
            }

            // 체지방량/체지방률 중 하나만 입력해도 체중을 기준으로 나머지가 실시간으로 계산된다.
            if (weight != null && weight!! > 0 && fatDrivenBy != null) {
                Text(
                    if (fatDrivenBy == FatField.PERCENT) "체지방량은 체지방률 기준으로 자동 계산됩니다"
                    else "체지방률은 체지방량 기준으로 자동 계산됩니다",
                    style = type.caption,
                    color = colors.muted,
                    modifier = Modifier.padding(top = ShaSpacing.sm)
                )
            }

            ShaTextField(
                label = "측정도구",
                value = tool,
                onValueChange = { tool = it },
                placeholder = "인바디, 갤럭시워치 …",
                modifier = Modifier.padding(top = ShaSpacing.md)
            )
            ShaTextField(
                label = "메모",
                value = memo,
                onValueChange = { memo = it },
                modifier = Modifier.padding(top = ShaSpacing.md)
            )

            Row(
                Modifier.fillMaxWidth().padding(top = ShaSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(ShaSpacing.md)
            ) {
                if (initial != null) {
                    ShaSecondaryButton("수정 취소", onCancelEdit, Modifier.weight(1f))
                }
                ShaPrimaryButton(
                    text = if (initial == null) "기록 저장" else "수정 저장",
                    enabled = (weight ?: 0.0) > 0,
                    modifier = Modifier.weight(1f).testTag("body_save"),
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
                )
            }
        }
    }
}

@Composable
private fun RecordCard(record: BodyRecord, onEdit: () -> Unit, onDelete: () -> Unit) {
    val colors = ShaTheme.colors
    val type = ShaTheme.type

    ShaCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(record.date, style = type.caption, color = colors.muted)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(fmt(record.weightKg), style = type.data, color = colors.ink)
                    Text(
                        "kg",
                        style = type.caption,
                        color = colors.muted,
                        modifier = Modifier.padding(start = 2.dp, bottom = 2.dp)
                    )
                }
                val detail = listOfNotNull(
                    record.muscleMassKg?.let { "골격근 " + fmt(it) + "kg" },
                    record.fatPercent?.let { "체지방 " + fmt(it) + "%" },
                    record.waistCm?.let { "허리 " + fmt(it) + "cm" },
                    record.tool.takeIf { it.isNotBlank() },
                    record.memo.takeIf { it.isNotBlank() }
                )
                if (detail.isNotEmpty()) {
                    Text(detail.joinToString(" · "), style = type.caption, color = colors.muted)
                }
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.Edit, contentDescription = "수정",
                    tint = colors.muted, modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.Delete, contentDescription = "삭제",
                    tint = colors.muted, modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun fmt(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

/** 체지방량/체지방률 중 사용자가 방금 직접 입력한 쪽. 나머지는 이 값을 기준으로 계산된다. */
private enum class FatField { MASS, PERCENT }

private fun round1(value: Double): Double = (value * 10).roundToInt() / 10.0
