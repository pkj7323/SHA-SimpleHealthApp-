package com.pkj.leanbulk.ui

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.pkj.leanbulk.data.ExerciseWithSets
import com.pkj.leanbulk.data.MealEntry
import com.pkj.leanbulk.data.WorkoutSession
import com.pkj.leanbulk.data.hasContent
import com.pkj.leanbulk.ui.theme.ShaSpacing
import com.pkj.leanbulk.ui.theme.ShaTheme
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(vm: MainViewModel) {
    val meals by vm.allMeals.collectAsState()
    val workouts by vm.allWorkouts.collectAsState()
    val sessions by vm.allWorkoutSessions.collectAsState()

    // 날짜별로 한 번만 묶어둔다. 매 항목마다 전체 목록을 다시 훑으면 기록이 쌓일수록 느려진다.
    val days = remember(meals, workouts, sessions) {
        val mealsByDate = meals.groupBy { it.date }
        val workoutsByDate = workouts.groupBy { it.exercise.date }
        val sessionsByDate = sessions.associateBy { it.date }
        (mealsByDate.keys + workoutsByDate.keys)
            .sortedDescending()
            .map { date ->
                DayRecord(
                    date = date,
                    meals = mealsByDate[date].orEmpty(),
                    workouts = workoutsByDate[date].orEmpty(),
                    session = sessionsByDate[date]
                )
            }
    }

    val colors = ShaTheme.colors
    val type = ShaTheme.type

    ShaScreen {
        LazyColumn(
            Modifier.fillMaxWidth().padding(horizontal = ShaSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(ShaSpacing.md)
        ) {
            item {
                Text(
                    "히스토리",
                    style = type.h1,
                    color = colors.ink,
                    modifier = Modifier.padding(top = ShaSpacing.lg, bottom = ShaSpacing.md)
                )
            }

            if (days.isEmpty()) {
                item {
                    Text(
                        "아직 기록이 없습니다.",
                        style = type.body,
                        color = colors.muted,
                        modifier = Modifier.padding(vertical = ShaSpacing.md)
                    )
                }
            }

            items(days, key = { it.date }) { day ->
                DayCard(
                    date = day.date,
                    dayMeals = day.meals,
                    dayWorkouts = day.workouts,
                    session = day.session
                )
            }

            item { Box(Modifier.padding(bottom = ShaSpacing.xl)) }
        }
    }
}

private data class DayRecord(
    val date: String,
    val meals: List<MealEntry>,
    val workouts: List<ExerciseWithSets>,
    val session: WorkoutSession?
)

@Composable
private fun DayCard(
    date: String,
    dayMeals: List<MealEntry>,
    dayWorkouts: List<ExerciseWithSets>,
    session: WorkoutSession?
) {
    var expanded by remember { mutableStateOf(false) }
    val colors = ShaTheme.colors
    val type = ShaTheme.type

    // 빈 칸(아무것도 안 담은 끼니)은 세지 않는다 — 고정 네 칸이라 늘 같이 저장된다.
    val filled = dayMeals.filter { it.hasContent }
    val kcal = filled.sumOf { it.macros.kcal ?: 0.0 }.roundToInt()

    ShaCard {
        Column {
            // 펼치기/접기는 이 머리 줄에만 건다. 아래 상세 텍스트에 클릭이 걸리면
            // 길게 눌러 드래그하는 "글자 선택" 제스처를 가로채버리기 때문.
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        shaDateLabel(LocalDate.parse(date)),
                        style = type.bodyStrong,
                        color = colors.ink
                    )
                    Text(
                        summaryLine(filled.size, dayWorkouts, session, memoCount(filled, dayWorkouts)),
                        style = type.caption,
                        color = colors.muted
                    )
                }
                // 그날을 훑을 때 눈이 먼저 가는 숫자는 칼로리다. 없는 날엔 자리도 비운다.
                if (filled.isNotEmpty()) {
                    Text(kcal.toString(), style = type.data, color = colors.ink)
                    Text(
                        "kcal",
                        style = type.caption,
                        color = colors.muted,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "접기" else "자세히 보기",
                    tint = colors.muted,
                    modifier = Modifier.padding(start = ShaSpacing.sm)
                )
            }

            if (expanded) {
                // 펼칠 때마다 문자열을 다시 만들 필요는 없다.
                val detail = remember(dayMeals, dayWorkouts, session) {
                    buildDayDetail(date, dayMeals, dayWorkouts, session)
                }
                val clipboard = LocalClipboardManager.current

                CopyableHistoryBlock(
                    text = detail,
                    onCopy = { clipboard.setText(AnnotatedString(detail)) },
                    modifier = Modifier.padding(top = ShaSpacing.md)
                )
            }
        }
    }
}

/** 메모는 펼쳐야 보이므로, 접힌 상태에서도 있다는 것만 알려준다. */
private fun memoCount(meals: List<MealEntry>, workouts: List<ExerciseWithSets>): Int =
    meals.count { it.memo.isNotBlank() } + workouts.count { it.exercise.memo.isNotBlank() }

/**
 * 카드 접힌 상태의 한 줄 요약.
 *
 * 예전엔 식단·운동·시간·메모가 각자 한 줄씩이라 카드 하나가 다섯 줄이었다. 날짜만
 * 훑으려는 화면인데 카드가 커서 한 화면에 사흘이 안 들어왔다.
 */
private fun summaryLine(
    mealCount: Int,
    workouts: List<ExerciseWithSets>,
    session: WorkoutSession?,
    memos: Int
): String {
    val parts = buildList {
        if (workouts.isNotEmpty()) {
            val routine = workouts.first().exercise.routineName.ifBlank { "운동" }
            add(routine + " " + workouts.size + "종목")
            // 볼륨은 운동한 날의 핵심 숫자다. 무게를 안 쓰는 종목만 한 날은 0이라 뺀다.
            val volume = workouts.sumOf { it.volume }.roundToInt()
            if (volume > 0) add(volume.toString() + "kg")
        }
        // 아직 안 끝낸 운동은 시간을 안 적는다(`finishedMs`가 null).
        session?.finishedMs?.let { add(formatClock(it / 1000)) }
        if (mealCount > 0) add(mealCount.toString() + "끼니")
        if (memos > 0) add("메모 " + memos)
    }
    return if (parts.isEmpty()) "기록 없음" else parts.joinToString(" · ")
}

/** 그날 기록을 사람이 읽기 좋은(그리고 복사해서 붙여넣기 좋은) 텍스트로 만든다. */
private fun buildDayDetail(
    date: String,
    meals: List<MealEntry>,
    workouts: List<ExerciseWithSets>,
    session: WorkoutSession?
): String = buildString {
    appendLine("[$date]")

    if (workouts.isNotEmpty()) {
        val routine = workouts.first().exercise.routineName
        appendLine()
        appendLine("■ 운동${if (routine.isBlank()) "" else " — $routine"}")
        session?.finishedMs?.let { appendLine("운동 시간 ${formatClock(it / 1000)}") }
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
