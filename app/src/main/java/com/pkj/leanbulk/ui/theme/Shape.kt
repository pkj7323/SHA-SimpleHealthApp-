package com.pkj.leanbulk.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val ShaShapes = Shapes(
    small = RoundedCornerShape(4.dp),        // 뱃지, 세트 번호 칩
    medium = RoundedCornerShape(6.dp),       // 인풋
    large = RoundedCornerShape(14.dp),       // 카드, 주요 버튼, 점선 추가 버튼
    extraLarge = RoundedCornerShape(16.dp)   // 스탯 타일, 큰 카드
)
