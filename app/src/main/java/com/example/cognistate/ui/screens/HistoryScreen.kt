package com.example.cognistate.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cognistate.data.entities.CogniEvent
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.core.component.shape.LineComponent
import com.patrykandpatrick.vico.core.component.shape.Shapes
import com.patrykandpatrick.vico.core.entry.ChartEntry
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer

class StressEntry(
    val index: Int,
    val score: Float,
    val isPeak: Boolean = false
) : ChartEntry {
    override val x: Float = index.toFloat()
    override val y: Float = score
    override fun withY(y: Float) = StressEntry(index, y, isPeak)
}

@Composable
fun HistoryScreen() {
    // For demo purposes, generate some mock data if DB is empty
    val dataPoints = remember {
        List(24) { i ->
            CogniEvent(
                id = i,
                timestamp = System.currentTimeMillis() - (24 - i) * 3600000L,
                stressScore = (0.2f + (i % 5) * 0.15f).coerceIn(0f, 1f),
                stateLabel = if (i % 7 == 0) "REDLINING" else "FLOW",
                edaRaw = 0f, hrvRaw = 0f, gazeScore = 0f
            )
        }
    }

    val chartEntryModelProducer = remember { ChartEntryModelProducer() }

    LaunchedEffect(dataPoints) {
        if (dataPoints.isNotEmpty()) {
            val allEntries = dataPoints.mapIndexed { index, point ->
                StressEntry(index, point.stressScore, point.stateLabel == "REDLINING")
            }
            
            // Only create points where peak redline is true for the second series
            val peakEntries = dataPoints.mapIndexedNotNull { index, point ->
                if (point.stateLabel == "REDLINING") StressEntry(index, point.stressScore, true) else null
            }
            
            chartEntryModelProducer.setEntries(allEntries, peakEntries)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            text = "7-Day Stress History",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        if (dataPoints.isNotEmpty()) {
            val primaryColor = MaterialTheme.colorScheme.primary

            val defaultColumn = LineComponent(
                color = primaryColor.copy(alpha = 0.6f).toArgb(),
                thicknessDp = 2f,
                shape = Shapes.roundedCornerShape(topLeftPercent = 50, topRightPercent = 50)
            )

            val columnChart = columnChart(
                columns = listOf(defaultColumn)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                Chart(
                    chart = columnChart,
                    chartModelProducer = chartEntryModelProducer,
                    startAxis = rememberStartAxis(
                        valueFormatter = { value, _ -> "%.1f".format(value) }
                    ),
                    bottomAxis = rememberBottomAxis(
                        valueFormatter = { value, _ -> 
                            val hour = value.toInt() % 24
                            if (hour % 6 == 0) "${hour}h" else ""
                        }
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(12.dp).background(Color.Red, shape = androidx.compose.foundation.shape.CircleShape))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Peak Redline Moments (AI Detected)", color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Privacy: All data stored locally on NPU",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }
    }
}
