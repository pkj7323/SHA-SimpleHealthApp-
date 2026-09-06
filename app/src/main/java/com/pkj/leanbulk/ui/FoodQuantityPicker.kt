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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
        trailing?.invoke()
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
    // 비워두면 "기타"로 저장한다. 미리 채워두면 지우고 쓰는 사람이 매번 지워야 한다.
    var category by remember { mutableStateOf("") }
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
                NullableNumberField(
                    label = "기준량(g)",
                    value = baseAmountGram,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    onValueChange = { baseAmountGram = it }
                )
                Text(
                    "위 영양정보가 몇 g 기준인지. 모르면 비워두세요 — 그때는 배수(×1, ×2)로만 담깁니다.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
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

/** 모르는 값은 0이 아니라 `?`로 적는다 — 0kcal이라고 적어두면 사실이 아닌 값이 기록으로 남는다. */
private fun formatMacroSummary(m: Macros): String {
    fun v(value: Double?) = value?.roundToInt()?.toString() ?: "?"
    return "${v(m.kcal)}kcal · 단백질 ${v(m.protein)}g · 탄수 ${v(m.carbs)}g · 지방 ${v(m.fat)}g"
}
