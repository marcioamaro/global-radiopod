package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.RankingInfo

@Composable
fun RankingSourceHeader(info: RankingInfo, color: Color) {
    Column(Modifier.fillMaxWidth().padding(6.dp)) {
        Text("Top 20 • ${info.period}", color = color, fontSize = 10.sp)
        Text(info.note, color = color.copy(alpha = 0.8f), fontSize = 9.sp)
    }
}
