package com.pkj.leanbulk.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
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
import com.pkj.leanbulk.data.ActivityLevel
import com.pkj.leanbulk.data.DietTarget
import com.pkj.leanbulk.data.GoalType
import com.pkj.leanbulk.data.TargetCalculator
import com.pkj.leanbulk.data.DietTemplateWithMeals
import com.pkj.leanbulk.data.FoodItem
import com.pkj.leanbulk.data.MacroSource
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
    val latestWeight by vm.latestWeightKg.collectAsState()

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
                    onAcceptEstimate = { vm.acceptEstimate(index) },
                    onStartFoodBased = { vm.startFoodBasedMeal(index) },
                    onAddFood = { foodPickerFor = index },
                    onRemoveFood = { foodIndex -> vm.removeFoodFromMeal(index, foodIndex) },
                    onMoveFood = { from, to -> vm.moveFoodInMeal(index, from, to) }
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
            latestWeightKg = latestWeight,
            onSave = { vm.saveTarget(it); showTargetDialog = false },
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

    // 어느 다이얼로그를 열지는 그 끼니가 새 방식인지로 갈린다 — 끼니 하나가 두 방식을
    // 동시에 쓰지 않으므로(§3, DIET-REDESIGN.md) 상태 변수 하나로 충분하다.
    foodPickerFor?.let { index ->
        if (draft.getOrNull(index)?.isFoodBased == true) {
            FoodQuantityPickerDialog(
                foods = foods,
                onConfirm = { food, multiplier -> vm.addFoodToMeal(index, food, multiplier) },
                onHide = vm::hideFood,
                onDismiss = { foodPickerFor = null }
            )
        } else {
            FoodPickerDialog(
                foods = foods,
                onPick = { vm.applyFood(index, it); foodPickerFor = null },
                onHide = vm::hideFood,
                onDismiss = { foodPickerFor = null }
            )
        }
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
    onAcceptEstimate: () -> Unit,
    onStartFoodBased: () -> Unit,
    onAddFood: () -> Unit,
    onRemoveFood: (Int) -> Unit,
    onMoveFood: (Int, Int) -> Unit
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

            if (meal.isFoodBased) {
                FoodBasedMealBody(meal, onAddFood, onRemoveFood, onMoveFood)
            } else {
                LegacyMealBody(meal, onChange, onPickFood, onAcceptEstimate, onStartFoodBased)
            }
        }
    }
}

/**
 * 음식 줄로 구성된 끼니. 매크로는 항상 [DraftMeal.recomputeFromFoods]로 미리 계산돼 있어서
 * 직접 입력 칸도, 어림값 안내도 필요 없다 — 카탈로그 값 × 배수라 "모름"이 나올 일이 없다.
 */
