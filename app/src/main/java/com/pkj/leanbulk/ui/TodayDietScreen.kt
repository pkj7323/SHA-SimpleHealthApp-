package com.pkj.leanbulk.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pkj.leanbulk.data.ActivityLevel
import com.pkj.leanbulk.data.DietTarget
import com.pkj.leanbulk.data.GoalType
import com.pkj.leanbulk.data.TargetCalculator
import com.pkj.leanbulk.data.Macros
import com.pkj.leanbulk.data.MealPresetWithFoods
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun TodayDietScreen(vm: MainViewModel) {
    val date by vm.dietDate.collectAsState()
    val draft by vm.dietDraft.collectAsState()
    val foods by vm.foods.collectAsState()
    val target by vm.target.collectAsState()
    val savedAt by vm.savedAt.collectAsState()
    val latestWeight by vm.latestWeightKg.collectAsState()

    var showAddMeal by remember { mutableStateOf(false) }
    var showTargetDialog by remember { mutableStateOf(false) }
    var foodPickerFor by remember { mutableStateOf<Int?>(null) }
    var presetPickerFor by remember { mutableStateOf<Int?>(null) }
    var savePresetFor by remember { mutableStateOf<Int?>(null) }
    val mealPresets by vm.mealPresets.collectAsState()

    val totals = remember(draft) { draft.totals() }

    Box(Modifier.fillMaxSize()) {
        // 헤더/합계까지 전부 한 LazyColumn 안에 넣어야 위로 같이 스크롤된다.
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column {
                    // 날짜 + 기록 (운동과 별도로 저장된다)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        val context = LocalContext.current
                        OutlinedButton(onClick = {
                            val current = LocalDate.parse(date)
                            DatePickerDialog(
                                context,
                                { _, y, m, d -> vm.setDietDate(LocalDate.of(y, m + 1, d).toString()) },
                                current.year, current.monthValue - 1, current.dayOfMonth
                            ).show()
                        }) { Text(date) }
                        Box(Modifier.weight(1f))
                        // 첫 편집 전에도 자동 저장이 켜져 있다는 걸 알 수 있게 항상 표시한다.
                        Text(
                            text = savedAt?.let { "저장됨 $it" } ?: "자동 저장",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Box(Modifier.padding(horizontal = 4.dp))
                        OutlinedButton(onClick = { showTargetDialog = true }) { Text("목표") }
                    }

                    HorizontalDivider(Modifier.padding(vertical = 12.dp))

                    TotalsSection(totals, target)
                }
            }

            itemsIndexed(draft) { index, meal ->
                MealCard(
                    meal = meal,
                    // 고정 네 칸(아침·점심·저녁·간식)은 지우지 못한다. 추가로 만든 끼니만 지운다.
                    onRemove = if (index >= FIXED_MEAL_SLOTS.size) ({ vm.removeMeal(index) }) else null,
                    onAddFood = { foodPickerFor = index },
                    onOpenPresets = { presetPickerFor = index },
                    onRemoveFood = { foodIndex -> vm.removeFoodFromMeal(index, foodIndex) },
                    onMoveFood = { from, to -> vm.moveFoodInMeal(index, from, to) }
                )
            }

            // 마지막 카드가 + 버튼에 가리지 않도록 아래를 비워둔다.
            item { Box(Modifier.padding(bottom = 72.dp)) }
        }

        SmallFloatingActionButton(
            onClick = { showAddMeal = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "끼니 추가")
        }
    }

    if (showAddMeal) {
        TextInputDialog(
            title = "끼니 추가",
            label = "끼니 이름 (예: 아침, 간식)",
            initial = "",
            confirmText = "추가",
            onConfirm = { vm.addMeal(it); showAddMeal = false },
            onDismiss = { showAddMeal = false }
        )
    }

    if (showTargetDialog) {
        TargetDialog(
            target = target,
            latestWeightKg = latestWeight,
            onSave = { vm.saveTarget(it); showTargetDialog = false },
            onDismiss = { showTargetDialog = false }
        )
    }

    foodPickerFor?.let { index ->
        FoodQuantityPickerDialog(
            foods = foods,
            onConfirm = { food, multiplier -> vm.addFoodToMeal(index, food, multiplier) },
            onToggleFavorite = vm::toggleFoodFavorite,
            onAddNewFood = { name, category, macros, baseAmountGram ->
                vm.addNewFood(name, category, macros, baseAmountGram)
            },
            onHide = vm::hideFood,
            onDismiss = { foodPickerFor = null }
        )
    }

    presetPickerFor?.let { index ->
        MealPresetDialog(
            presets = mealPresets,
            canSaveCurrent = draft.getOrNull(index)?.foods?.isNotEmpty() == true,
            onPick = { vm.addPresetToMeal(index, it) },
            onDelete = vm::deleteMealPreset,
            onSaveCurrent = { savePresetFor = index; presetPickerFor = null },
            onDismiss = { presetPickerFor = null }
        )
    }

    savePresetFor?.let { index ->
        TextInputDialog(
            title = "지금 조합을 내 식사로 저장",
            label = "내 식사 이름",
            initial = "",
            confirmText = "저장",
            onConfirm = { vm.saveMealAsPreset(index, it); savePresetFor = null },
            onDismiss = { savePresetFor = null }
        )
    }
}

