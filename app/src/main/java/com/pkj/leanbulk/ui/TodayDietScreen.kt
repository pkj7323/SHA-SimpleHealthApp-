package com.pkj.leanbulk.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pkj.leanbulk.data.DietTarget
import com.pkj.leanbulk.data.DietTemplateWithMeals
import com.pkj.leanbulk.data.FoodItem
import com.pkj.leanbulk.data.Macros
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun TodayDietScreen(vm: MainViewModel) {
    val date by vm.dietDate.collectAsState()
    val templateName by vm.dietTemplateName.collectAsState()
    val draft by vm.dietDraft.collectAsState()
    val templates by vm.dietTemplates.collectAsState()
    val foods by vm.foods.collectAsState()
    val target by vm.target.collectAsState()
    val savedAt by vm.savedAt.collectAsState()

    var showTemplatePicker by remember { mutableStateOf(false) }
    var showAddMeal by remember { mutableStateOf(false) }
    var showTargetDialog by remember { mutableStateOf(false) }
    var showSaveTemplate by remember { mutableStateOf(false) }
    var foodPickerFor by remember { mutableStateOf<Int?>(null) }
    var pendingTemplate by remember { mutableStateOf<DietTemplateWithMeals?>(null) }

    val totals = remember(draft) { draft.totals() }

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

                // 식단표 이름은 길 수 있으므로 한 줄 전체를 쓰고 넘치면 … 으로 자른다.
                OutlinedButton(
                    onClick = { showTemplatePicker = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text(
                        text = if (templateName.isBlank()) "식단표 선택" else templateName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = { showAddMeal = true }, modifier = Modifier.weight(1f)) {
                        Text("끼니 추가", maxLines = 1)
                    }
                    OutlinedButton(onClick = { showSaveTemplate = true }, modifier = Modifier.weight(1f)) {
                        Text("식단표로 저장", maxLines = 1)
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 12.dp))

                TotalsSection(totals, target)
            }
        }

        if (draft.isEmpty()) {
            item { Text("식단표를 불러오거나 끼니를 추가하세요.") }
        } else {
            itemsIndexed(draft) { index, meal ->
                MealCard(
                    meal = meal,
                    onChange = { transform -> vm.updateMeal(index, transform) },
                    onRemove = { vm.removeMeal(index) },
                    onPickFood = { foodPickerFor = index },
                    onAcceptEstimate = { vm.acceptEstimate(index) }
                )
            }
        }
    }

    if (showTemplatePicker) {
        DietTemplatePickerDialog(
            templates = templates,
            // 이미 짜둔 끼니가 있으면 통째로 갈아엎는 것이므로 한 번 확인한다.
            onPick = {
                if (draft.isEmpty()) vm.loadDietTemplate(it) else pendingTemplate = it
                showTemplatePicker = false
            },
            onAdd = vm::addDietTemplate,
            onRename = vm::renameDietTemplate,
            onDelete = vm::deleteDietTemplate,
            onOverwrite = { vm.overwriteTemplate(it.template.id, it.template.name) },
            canOverwrite = draft.isNotEmpty(),
            onDismiss = { showTemplatePicker = false }
        )
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

    if (showSaveTemplate) {
        TextInputDialog(
            title = "식단표로 저장",
            label = "식단표 이름",
            initial = templateName,
            confirmText = "저장",
            onConfirm = { vm.saveCurrentAsTemplate(it); showSaveTemplate = false },
            onDismiss = { showSaveTemplate = false }
        )
    }

    if (showTargetDialog) {
        TargetDialog(
            target = target,
            onSave = { k, p, c, f, s -> vm.saveTarget(k, p, c, f, s); showTargetDialog = false },
            onDismiss = { showTargetDialog = false }
        )
    }

    pendingTemplate?.let { template ->
        AlertDialog(
            onDismissRequest = { pendingTemplate = null },
            title = { Text("지금 끼니를 갈아엎을까요?") },
            text = { Text("$date 의 끼니 ${draft.size}개가 '${template.template.name}' 내용으로 바뀝니다.") },
            confirmButton = {
                TextButton(onClick = { vm.loadDietTemplate(template); pendingTemplate = null }) { Text("불러오기") }
            },
            dismissButton = { TextButton(onClick = { pendingTemplate = null }) { Text("취소") } }
        )
    }

    foodPickerFor?.let { index ->
        FoodPickerDialog(
            foods = foods,
            onPick = { vm.applyFood(index, it); foodPickerFor = null },
            onDelete = vm::deleteFood,
            onDismiss = { foodPickerFor = null }
        )
    }

}

