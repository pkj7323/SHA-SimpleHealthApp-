package com.pkj.leanbulk.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pkj.leanbulk.data.AmountUnit
import com.pkj.leanbulk.data.FoodItem
import com.pkj.leanbulk.data.Macros
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class FoodPickerTab(val label: String) {
    SEARCH("검색"), FAVORITE("즐겨찾기")
}

/**
 * 음식을 고르고 배수로 양을 정한다(DIET-REDESIGN.md 2·9단계).
 *
 * 검색·즐겨찾기 두 탭이다. 음식을 고르면 [FoodQuantityDialog]로 넘어가고, 확정하면 목록으로
 * **돌아온다** — 한 끼니에 음식을 여러 개 담는 게 자연스러운 흐름이라, 매번 처음부터 다시
 * 열 필요가 없다. 완전히 닫으려면 "닫기"를 누른다.
 *
 * 여러 음식 묶음("내 식사")은 여기 없다 — 그건 음식 하나가 아니라 조합이라, 끼니 카드의
 * "내 식사" 버튼에서 따로 담는다.
 */
@Composable
fun FoodQuantityPickerDialog(
    foods: List<FoodItem>,
    onConfirm: (FoodItem, Double) -> Unit,
    onToggleFavorite: (FoodItem) -> Unit,
    onEditFood: (FoodItem) -> Unit,
    onAddNewFood: suspend (
        name: String, category: String, macros: Macros, baseAmount: Double?, unit: AmountUnit
    ) -> FoodItem,
    onDeleteFoods: (List<Long>) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf<FoodItem?>(null) }
    var tab by remember { mutableStateOf(FoodPickerTab.SEARCH) }
    var showDirectRegister by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<FoodItem?>(null) }
    val scope = rememberCoroutineScope()

    editing?.let { food ->
        FoodFormDialog(
            title = "음식 수정",
            confirmText = "저장",
            initial = food,
            onConfirm = { name, category, macros, baseAmount, unit ->
                onEditFood(
                    food.copy(
                        name = name,
                        category = category,
                        macros = macros,
                        baseAmount = baseAmount,
                        baseAmountUnit = unit
                    )
                )
                editing = null
            },
            onDismiss = { editing = null }
        )
        return
    }

    selected?.let { food ->
        FoodQuantityDialog(
            food = food,
            onConfirm = { multiplier ->
                onConfirm(food, multiplier)
                selected = null // 검색 목록으로 돌아가 다른 음식을 이어 담을 수 있게 한다.
            },
            onDismiss = { selected = null }
        )
        return
    }

    if (showDirectRegister) {
        FoodFormDialog(
            title = "음식 직접 등록",
            confirmText = "등록",
            initial = null,
            onConfirm = { name, category, macros, baseAmount, unit ->
                scope.launch {
                    val created = onAddNewFood(name, category, macros, baseAmount, unit)
                    showDirectRegister = false
                    selected = created // 등록하자마자 수량을 정해 바로 담을 수 있게 이어준다.
                }
            },
            onDismiss = { showDirectRegister = false }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("음식 추가") },
        text = {
            Column {
                TabRow(selectedTabIndex = tab.ordinal) {
                    FoodPickerTab.entries.forEach { t ->
                        Tab(
                            selected = tab == t,
                            onClick = { tab = t },
                            text = { Text(t.label) },
                            modifier = Modifier.testTag("food_tab_${t.name}")
                        )
                    }
                }
                when (tab) {
                    FoodPickerTab.SEARCH -> SearchFoodTab(
                        foods = foods,
                        onPick = { selected = it },
                        onDeleteFoods = onDeleteFoods,
                        onToggleFavorite = onToggleFavorite,
                        onEdit = { editing = it },
                        onDirectRegister = { showDirectRegister = true }
                    )
                    FoodPickerTab.FAVORITE -> FavoriteFoodTab(
                        foods = foods.filter { it.favorite },
                        onPick = { selected = it },
                        onToggleFavorite = onToggleFavorite,
                        onEdit = { editing = it }
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } }
    )
}

@Composable
private fun SearchFoodTab(
    foods: List<FoodItem>,
    onPick: (FoodItem) -> Unit,
    onDeleteFoods: (List<Long>) -> Unit,
    onToggleFavorite: (FoodItem) -> Unit,
    onEdit: (FoodItem) -> Unit,
    onDirectRegister: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    // 삭제 버튼을 줄마다 띄워두면 음식을 고르다가 잘못 누르기 쉽다. 지울 때만 켜는 모드를 둔다.
    var selecting by remember { mutableStateOf(false) }
    val picked = remember { mutableStateListOf<Long>() }
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("음식 ${picked.size}개를 지울까요?") },
            text = { Text("음식 목록에서만 없어집니다. 이미 기록한 끼니는 그대로 남습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteFoods(picked.toList())
                    picked.clear()
                    selecting = false
                    confirmDelete = false
                }) { Text("지우기") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } }
        )
    }
    // "일반 검색"과 "AI 검색" 중 고른다. AI 검색은 아직 백엔드가 없어 안내만 뜬다(§9.5).
    var aiSearch by remember { mutableStateOf(false) }

    Column(Modifier.padding(top = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = !aiSearch, onClick = { aiSearch = false }, label = { Text("일반 검색") })
            FilterChip(
                selected = aiSearch,
                onClick = { aiSearch = true },
                label = { Text("AI 검색") },
                modifier = Modifier.testTag("food_ai_search_toggle")
            )
        }

        if (aiSearch) {
            Text(
                "AI 검색은 준비 중입니다.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 16.dp).testTag("food_ai_search_placeholder")
            )
            return@Column
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("음식 검색") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("food_search_query")
        )

        val categories = remember(foods) { foods.map { it.category }.distinct().sorted() }
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                FilterChip(selected = category == null, onClick = { category = null }, label = { Text("전체") })
            }
            items(categories) { item ->
                FilterChip(
                    selected = category == item,
                    onClick = { category = if (category == item) null else item },
                    label = { Text(item) }
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = selecting,
                onClick = {
                    selecting = !selecting
                    if (!selecting) picked.clear()
                },
                label = { Text(if (selecting) "선택 취소" else "선택 삭제") },
                modifier = Modifier.testTag("food_select_mode")
            )
            if (selecting && picked.isNotEmpty()) {
                TextButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.testTag("food_delete_selected")
                ) { Text("${picked.size}개 지우기") }
            }
        }

        val filtered = foods.filter {
            (category == null || it.category == category) && it.name.contains(query, ignoreCase = true)
        }

        LazyColumn(Modifier.heightIn(max = 320.dp)) {
            // 검색 결과 맨 위에 고정 — 찾는 음식이 카탈로그에 없을 때 바로 등록하러 간다(§9.6).
            if (!selecting) item {
                Row(
                    Modifier.fillMaxWidth().testTag("food_direct_register"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDirectRegister, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text("직접 등록", modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
            if (filtered.isEmpty()) {
                item { Text("찾는 음식이 없습니다.", style = MaterialTheme.typography.bodySmall) }
            }
            items(filtered, key = { it.id }) { food ->
                FoodRow(
                    food = food,
                    // 선택 모드에서는 줄을 눌러도 수량 화면으로 넘어가지 않는다 — 지우려던
                    // 손짓으로 음식이 담기면 안 된다.
                    onPick = { if (selecting) togglePick(picked, food.id) else onPick(food) },
                    onToggleFavorite = { onToggleFavorite(food) },
                    onEdit = { onEdit(food) },
                    selecting = selecting,
                    checked = food.id in picked,
                    onCheckedChange = { togglePick(picked, food.id) }
                )
            }
        }
    }
}

@Composable
private fun FavoriteFoodTab(
    foods: List<FoodItem>,
    onPick: (FoodItem) -> Unit,
    onToggleFavorite: (FoodItem) -> Unit,
    onEdit: (FoodItem) -> Unit
) {
    Column(Modifier.padding(top = 8.dp)) {
        if (foods.isEmpty()) {
            Text(
                "즐겨찾기한 음식이 없습니다. 검색 탭에서 별 아이콘을 눌러 추가하세요.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        LazyColumn(Modifier.heightIn(max = 360.dp)) {
            items(foods, key = { it.id }) { food ->
                FoodRow(
                    food = food,
                    onPick = { onPick(food) },
                    onToggleFavorite = { onToggleFavorite(food) },
                    onEdit = { onEdit(food) }
                )
            }
        }
    }
}

/**
 * 음식 한 줄.
 *
 * 이름 칸을 [TextButton] 대신 그냥 눌리는 [Row]로 만든 이유: 버튼은 높이가 정해져 있어서
 * 이름이 길어 두 줄로 넘어가면 아래가 잘렸다(옛 자유 텍스트 기록에서 넘어온 긴 이름들).
 */
@Composable
private fun FoodRow(
    food: FoodItem,
    onPick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    selecting: Boolean = false,
    checked: Boolean = false,
    onCheckedChange: () -> Unit = {}
) {
    Row(
        Modifier.fillMaxWidth().testTag("food_row_${food.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selecting) {
            Checkbox(
                checked = checked,
                onCheckedChange = { onCheckedChange() },
                modifier = Modifier.testTag("food_check_${food.id}")
            )
        } else {
            IconButton(onClick = onToggleFavorite, modifier = Modifier.testTag("food_favorite_${food.id}")) {
                if (food.favorite) {
                    Icon(Icons.Filled.Star, contentDescription = "즐겨찾기 해제")
                } else {
                    Icon(Icons.Outlined.StarBorder, contentDescription = "즐겨찾기 추가")
                }
            }
        }
        Column(
            Modifier.weight(1f).clickable(onClick = onPick).padding(vertical = 10.dp, horizontal = 4.dp)
        ) {
            Text(food.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                formatMacroSummary(food.macros),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!selecting) {
            IconButton(onClick = onEdit, modifier = Modifier.testTag("food_edit_${food.id}")) {
                Icon(Icons.Filled.Edit, contentDescription = "음식 수정")
            }
        }
    }
}

private fun togglePick(picked: MutableList<Long>, id: Long) {
    if (!picked.remove(id)) picked.add(id)
}

/**
 * 배수를 정하고 계산된 매크로·그램을 확인한다.
 *
 * **기준 그램을 모르는 음식은 그램을 아예 안 보여준다** — 배수로 계산해서 그램을
 * 지어내면 이 앱이 지키는 "모르면 모른다"는 원칙이 화면 단계에서부터 깨진다.
 */
@Composable
fun FoodQuantityDialog(
    food: FoodItem,
    initialMultiplier: Double = 1.0,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var multiplier by remember { mutableStateOf(initialMultiplier) }
    val macros = remember(multiplier) { food.macros.scaledBy(multiplier) }
    val amount = remember(multiplier) { food.baseAmount?.times(multiplier) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(food.name) },
        text = {
            Column {
                NumberStepper(
                    description = "배수",
                    value = multiplier,
                    step = 0.5,
                    modifier = Modifier.fillMaxWidth()
                ) { multiplier = it.coerceAtLeast(0.5) }

                amount?.let {
                    Text(
                        "${it.roundToInt()}${food.baseAmountUnit.label}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp).testTag("food_quantity_gram")
                    )
                }
                Text(
                    formatMacroSummary(macros),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp).testTag("food_quantity_macros")
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(multiplier) }) { Text("추가") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

/**
 * 음식 하나를 적는 폼. **직접 등록과 수정이 같은 화면을 쓴다** — 적는 항목이 똑같은데
 * 화면을 둘로 두면 한쪽만 고치는 실수가 난다. [initial]이 있으면 그 값으로 채우고 수정이 된다.
 */
@Composable
private fun FoodFormDialog(
    title: String,
    confirmText: String,
    initial: FoodItem?,
    onConfirm: (name: String, category: String, macros: Macros, baseAmount: Double?, unit: AmountUnit) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    // 비워두면 "기타"로 저장한다. 미리 채워두면 지우고 쓰는 사람이 매번 지워야 한다.
    var category by remember { mutableStateOf(initial?.category ?: "") }
    var kcal by remember { mutableStateOf(initial?.macros?.kcal) }
    var protein by remember { mutableStateOf(initial?.macros?.protein) }
    var carbs by remember { mutableStateOf(initial?.macros?.carbs) }
    var fat by remember { mutableStateOf(initial?.macros?.fat) }
    var sugar by remember { mutableStateOf(initial?.macros?.sugar) }
    var baseAmount by remember { mutableStateOf(initial?.baseAmount) }
    var unit by remember { mutableStateOf(initial?.baseAmountUnit ?: AmountUnit.G) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("음식 이름") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("direct_register_name")
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("분류") },
                    placeholder = { Text("기타") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NullableNumberField("칼로리", kcal) { kcal = it }
                    NullableNumberField("단백질", protein) { protein = it }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NullableNumberField("탄수", carbs) { carbs = it }
                    NullableNumberField("지방", fat) { fat = it }
                    NullableNumberField("당", sugar) { sugar = it }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NullableNumberField(
                        label = "기준량",
                        value = baseAmount,
                        modifier = Modifier.weight(1f),
                        onValueChange = { baseAmount = it }
                    )
                    AmountUnit.entries.forEach { u ->
                        FilterChip(
                            selected = unit == u,
                            onClick = { unit = u },
                            label = { Text(u.label) },
                            modifier = Modifier.testTag("unit_${u.name}")
                        )
                    }
                }
                Text(
                    "위 영양정보가 얼마를 기준으로 한 값인지. 모르면 비워두세요 — 그때는 배수(×1, ×2)로만 담깁니다.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        name.trim(),
                        category.ifBlank { "기타" },
                        Macros(kcal, protein, carbs, fat, sugar),
                        baseAmount,
                        unit
                    )
                },
                enabled = name.isNotBlank()
            ) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

/** 모르는 값은 0이 아니라 `?`로 적는다 — 0kcal이라고 적어두면 사실이 아닌 값이 기록으로 남는다. */
private fun formatMacroSummary(m: Macros): String {
    fun v(value: Double?) = value?.roundToInt()?.toString() ?: "?"
    return "${v(m.kcal)}kcal · 단백질 ${v(m.protein)}g · 탄수 ${v(m.carbs)}g · 지방 ${v(m.fat)}g"
}
