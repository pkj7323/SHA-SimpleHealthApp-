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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.pkj.leanbulk.data.FoodItem
import com.pkj.leanbulk.data.Macros
import kotlin.math.roundToInt

/**
 * 음식을 고르고 배수로 양을 정한다(DIET-REDESIGN.md 2단계).
 *
 * 검색해서 음식을 고르면 [FoodQuantityDialog]로 넘어간다. 확정하면 검색 목록으로
 * **돌아온다** — 한 끼니에 음식을 여러 개 담는 게 자연스러운 흐름이라, 매번 처음부터
 * 다시 열 필요가 없다. 완전히 닫으려면 "닫기"를 누른다.
 */
@Composable
fun FoodQuantityPickerDialog(
    foods: List<FoodItem>,
    onConfirm: (FoodItem, Double) -> Unit,
    onHide: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<FoodItem?>(null) }

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

    val categories = remember(foods) { foods.map { it.category }.distinct().sorted() }
    val filtered = foods.filter {
        (category == null || it.category == category) && it.name.contains(query, ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("음식 추가") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("음식 검색") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("food_search_query")
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
                    Text("찾는 음식이 없습니다.", style = MaterialTheme.typography.bodySmall)
                }
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(filtered, key = { it.id }) { food ->
                        Row(
                            Modifier.fillMaxWidth().testTag("food_row_${food.id}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { selected = food }, modifier = Modifier.weight(1f)) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(food.name)
                                    Text(formatMacroSummary(food.macros), style = MaterialTheme.typography.bodySmall)
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

private fun formatMacroSummary(m: Macros): String =
    "${m.kcal?.roundToInt() ?: 0}kcal · 단백질 ${m.protein?.roundToInt() ?: 0}g · " +
        "탄수 ${m.carbs?.roundToInt() ?: 0}g · 지방 ${m.fat?.roundToInt() ?: 0}g"
