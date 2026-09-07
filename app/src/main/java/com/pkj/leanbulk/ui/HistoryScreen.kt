package com.pkj.leanbulk.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pkj.leanbulk.data.ExerciseWithSets
import com.pkj.leanbulk.data.MealEntry
import com.pkj.leanbulk.data.hasContent
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(vm: MainViewModel) {
    val meals by vm.allMeals.collectAsState()
    val workouts by vm.allWorkouts.collectAsState()

    // 날짜별로 한 번만 묶어둔다. 매 항목마다 전체 목록을 다시 훑으면 기록이 쌓일수록 느려진다.
    val days = remember(meals, workouts) {
        val mealsByDate = meals.groupBy { it.date }
        val workoutsByDate = workouts.groupBy { it.exercise.date }
        (mealsByDate.keys + workoutsByDate.keys)
            .sortedDescending()
            .map { date ->
                DayRecord(
                    date = date,
                    meals = mealsByDate[date].orEmpty(),
                    workouts = workoutsByDate[date].orEmpty()
                )
            }
    }

    LazyColumn(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(days, key = { it.date }) { day ->
            DayCard(date = day.date, dayMeals = day.meals, dayWorkouts = day.workouts)
        }
    }
}

private data class DayRecord(
    val date: String,
    val meals: List<MealEntry>,
    val workouts: List<ExerciseWithSets>
)

@Composable
private fun DayCard(date: String, dayMeals: List<MealEntry>, dayWorkouts: List<ExerciseWithSets>) {
    var expanded by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            // 펼치기/접기는 이 헤더에만 건다. 아래 상세 텍스트에 클릭이 걸리면
            // 길게 눌러 드래그하는 "텍스트 선택" 제스처를 가로채버리기 때문.
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
            ) {
                Text(date, fontWeight = FontWeight.Bold)

                // 빈 칸(아무것도 안 담은 끼니)은 세지 않는다 — 고정 네 칸이라 늘 같이 저장된다.
                val filled = dayMeals.filter { it.hasContent }
                if (filled.isNotEmpty()) {
                    val kcal = filled.sumOf { it.macros.kcal ?: 0.0 }.roundToInt()
                    val protein = filled.sumOf { it.macros.protein ?: 0.0 }.roundToInt()
                    Text("식단: ${kcal}kcal · 단백질 ${protein}g (${filled.size}끼니)")
                }
                if (dayWorkouts.isNotEmpty()) {
                    val routine = dayWorkouts.first().exercise.routineName.ifBlank { "운동" }
                    Text("$routine · 총 볼륨 ${dayWorkouts.sumOf { it.volume }.roundToInt()}kg (${dayWorkouts.size}종목)")
                }
                if (filled.isEmpty() && dayWorkouts.isEmpty()) {
                    Text("기록 없음")
                }

                // 메모는 펼쳐야 보이므로, 접힌 상태에서도 있다는 것만 알려준다.
                val memoCount = filled.count { it.memo.isNotBlank() } +
                    dayWorkouts.count { it.exercise.memo.isNotBlank() }
                if (memoCount > 0) {
                    Text("메모 ${memoCount}개", style = MaterialTheme.typography.bodySmall)
                }

                Text(
                    if (expanded) "▲ 접기" else "▼ 자세히 보기",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            if (expanded) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                // 펼칠 때마다 문자열을 다시 만들 필요는 없다.
                val detail = remember(dayMeals, dayWorkouts) { buildDayDetail(date, dayMeals, dayWorkouts) }
                val clipboard = LocalClipboardManager.current

                SelectionContainer {
                    Text(
                        text = detail,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                OutlinedButton(
                    onClick = { clipboard.setText(AnnotatedString(detail)) },
                    modifier = Modifier.padding(top = 8.dp)
                ) { Text("전체 복사") }
            }
        }
    }
}

/** 그날 기록을 사람이 읽기 좋은(그리고 복사해서 붙여넣기 좋은) 텍스트로 만든다. */
private fun buildDayDetail(
    date: String,
    meals: List<MealEntry>,
    workouts: List<ExerciseWithSets>
): String = buildString {
    appendLine("[$date]")

    if (workouts.isNotEmpty()) {
        val routine = workouts.first().exercise.routineName
        appendLine()
        appendLine("■ 운동${if (routine.isBlank()) "" else " — $routine"}")
        workouts.sortedBy { it.exercise.orderIndex }.forEach { item ->
            appendLine()
            appendLine("· ${item.exercise.exerciseName}")
            item.sets.sortedBy { it.setIndex }.forEach { s ->
                val mark = if (s.done) "✓" else "·"
                val weight = if (s.weightKg > 0) "${trimNum(s.weightKg)}kg × " else ""
                appendLine("   $mark ${s.setIndex + 1}세트  $weight${s.reps}회")
            }
            appendLine("   → 총 ${item.totalReps}회 · 볼륨 ${item.volume.roundToInt()}kg")
            if (item.exercise.memo.isNotBlank()) appendLine("   메모: ${item.exercise.memo}")
        }
        appendLine()
        appendLine("총 볼륨 ${workouts.sumOf { it.volume }.roundToInt()}kg")
    }

    val filled = meals.filter { it.hasContent }
    if (filled.isNotEmpty()) {
        appendLine()
        appendLine("■ 식단")
        filled.sortedBy { it.orderIndex }.forEach { m ->
            appendLine()
            val kcalText = m.macros.kcal?.let { "${it.roundToInt()}kcal" } ?: "영양정보 없음"
            appendLine("· ${m.slotName}  $kcalText")
            if (m.menuText.isNotBlank()) appendLine("   ${m.menuText}")
            if (!m.macros.isEmpty) {
                appendLine(
                    "   단백질 ${num(m.macros.protein)}g · 탄수 ${num(m.macros.carbs)}g · " +
                        "지방 ${num(m.macros.fat)}g · 당 ${num(m.macros.sugar)}g"
                )
            }
            if (m.memo.isNotBlank()) appendLine("   메모: ${m.memo}")
        }

        appendLine()
        appendLine(
            "합계 ${filled.sumOf { it.macros.kcal ?: 0.0 }.roundToInt()}kcal · " +
                "단백질 ${filled.sumOf { it.macros.protein ?: 0.0 }.roundToInt()}g · " +
                "탄수 ${filled.sumOf { it.macros.carbs ?: 0.0 }.roundToInt()}g · " +
                "지방 ${filled.sumOf { it.macros.fat ?: 0.0 }.roundToInt()}g · " +
                "당 ${filled.sumOf { it.macros.sugar ?: 0.0 }.roundToInt()}g"
        )

        val unknown = filled.count { it.macros.isEmpty }
        if (unknown > 0) appendLine("(영양정보를 모르는 ${unknown}끼니는 합계에서 빠짐)")
    }
}

/** 모르는 값(null)은 "?"로 표시해 0과 구분한다. */
private fun num(value: Double?): String = value?.roundToInt()?.toString() ?: "?"

private fun trimNum(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
