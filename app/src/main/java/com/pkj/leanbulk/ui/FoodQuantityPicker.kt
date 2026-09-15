package com.pkj.leanbulk.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.width
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
import com.pkj.leanbulk.ui.theme.ShaSpacing
import com.pkj.leanbulk.ui.theme.ShaTheme
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
    onDismiss: () -> Unit,
    /** 이 끼니에 이미 담긴 음식 이름. 창을 연 채로 여러 개 담을 때 뭘 담았는지 보이게 한다. */
    addedNames: List<String> = emptyList()
) {
    var selected by remember { mutableStateOf<FoodItem?>(null) }
    var tab by remember { mutableStateOf(FoodPickerTab.SEARCH) }
    var showDirectRegister by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<FoodItem?>(null) }
    val scope = rememberCoroutineScope()

    // 수량·등록·수정도 **같은 창 안에서 단계만 바꾼다.** 단계마다 다른 Dialog를 띄우면
    // 창이 닫혔다 다시 열리면서 매번 아래로 내려갔다 올라오는 애니메이션이 났다.
    val formState = remember(editing?.id, showDirectRegister) {
        if (editing != null || showDirectRegister) FoodFormState(editing) else null
    }
    var multiplier by remember(selected?.id) { mutableStateOf(1.0) }
    val mode = when {
        formState != null -> PickerMode.FORM
        selected != null -> PickerMode.QUANTITY
        else -> PickerMode.LIST
    }

    fun backToList() {
        selected = null
        editing = null
        showDirectRegister = false
    }

    ShaDialog(
        title = when (mode) {
            PickerMode.FORM -> if (editing != null) "음식 수정" else "음식 직접 등록"
            PickerMode.QUANTITY -> selected?.name.orEmpty()
            PickerMode.LIST -> "음식 추가"
        },
        // 목록이 아닌 단계에서 닫으면 창을 끄지 않고 목록으로 돌아간다 — 한 끼니에 여러 개를
        // 담는 중이라, 수량에서 한 번 취소했다고 처음부터 다시 열게 하면 안 된다.
        onDismiss = { if (mode == PickerMode.LIST) onDismiss() else backToList() },
        dismissText = if (mode == PickerMode.LIST) "닫기" else "취소",
        confirmText = when (mode) {
            PickerMode.FORM -> if (editing != null) "저장" else "등록"
            PickerMode.QUANTITY -> "추가"
            // 목록 위에 떠 있으면 음식 한 줄처럼 보인다. 늘 손 닿는 아래에 둔다(종목 추가 창과 같다).
            PickerMode.LIST -> "직접 등록"
        },
        confirmEnabled = formState?.name?.isNotBlank() ?: true,
        onConfirm = {
            when (mode) {
                PickerMode.FORM -> {
                    val form = formState ?: return@ShaDialog
                    val target = editing
                    if (target != null) {
                        onEditFood(form.applyTo(target))
                        editing = null
                    } else {
                        scope.launch {
                            val created = onAddNewFood(
                                form.name.trim(), form.categoryOrDefault, form.macros,
                                form.baseAmount, form.unit
                            )
                            showDirectRegister = false
                            selected = created // 등록하자마자 수량을 정해 바로 담을 수 있게 이어준다.
                        }
                    }
                }
                PickerMode.QUANTITY -> {
                    selected?.let { onConfirm(it, multiplier) }
                    selected = null // 목록으로 돌아가 다른 음식을 이어 담을 수 있게 한다.
                }
                PickerMode.LIST -> showDirectRegister = true
            }
        }
    ) {
        when (mode) {
            PickerMode.FORM -> formState?.let { FoodFormContent(it) }
            PickerMode.QUANTITY -> selected?.let {
                FoodQuantityContent(it, multiplier) { value -> multiplier = value }
            }
            PickerMode.LIST -> {
                SubTabBar(
                    tabs = FoodPickerTab.entries.map { it.label },
                    selected = tab.ordinal,
                    onSelect = { tab = FoodPickerTab.entries[it] },
                    modifier = Modifier.testTag("food_tab_bar")
                )
                if (addedNames.isNotEmpty()) {
                    Text(
                        "이 끼니에 담음 · " + addedNames.joinToString(", "),
                        style = ShaTheme.type.caption,
                        color = ShaTheme.colors.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = ShaSpacing.sm).testTag("food_added_names")
                    )
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
        }
    }
}

