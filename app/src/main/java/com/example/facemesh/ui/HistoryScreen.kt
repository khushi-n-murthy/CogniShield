package com.example.facemesh.ui

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
import com.example.facemesh.data.MockSessionRepository
import com.example.facemesh.data.SessionDataPoint
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.chart.composed.plus
import com.patrykandpatrick.vico.core.chart.line.LineChart
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
    val repository = remember { MockSessionRepository() }
    val dataPoints by repository.getWeeklyHistory().collectAsState(initial = emptyList())

    val chartEntryModelProducer = remember { ChartEntryModelProducer() }

    LaunchedEffect(dataPoints) {
        if (dataPoints.isNotEmpty()) {
            val allEntries = dataPoints.mapIndexed { index, point ->
                StressEntry(index, point.stressScore, point.isPeakRedline)
            }
            
            // Only create points where peak redline is true for the second series
            val peakEntries = dataPoints.mapIndexedNotNull { index, point ->
                if (point.isPeakRedline) StressEntry(index, point.stressScore, true) else null
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

            val peakPoint = LineComponent(
                color = android.graphics.Color.RED,
                thicknessDp = 6f,
                shape = Shapes.pillShape
            )

            val lineChart = lineChart(
                lines = listOf(
                    LineChart.LineSpec(
                        lineColor = android.graphics.Color.TRANSPARENT,
                        point = peakPoint,
                        pointSizeDp = 6f
                    )
                )
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
                            val day = (value.toInt() / 24) + 1
                            if (value.toInt() % 24 == 0) "Day $day" else ""
                        }
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(12.dp).background(Color.Red, shape = androidx.compose.foundation.shape.CircleShape))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Peak Redline Moments", color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Loading data...", color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}