@Composable
private fun TotalsSection(totals: DietTotals, target: DietTarget?) {
    Text("오늘 섭취 (체크한 끼니 기준)", style = MaterialTheme.typography.titleMedium)

    MacroBar("칼로리", totals.entered.kcal, totals.estimatedExtra?.kcal, target?.kcal, "kcal")
    MacroBar("단백질", totals.entered.protein, totals.estimatedExtra?.protein, target?.protein, "g")
    MacroBar("탄수화물", totals.entered.carbs, totals.estimatedExtra?.carbs, target?.carbs, "g")
    MacroBar("지방", totals.entered.fat, totals.estimatedExtra?.fat, target?.fat, "g")
    MacroBar("당", totals.entered.sugar, totals.estimatedExtra?.sugar, target?.sugar, "g")

    if (totals.unknownCount > 0) {
        Text(
            "· 영양정보 미입력 ${totals.unknownCount}끼니" +
                if (totals.estimatedExtra != null) " — 괄호 안은 메뉴 이름으로 뽑은 어림값(참고용)" else "",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun MacroBar(label: String, value: Double?, estimate: Double?, target: Double?, unit: String) {
    val actual = value ?: 0.0
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            val estText = if (estimate != null && estimate > 0) " (+약 ${estimate.roundToInt()})" else ""
            val targetText = target?.let { " / ${it.roundToInt()}" } ?: ""
            Text("${actual.roundToInt()}$estText$targetText $unit")
        }
        if (target != null && target > 0) {
            LinearProgressIndicator(
                progress = { (actual / target).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MealCard(
    meal: DraftMeal,
    onChange: ((DraftMeal) -> DraftMeal) -> Unit,
    onRemove: () -> Unit,
    onPickFood: () -> Unit,
    onAcceptEstimate: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = meal.eaten, onCheckedChange = { c -> onChange { it.copy(eaten = c) } })
                OutlinedTextField(
                    value = meal.slotName,
                    onValueChange = { v -> onChange { it.copy(slotName = v) } },
                    label = { Text("끼니") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = "끼니 삭제")
                }
            }

            OutlinedTextField(
                value = meal.menuText,
                onValueChange = { v -> onChange { it.copy(menuText = v) } },
                label = { Text("메뉴") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )

            OutlinedButton(
                onClick = onPickFood,
                modifier = Modifier.padding(top = 8.dp)
            ) { Text("내 음식에서 불러오기") }

            Text(
                "영양정보 (모르면 비워두세요)",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )
            Row(
                Modifier.fillMaxWidth().horizontalScrollRow(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NullableNumberField("칼로리", meal.macros.kcal) { v -> onChange { it.copy(macros = it.macros.copy(kcal = v)) } }
                NullableNumberField("단백질", meal.macros.protein) { v -> onChange { it.copy(macros = it.macros.copy(protein = v)) } }
                NullableNumberField("탄수", meal.macros.carbs) { v -> onChange { it.copy(macros = it.macros.copy(carbs = v)) } }
                NullableNumberField("지방", meal.macros.fat) { v -> onChange { it.copy(macros = it.macros.copy(fat = v)) } }
                NullableNumberField("당", meal.macros.sugar) { v -> onChange { it.copy(macros = it.macros.copy(sugar = v)) } }
            }

            // 글자 하나 칠 때마다 추정 테이블을 다시 훑지 않도록 메뉴/매크로가 바뀔 때만 계산한다.
            val estimate = remember(meal.menuText, meal.macros) { meal.estimate }
            estimate?.let { est ->
                Column(Modifier.padding(top = 8.dp)) {
                    Text(
                        "어림값 (참고용): ${formatMacros(est)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = onAcceptEstimate) { Text("이 값으로 채우기") }
                }
            }
        }
    }
}

private fun formatMacros(m: Macros): String =
    "약 ${m.kcal?.roundToInt() ?: 0}kcal · 단백질 ${m.protein?.roundToInt() ?: 0}g · " +
        "탄수 ${m.carbs?.roundToInt() ?: 0}g · 지방 ${m.fat?.roundToInt() ?: 0}g · 당 ${m.sugar?.roundToInt() ?: 0}g"

/** 가로로 긴 입력 줄이 좁은 화면에서 잘리지 않도록 스크롤 가능하게 만든다. */
@Composable
private fun Modifier.horizontalScrollRow(): Modifier =
    this.then(Modifier.horizontalScroll(rememberScrollState()))

// ---------- 다이얼로그 ----------

@Composable
private fun DietTemplatePickerDialog(
    templates: List<DietTemplateWithMeals>,
    onPick: (DietTemplateWithMeals) -> Unit,
    onAdd: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onOverwrite: (DietTemplateWithMeals) -> Unit,
    canOverwrite: Boolean,
    onDismiss: () -> Unit
) {
    var editing by remember { mutableStateOf<Pair<Long, String>?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("식단표 선택") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                templates.forEach { item ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onPick(item) }, modifier = Modifier.weight(1f)) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(item.template.name)
                                Text("${item.meals.size}끼니", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (canOverwrite) {
                            TextButton(onClick = { onOverwrite(item); onDismiss() }) { Text("덮어쓰기") }
                        }
                        IconButton(onClick = { editing = item.template.id to item.template.name }) {
                            Icon(Icons.Filled.Edit, contentDescription = "이름 수정")
                        }
                        IconButton(onClick = { onDelete(item.template.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "식단표 삭제")
                        }
                    }
                }
                OutlinedButton(
                    onClick = { editing = 0L to "" },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("빈 식단표 추가")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } }
    )

    editing?.let { (id, currentName) ->
        TextInputDialog(
            title = if (id == 0L) "식단표 추가" else "식단표 이름 수정",
            label = "식단표 이름",
            initial = currentName,
            confirmText = if (id == 0L) "추가" else "저장",
            onConfirm = { name ->
                if (id == 0L) onAdd(name) else onRename(id, name)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
}

@Composable
private fun FoodPickerDialog(
    foods: List<FoodItem>,
    onPick: (FoodItem) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = foods.filter { it.name.contains(query, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("내 음식 목록") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("음식 검색") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (foods.isEmpty()) {
                    Text(
                        "아직 저장된 음식이 없습니다. 영양정보를 채워서 기록하면 여기에 쌓입니다.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                LazyColumn(Modifier.heightIn(max = 320.dp).padding(top = 8.dp)) {
                    items(filtered, key = { it.id }) { food ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { onPick(food) }, modifier = Modifier.weight(1f)) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(food.name)
                                    Text(formatMacros(food.macros), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            IconButton(onClick = { onDelete(food.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "음식 삭제")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } }
    )
}

@Composable
private fun TargetDialog(
    target: DietTarget?,
    onSave: (Double, Double, Double, Double, Double) -> Unit,
    onDismiss: () -> Unit
) {
    var kcal by remember { mutableStateOf(target?.kcal ?: 2550.0) }
    var protein by remember { mutableStateOf(target?.protein ?: 175.0) }
    var carbs by remember { mutableStateOf(target?.carbs ?: 260.0) }
    var fat by remember { mutableStateOf(target?.fat ?: 67.0) }
    var sugar by remember { mutableStateOf(target?.sugar ?: 60.0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("하루 섭취 목표") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledNumberField("칼로리 (kcal)", kcal) { kcal = it }
                LabeledNumberField("단백질 (g)", protein) { protein = it }
                LabeledNumberField("탄수화물 (g)", carbs) { carbs = it }
                LabeledNumberField("지방 (g)", fat) { fat = it }
                LabeledNumberField("당 (g)", sugar) { sugar = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(kcal, protein, carbs, fat, sugar) }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

@Composable
private fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    confirmText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}
