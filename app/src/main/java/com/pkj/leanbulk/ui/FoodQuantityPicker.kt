package com.pkj.leanbulk.ui

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.pkj.leanbulk.data.FoodItem
import com.pkj.leanbulk.data.Macros
import com.pkj.leanbulk.data.MealPresetWithFoods
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class FoodPickerTab(val label: String) {
    SEARCH("검색"), FAVORITE("즐겨찾기"), PRESET("식사세트")
}

/**
 * 음식을 고르고 배수로 양을 정한다(DIET-REDESIGN.md 2·9단계).
 *
 * 검색·즐겨찾기·식사세트 세 탭으로 나뉜다(인아웃 참고, §9.7). 검색 탭에서 음식을 고르면
 * [FoodQuantityDialog]로 넘어간다. 확정하면 검색 목록으로 **돌아온다** — 한 끼니에 음식을
 * 여러 개 담는 게 자연스러운 흐름이라, 매번 처음부터 다시 열 필요가 없다. 완전히 닫으려면
 * "닫기"를 누른다.
 *
 * 식사세트는 고르는 즉시 끼니에 통째로 담기고([onPickPreset]) 수량 단계를 거치지 않는다 —
 * 이미 배수가 정해진 조합이기 때문이다.
 */
@Composable
fun FoodQuantityPickerDialog(
    foods: List<FoodItem>,
    mealPresets: List<MealPresetWithFoods>,
    onConfirm: (FoodItem, Double) -> Unit,
    onPickPreset: (MealPresetWithFoods) -> Unit,
    onDeletePreset: (Long) -> Unit,
    onToggleFavorite: (FoodItem) -> Unit,
    onAddNewFood: suspend (name: String, category: String, macros: Macros, baseAmountGram: Double?) -> FoodItem,
    onHide: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf<FoodItem?>(null) }
    var tab by remember { mutableStateOf(FoodPickerTab.SEARCH) }
    var showDirectRegister by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
        DirectRegisterFoodDialog(
            onConfirm = { name, category, macros, baseAmountGram ->
                scope.launch {
                    val created = onAddNewFood(name, category, macros, baseAmountGram)
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
                        onHide = onHide,
                        onToggleFavorite = onToggleFavorite,
                        onDirectRegister = { showDirectRegister = true }
                    )
                    FoodPickerTab.FAVORITE -> FavoriteFoodTab(
                        foods = foods.filter { it.favorite },
                        onPick = { selected = it },
                        onToggleFavorite = onToggleFavorite
                    )
                    FoodPickerTab.PRESET -> MealPresetTab(
                        presets = mealPresets,
                        onPick = onPickPreset,
                        onDelete = onDeletePreset
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
    onHide: (Long) -> Unit,
    onToggleFavorite: (FoodItem) -> Unit,
    onDirectRegister: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
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

        val filtered = foods.filter {
            (category == null || it.category == category) && it.name.contains(query, ignoreCase = true)
        }

        LazyColumn(Modifier.heightIn(max = 320.dp)) {
            // 검색 결과 맨 위에 고정 — 찾는 음식이 카탈로그에 없을 때 바로 등록하러 간다(§9.6).
            item {
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
                    onPick = { onPick(food) },
                    onToggleFavorite = { onToggleFavorite(food) },
                    trailing = {
                        IconButton(onClick = { onHide(food.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "목록에서 숨기기")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun FavoriteFoodTab(
    foods: List<FoodItem>,
    onPick: (FoodItem) -> Unit,
    onToggleFavorite: (FoodItem) -> Unit
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
                FoodRow(food = food, onPick = { onPick(food) }, onToggleFavorite = { onToggleFavorite(food) })
            }
        }
    }
}

@Composable
private fun FoodRow(
    food: FoodItem,
    onPick: () -> Unit,
    onToggleFavorite: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().testTag("food_row_${food.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onToggleFavorite, modifier = Modifier.testTag("food_favorite_${food.id}")) {
            if (food.favorite) {
                Icon(Icons.Filled.Star, contentDescription = "즐겨찾기 해제")
            } else {
                Icon(Icons.Outlined.StarBorder, contentDescription = "즐겨찾기 추가")
            }
        }
        TextButton(onClick = onPick, modifier = Modifier.weight(1f)) {
            Column(Modifier.fillMaxWidth()) {
                Text(food.name)
                Text(formatMacroSummary(food.macros), style = MaterialTheme.typography.bodySmall)
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun MealPresetTab(
    presets: List<MealPresetWithFoods>,
    onPick: (MealPresetWithFoods) -> Unit,
    onDelete: (Long) -> Unit
) {
    Column(Modifier.padding(top = 8.dp)) {
        if (presets.isEmpty()) {
            Text(
                "저장된 내 식사가 없습니다. 끼니에 음식을 담은 뒤 \"이 조합을 세트로 저장\"으로 만들 수 있습니다.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        LazyColumn(Modifier.heightIn(max = 360.dp)) {
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
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    IconButton(onClick = { onDelete(preset.preset.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "내 식사 삭제")
                    }
                }
            }
        }
    }
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
    val amountGram = remember(multiplier) { food.baseAmountGram?.times(multiplier) }

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

                amountGram?.let {
                    Text(
                        "${it.roundToInt()}g",
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

/** 검색해도 없는 음식을 새로 카탈로그에 만든다(§9.6, 종목의 "직접 추가"와 같은 자리). */
@Composable
private fun DirectRegisterFoodDialog(
    onConfirm: (name: String, category: String, macros: Macros, baseAmountGram: Double?) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("기타") }
    var kcal by remember { mutableStateOf<Double?>(null) }
    var protein by remember { mutableStateOf<Double?>(null) }
    var carbs by remember { mutableStateOf<Double?>(null) }
    var fat by remember { mutableStateOf<Double?>(null) }
    var sugar by remember { mutableStateOf<Double?>(null) }
    var baseAmountGram by remember { mutableStateOf<Double?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("음식 직접 등록") },
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
                NullableNumberField("기준량(g, 아는 경우만)", baseAmountGram) { baseAmountGram = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(name.trim(), category.ifBlank { "기타" }, Macros(kcal, protein, carbs, fat, sugar), baseAmountGram)
                },
                enabled = name.isNotBlank()
            ) { Text("등록") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

private fun formatMacroSummary(m: Macros): String =
    "${m.kcal?.roundToInt() ?: 0}kcal · 단백질 ${m.protein?.roundToInt() ?: 0}g · " +
        "탄수 ${m.carbs?.roundToInt() ?: 0}g · 지방 ${m.fat?.roundToInt() ?: 0}g"