@Composable
private fun FoodBasedMealBody(
    meal: DraftMeal,
    onAddFood: () -> Unit,
    onRemoveFood: (Int) -> Unit,
    onMoveFood: (Int, Int) -> Unit
) {
    Column(Modifier.padding(top = 8.dp)) {
        meal.foods.forEachIndexed { index, food ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(food.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

        OutlinedButton(onClick = onAddFood, modifier = Modifier.padding(top = 4.dp)) { Text("음식 추가") }

        if (meal.foods.isNotEmpty()) {
            Text(
                "합계 ${formatMacros(meal.macros)}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/** 자유 텍스트로 적는 옛 방식. DIET-REDESIGN.md §3 — 새 구조로 자동 변환하지 않는다. */
@Composable
private fun LegacyMealBody(
    meal: DraftMeal,
    onChange: ((DraftMeal) -> DraftMeal) -> Unit,
    onPickFood: () -> Unit,
    onAcceptEstimate: () -> Unit,
    onStartFoodBased: () -> Unit
) {
    OutlinedTextField(
        value = meal.menuText,
        onValueChange = { v -> onChange { it.copy(menuText = v) } },
        label = { Text("메뉴") },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    )

    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(onClick = onPickFood) { Text("음식 목록에서 고르기") }
        // 아직 아무것도 안 적은 끼니에서만 보여준다 — 이미 적은 걸 새 방식으로
        // 바꿔주는 자동 변환은 없다(직접 다시 입력해야 한다).
        if (meal.menuText.isBlank() && meal.macros.isEmpty) {
            OutlinedButton(onClick = onStartFoodBased) { Text("음식으로 채우기") }
        }
    }

    // 저장된 뒤에도 이 숫자가 어디서 왔는지 보이게 한다. 직접 잰 값과 때려맞춘 값이
    // 화면에서 같아 보이면, 사용자도 나중에 자기 기록을 못 믿는다.
    Text(
        when (meal.macroSource) {
            MacroSource.ESTIMATED -> "영양정보 ≈ 메뉴 이름으로 어림한 값"
            MacroSource.CATALOG -> "영양정보 · 음식 목록에서 가져온 값"
            else -> "영양정보 (모르면 비워두세요)"
        },
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 8.dp)
    )
    Row(
        Modifier.fillMaxWidth().horizontalScrollRow(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        NullableNumberField("칼로리", meal.macros.kcal) { v -> onChange { it.editMacros { m -> m.copy(kcal = v) } } }
        NullableNumberField("단백질", meal.macros.protein) { v -> onChange { it.editMacros { m -> m.copy(protein = v) } } }
        NullableNumberField("탄수", meal.macros.carbs) { v -> onChange { it.editMacros { m -> m.copy(carbs = v) } } }
        NullableNumberField("지방", meal.macros.fat) { v -> onChange { it.editMacros { m -> m.copy(fat = v) } } }
        NullableNumberField("당", meal.macros.sugar) { v -> onChange { it.editMacros { m -> m.copy(sugar = v) } } }
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

private fun formatFoodQuantity(food: DraftMealFood): String {
    val qty = food.amountGram?.let { "${it.roundToInt()}g" } ?: "×${formatNumber(food.multiplier)}"
    return "$qty · ${formatMacros(food.macros)}"
}

private fun formatNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

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

/**
 * 음식 카탈로그에서 골라 끼니를 채운다.
 *
 * 예전에는 "한 번 입력한 음식"만 쌓여서 처음 쓸 때는 목록이 비어 있었다. 이제 시드로
 * 깔린 음식이 처음부터 있고, 쓰면서 쌓인 것과 한 목록에서 같이 검색된다.
 * 종목 선택 다이얼로그와 같은 구조다.
 */
@Composable
private fun FoodPickerDialog(
    foods: List<FoodItem>,
    onPick: (FoodItem) -> Unit,
    onHide: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }

    val categories = remember(foods) { foods.map { it.category }.distinct().sorted() }
    val filtered = foods.filter {
        (category == null || it.category == category) && it.name.contains(query, ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("음식 선택") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("음식 검색") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        FilterChip(
                            selected = category == null,
                            onClick = { category = null },
                            label = { Text("전체") }
                        )
                    }
                    items(categories) { item ->
                        FilterChip(
                            selected = category == item,
                            onClick = { category = if (category == item) null else item },
                            label = { Text(item) }
                        )
                    }
                }

                if (filtered.isEmpty()) {
                    Text(
                        "찾는 음식이 없습니다. 메뉴와 영양정보를 직접 적으면 다음부터 여기에 쌓입니다.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(filtered, key = { it.id }) { food ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { onPick(food) }, modifier = Modifier.weight(1f)) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(food.name)
                                    Text(formatMacros(food.macros), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            // 지우지 않고 숨긴다 — 시드 음식은 지워도 다음 갱신 때 slug로 되살아난다.
                            IconButton(onClick = { onHide(food.id) }) {
                                Icon(Icons.Filled.VisibilityOff, contentDescription = "목록에서 숨기기")
                            }
                        }
                    }
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