/** 음식 추가 창이 지금 보여주는 단계. */
private enum class PickerMode { LIST, QUANTITY, FORM }

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
        ShaDialog(
            title = "음식 ${picked.size}개를 지울까요?",
            onDismiss = { confirmDelete = false },
            confirmText = "지우기",
            onConfirm = {
                onDeleteFoods(picked.toList())
                picked.clear()
                selecting = false
                confirmDelete = false
            }
        ) {
            Text(
                "음식 목록에서만 없어집니다. 이미 기록한 끼니는 그대로 남습니다.",
                style = ShaTheme.type.body,
                color = ShaTheme.colors.bodyText
            )
        }
    }
    val colors = ShaTheme.colors
    val type = ShaTheme.type

    Column {
        // 검색칸과 "선택"을 한 줄에 둔다. 예전엔 검색 방식 칩·분류 칩·선택 삭제 칩이
        // 각자 한 줄씩 차지해서, 음식이 한 개도 보이기 전에 다섯 줄을 지나야 했다.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ShaSpacing.md),
            verticalAlignment = Alignment.Bottom
        ) {
            ShaTextField(
                label = "",
                value = query,
                onValueChange = { query = it },
                placeholder = "음식 검색",
                modifier = Modifier.weight(1f),
                fieldModifier = Modifier.testTag("food_search_query")
            )
            Text(
                text = if (selecting) "취소" else "선택",
                style = type.bodyStrong,
                color = if (selecting) colors.danger else colors.primary,
                modifier = Modifier
                    .clickable {
                        selecting = !selecting
                        if (!selecting) picked.clear()
                    }
                    .padding(horizontal = ShaSpacing.sm, vertical = 14.dp)
                    .testTag("food_select_mode")
            )
        }

        val categories = remember(foods) { foods.map { it.category }.distinct().sorted() }
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = ShaSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(ShaSpacing.sm)
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

        if (selecting && picked.isNotEmpty()) {
            Text(
                "${picked.size}개 지우기",
                style = type.bodyStrong,
                color = colors.danger,
                modifier = Modifier
                    .clickable { confirmDelete = true }
                    .padding(vertical = ShaSpacing.md)
                    .testTag("food_delete_selected")
            )
        }

        val filtered = foods.filter {
            (category == null || it.category == category) && it.name.contains(query, ignoreCase = true)
        }

        // 숫자 칸이 무슨 값인지 한 번만 적는다. 줄마다 "kcal"을 붙이면 자릿수가 어긋난다.
        if (filtered.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(bottom = ShaSpacing.xs)) {
                Box(Modifier.weight(1f))
                Text(
                    "kcal",
                    style = type.caption,
                    color = colors.muted,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(KCAL_COLUMN_WIDTH).padding(end = ShaSpacing.md)
                )
                Box(Modifier.width(ICON_COLUMN_WIDTH))
            }
        }

        LazyColumn(Modifier.heightIn(max = 320.dp)) {
            if (filtered.isEmpty()) {
                item {
                    Column(Modifier.padding(vertical = ShaSpacing.lg)) {
                        Text("찾는 음식이 없습니다.", style = type.body, color = colors.bodyText)
                        // AI 검색은 아직 백엔드가 없다(§9.5). 못 찾았을 때만 언급한다 —
                        // 늘 띄워두면 되지도 않는 기능이 화면 위쪽을 계속 차지한다.
                        Text(
                            "아래 \"직접 등록\"으로 만들 수 있습니다. AI 검색은 준비 중입니다.",
                            style = type.caption,
                            color = colors.muted,
                            modifier = Modifier
                                .padding(top = ShaSpacing.xs)
                                .testTag("food_ai_search_placeholder")
                        )
                    }
                }
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
        LazyColumn(Modifier.heightIn(max = 400.dp)) {
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
    val colors = ShaTheme.colors
    val type = ShaTheme.type

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
        }
        Column(
            Modifier.weight(1f).clickable(onClick = onPick).padding(vertical = ShaSpacing.md)
        ) {
            Text(
                food.name,
                style = type.bodyStrong,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            // 칼로리는 오른쪽으로 뺐으므로 여기서는 뺀다. 한 줄로 묶어 두 줄로 안 접히게 한다.
            Text(
                sideSummary(food),
                style = type.caption,
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // 이 목록에서 눈으로 고르는 기준은 칼로리다. **폭을 고정하고 오른쪽에 맞춘다** —
        // 안 그러면 17과 132의 오른쪽 끝이 어긋나서 세로로 훑을 때 비교가 안 된다.
        Text(
            food.macros.kcal?.roundToInt()?.toString() ?: "?",
            style = type.dataSmall,
            color = colors.bodyText,
            textAlign = TextAlign.End,
            modifier = Modifier.width(KCAL_COLUMN_WIDTH).padding(end = ShaSpacing.md)
        )
        if (!selecting) {
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(36.dp).testTag("food_favorite_${food.id}")
            ) {
                if (food.favorite) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = "즐겨찾기 해제",
                        tint = colors.primary,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        Icons.Outlined.StarBorder,
                        contentDescription = "즐겨찾기 추가",
                        tint = colors.muted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(36.dp).testTag("food_edit_${food.id}")
            ) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "음식 수정",
                    tint = colors.muted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** 이름 아래 한 줄. 기준량을 아는 음식은 그걸 앞에 세운다 — 100g인지 1개인지가 먼저 궁금하다. */
private fun sideSummary(food: FoodItem): String {
    val amount = food.baseAmount?.let { "${it.roundToInt()}${food.baseAmountUnit.label}" }
    val macros = "단백질 ${num(food.macros.protein)} · 탄수 ${num(food.macros.carbs)} · 지방 ${num(food.macros.fat)}"
    return listOfNotNull(amount, macros).joinToString(" · ")
}

private fun num(value: Double?): String = value?.roundToInt()?.let { "${it}g" } ?: "?"

/** 칼로리 칸과 오른쪽 아이콘 두 개의 폭. 머리글과 줄이 같은 값을 써야 세로로 맞는다. */
private val KCAL_COLUMN_WIDTH = 60.dp
private val ICON_COLUMN_WIDTH = 72.dp

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

    ShaDialog(
        title = food.name,
        onDismiss = onDismiss,
        confirmText = "추가",
        onConfirm = { onConfirm(multiplier) }
    ) {
        FoodQuantityContent(food, multiplier) { multiplier = it }
    }
}

/** 수량 단계의 내용. 음식 추가 창 안에서도, 따로 떠 있는 수량 창에서도 같은 것을 쓴다. */
@Composable
private fun FoodQuantityContent(
    food: FoodItem,
    multiplier: Double,
    onMultiplierChange: (Double) -> Unit
) {
    val macros = remember(food.id, multiplier) { food.macros.scaledBy(multiplier) }
    val amount = remember(food.id, multiplier) { food.baseAmount?.times(multiplier) }
    Column {
        NumberStepper(
            description = "배수",
            value = multiplier,
            step = 0.5,
            modifier = Modifier.fillMaxWidth()
        ) { onMultiplierChange(it.coerceAtLeast(0.5)) }

        // 담을 양이 이 창의 결론이므로 값 단계로 크게 적는다.
        amount?.let {
            Text(
                "${it.roundToInt()}${food.baseAmountUnit.label}",
                style = ShaTheme.type.data,
                color = ShaTheme.colors.ink,
                modifier = Modifier.padding(top = ShaSpacing.lg).testTag("food_quantity_gram")
            )
        }
        Text(
            formatMacroSummary(macros),
            style = ShaTheme.type.caption,
            color = ShaTheme.colors.muted,
            modifier = Modifier.padding(top = ShaSpacing.xs).testTag("food_quantity_macros")
        )
    }
}

/**
 * 음식 하나를 적는 폼의 상태. **직접 등록과 수정이 같은 것을 쓴다** — 적는 항목이 똑같은데
 * 화면을 둘로 두면 한쪽만 고치는 실수가 난다. [initial]이 있으면 그 값으로 채우고 수정이 된다.
 *
 * 상태를 바깥(음식 추가 창)이 들고 있어야 확인 버튼이 폼 값을 읽을 수 있다 — 버튼은
 * 창 껍데기에 있고 폼은 창 안쪽에 있기 때문이다.
 */
private class FoodFormState(initial: FoodItem?) {
    var name by mutableStateOf(initial?.name ?: "")
    // 비워두면 "기타"로 저장한다. 미리 채워두면 지우고 쓰는 사람이 매번 지워야 한다.
    var category by mutableStateOf(initial?.category ?: "")
    var kcal by mutableStateOf(initial?.macros?.kcal)
    var protein by mutableStateOf(initial?.macros?.protein)
    var carbs by mutableStateOf(initial?.macros?.carbs)
    var fat by mutableStateOf(initial?.macros?.fat)
    var sugar by mutableStateOf(initial?.macros?.sugar)
    var baseAmount by mutableStateOf(initial?.baseAmount)
    var unit by mutableStateOf(initial?.baseAmountUnit ?: AmountUnit.G)

    val categoryOrDefault: String get() = category.ifBlank { "기타" }
    val macros: Macros get() = Macros(kcal, protein, carbs, fat, sugar)

    fun applyTo(food: FoodItem): FoodItem = food.copy(
        name = name.trim(),
        category = categoryOrDefault,
        macros = macros,
        baseAmount = baseAmount,
        baseAmountUnit = unit
    )
}

@Composable
private fun FoodFormContent(form: FoodFormState) {
        Column(
            Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ShaSpacing.md)
        ) {
                ShaTextField(
                    label = "음식 이름",
                    value = form.name,
                    onValueChange = { form.name = it },
                    fieldModifier = Modifier.testTag("direct_register_name")
                )
                ShaTextField(
                    label = "분류",
                    value = form.category,
                    onValueChange = { form.category = it },
                    placeholder = "기타"
                )
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NullableNumberField("칼로리", form.kcal) { form.kcal = it }
                    NullableNumberField("단백질", form.protein) { form.protein = it }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NullableNumberField("탄수", form.carbs) { form.carbs = it }
                    NullableNumberField("지방", form.fat) { form.fat = it }
                    NullableNumberField("당", form.sugar) { form.sugar = it }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NullableNumberField(
                        label = "기준량",
                        value = form.baseAmount,
                        modifier = Modifier.weight(1f),
                        onValueChange = { form.baseAmount = it }
                    )
                    AmountUnit.entries.forEach { u ->
                        FilterChip(
                            selected = form.unit == u,
                            onClick = { form.unit = u },
                            label = { Text(u.label) },
                            modifier = Modifier.testTag("unit_${u.name}")
                        )
                    }
                }
                Text(
                    "위 영양정보가 얼마를 기준으로 한 값인지. 모르면 비워두세요 — 그때는 배수(×1, ×2)로만 담깁니다.",
                    style = ShaTheme.type.caption,
                    color = ShaTheme.colors.muted
                )
        }
}

/** 모르는 값은 0이 아니라 `?`로 적는다 — 0kcal이라고 적어두면 사실이 아닌 값이 기록으로 남는다. */
private fun formatMacroSummary(m: Macros): String {
    fun v(value: Double?) = value?.roundToInt()?.toString() ?: "?"
    return "${v(m.kcal)}kcal · 단백질 ${v(m.protein)}g · 탄수 ${v(m.carbs)}g · 지방 ${v(m.fat)}g"
}