@Composable
private fun TotalsSection(totals: DietTotals, target: DietTarget?) {
    Text("오늘 섭취", style = MaterialTheme.typography.titleMedium)

    MacroBar("칼로리", totals.entered.kcal, target?.kcal, "kcal")
    MacroBar("단백질", totals.entered.protein, target?.protein, "g")
    MacroBar("탄수화물", totals.entered.carbs, target?.carbs, "g")
    MacroBar("지방", totals.entered.fat, target?.fat, "g")
    MacroBar("당", totals.entered.sugar, target?.sugar, "g")

    if (totals.unknownCount > 0) {
        Text(
            "· 영양정보를 모르는 끼니 ${totals.unknownCount}개는 합계에 안 들어갔습니다",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun MacroBar(label: String, value: Double?, target: Double?, unit: String) {
    val actual = value ?: 0.0
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            val targetText = target?.let { " / ${it.roundToInt()}" } ?: ""
            Text("${actual.roundToInt()}$targetText $unit")
        }
        if (target != null && target > 0) {
            LinearProgressIndicator(
                progress = { (actual / target).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 끼니 한 칸. 이름(아침·점심·저녁·간식)은 고정이라 고치지 못하고, 담긴 음식 줄의 합이 곧 이 끼니다.
 *
 * [onRemove]가 null이면 삭제 버튼을 안 그린다 — 고정 네 칸은 지우는 개념 자체가 없다.
 */
@Composable
private fun MealCard(
    meal: DraftMeal,
    onRemove: (() -> Unit)?,
    onAddFood: () -> Unit,
    onOpenPresets: () -> Unit,
    onRemoveFood: (Int) -> Unit,
    onMoveFood: (Int, Int) -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(meal.slotName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                onRemove?.let {
                    IconButton(onClick = it) {
                        Icon(Icons.Filled.Delete, contentDescription = "끼니 삭제")
                    }
                }
            }

            meal.foods.forEachIndexed { index, food ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(food.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(formatFoodQuantity(food), style = MaterialTheme.typography.bodySmall)
                    }
                    // 종목 순서 조정과 같은 이유 — 담는 순서가 늘 마지막이라 끌어올릴 수단이 필요하다.
                    IconButton(
                        onClick = { onMoveFood(index, index - 1) },
                        enabled = index > 0,
                        modifier = Modifier.size(36.dp)
                    ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "위로") }
                    IconButton(
                        onClick = { onMoveFood(index, index + 1) },
                        enabled = index < meal.foods.lastIndex,
                        modifier = Modifier.size(36.dp)
                    ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "아래로") }
                    IconButton(onClick = { onRemoveFood(index) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "음식 삭제")
                    }
                }
            }

            // 자유 텍스트로 적던 시절의 기록. 지금은 그렇게 적을 방법이 없지만, 남아 있는
            // 기록을 화면에서 숨기면 사용자 입장에선 사라진 것과 같으므로 읽기 전용으로 보여준다.
            if (meal.foods.isEmpty() && meal.menuText.isNotBlank()) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(meal.menuText)
                    Text(
                        "예전 방식으로 적어둔 기록 · ${formatMacros(meal.macros)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onAddFood) { Text("음식 추가") }
                OutlinedButton(onClick = onOpenPresets) { Text("내 식사") }
            }

            if (meal.foods.isNotEmpty()) {
                Text(
                    "합계 ${formatMacros(meal.macros)}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

private fun formatFoodQuantity(food: DraftMealFood): String {
    val qty = food.amountGram?.let { "${it.roundToInt()}g" } ?: "×${formatNumber(food.multiplier)}"
    return "$qty · ${formatMacros(food.macros)}"
}

private fun formatNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

/** 모르는 값은 0이 아니라 `?`로 적는다 — 0kcal로 적어두면 "안 먹었다"는 뜻이 돼버린다. */
private fun formatMacros(m: Macros): String {
    fun v(value: Double?) = value?.roundToInt()?.toString() ?: "?"
    return "${v(m.kcal)}kcal · 단백질 ${v(m.protein)}g · 탄수 ${v(m.carbs)}g · " +
        "지방 ${v(m.fat)}g · 당 ${v(m.sugar)}g"
}

// ---------- 다이얼로그 ----------

/**
 * "내 식사"(식사세트). 고른 조합을 이 끼니에 통째로 담고, 지금 담긴 조합을 새 세트로 저장한다.
 *
 * 예전의 "식단표"(하루 전체 틀)를 대신한다 — 끼니 칸이 아침·점심·저녁·간식으로 고정되면서
 * 하루 단위로 통째로 갈아끼우는 틀은 쓸모가 없어졌고, 실제로 쓰는 단위는 끼니 하나였다.
 */
@Composable
private fun MealPresetDialog(
    presets: List<MealPresetWithFoods>,
    canSaveCurrent: Boolean,
    onPick: (MealPresetWithFoods) -> Unit,
    onDelete: (Long) -> Unit,
    onSaveCurrent: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("내 식사") },
        text = {
            Column {
                if (presets.isEmpty()) {
                    Text(
                        "저장된 내 식사가 없습니다. 끼니에 음식을 담은 뒤 아래 \"지금 조합 저장\"으로 만듭니다.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(presets, key = { it.preset.id }) { preset ->
                        Row(
                            Modifier.fillMaxWidth().testTag("meal_preset_row_${preset.preset.id}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { onPick(preset) }, modifier = Modifier.weight(1f)) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(preset.preset.name)
                                    Text(
                                        preset.foods.joinToString(" + ") { it.name },
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(onClick = { onDelete(preset.preset.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "내 식사 삭제")
                            }
                        }
                    }
                }
                if (canSaveCurrent) {
                    OutlinedButton(
                        onClick = onSaveCurrent,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) { Text("지금 조합 저장") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } }
    )
}


/**
 * 하루 섭취 목표.
 *
 * 목적(벌크업/유지/컷팅)과 활동량을 고르면 최근 체중으로 **권장값을 계산해 보여준다.**
 * 다만 목표 숫자를 자동으로 바꾸지는 않는다 — 체중을 잴 때마다 목표가 조용히 달라지면
 * 사용자가 자기 목표를 신뢰할 수 없다. "권장값 채우기"를 눌러야 칸에 들어간다.
 */
@Composable
private fun TargetDialog(
    target: DietTarget?,
    latestWeightKg: Double?,
    onSave: (DietTarget) -> Unit,
    onDismiss: () -> Unit
) {
    var kcal by remember { mutableStateOf(target?.kcal ?: 2550.0) }
    var protein by remember { mutableStateOf(target?.protein ?: 175.0) }
    var carbs by remember { mutableStateOf(target?.carbs ?: 260.0) }
    var fat by remember { mutableStateOf(target?.fat ?: 67.0) }
    var sugar by remember { mutableStateOf(target?.sugar ?: 60.0) }
    var goal by remember { mutableStateOf(target?.goal ?: GoalType.MAINTAIN) }
    var activity by remember { mutableStateOf(target?.activityLevel ?: ActivityLevel.MEDIUM) }

    val suggestion = latestWeightKg?.let { TargetCalculator.suggest(it, goal, activity) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("하루 섭취 목표") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("목적", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GoalType.entries.forEach { item ->
                        FilterChip(
                            selected = goal == item,
                            onClick = { goal = item },
                            label = { Text(item.label) }
                        )
                    }
                }

                Text("활동량", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ActivityLevel.entries.forEach { item ->
                        FilterChip(
                            selected = activity == item,
                            onClick = { activity = item },
                            label = { Text(item.label) }
                        )
                    }
                }

                if (suggestion == null) {
                    Text(
                        "권장값을 계산하려면 신체 탭에 체중을 한 번 기록해 주세요.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Text(
                        "체중 ${latestWeightKg}kg 기준 권장값 — " +
                            "${suggestion.kcal.toInt()}kcal · 단백질 ${suggestion.protein.toInt()}g · " +
                            "탄수 ${suggestion.carbs.toInt()}g · 지방 ${suggestion.fat.toInt()}g",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            kcal = suggestion.kcal
                            protein = suggestion.protein
                            carbs = suggestion.carbs
                            fat = suggestion.fat
                            sugar = suggestion.sugar
                        }
                    ) { Text("권장값으로 채우기") }
                }

                HorizontalDivider(Modifier.padding(vertical = 4.dp))

                LabeledNumberField("칼로리 (kcal)", kcal) { kcal = it }
                LabeledNumberField("단백질 (g)", protein) { protein = it }
                LabeledNumberField("탄수화물 (g)", carbs) { carbs = it }
                LabeledNumberField("지방 (g)", fat) { fat = it }
                LabeledNumberField("당 (g)", sugar) { sugar = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        DietTarget(
                            kcal = kcal, protein = protein, carbs = carbs, fat = fat, sugar = sugar,
                            goal = goal, activityLevel = activity
                        )
                    )
                }
            ) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

private val GoalType.label: String
    get() = when (this) {
        GoalType.BULK -> "벌크업"
        GoalType.MAINTAIN -> "유지"
        GoalType.CUT -> "컷팅"
    }

private val ActivityLevel.label: String
    get() = when (this) {
        ActivityLevel.LOW -> "적음"
        ActivityLevel.MEDIUM -> "보통"
        ActivityLevel.HIGH -> "많음"
    }
