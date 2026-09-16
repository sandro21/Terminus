package com.terminus.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.terminus.shared.DepartureDto
import com.terminus.shared.StationDto
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

private val Ink = Color(0xFF172A3A)
private val Paper = Color(0xFFF7F4ED)
private val Accent = Color(0xFFF2B84B)
private val Muted = Color(0xFF60727D)

@Composable
fun TerminusApp(viewModel: AppViewModel, requestNotificationPermission: () -> Unit) {
    val selected by viewModel.selectedStation.collectAsState()
    val stations by viewModel.stations.collectAsState()
    val departures by viewModel.departures.collectAsState()
    val status by viewModel.status.collectAsState()

    MaterialTheme {
        Surface(color = Paper, modifier = Modifier.fillMaxSize()) {
            if (selected == null) {
                StationList(stations, viewModel::openStation)
            } else {
                StationDetail(
                    station = selected!!,
                    departures = departures,
                    status = status,
                    onBack = viewModel::closeStation,
                    onRefresh = viewModel::refresh,
                    onSaveAlert = { line, delay ->
                        requestNotificationPermission()
                        viewModel.saveAlert(line, delay)
                    },
                )
            }
        }
    }
}

@Composable
private fun StationList(stations: List<StationDto>, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(42.dp))
        Text("TERMINUS", color = Accent, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        Text("Atlanta rail", color = Ink, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text("Choose a station. Schedules stay available underground.", color = Muted, modifier = Modifier.padding(top = 6.dp, bottom = 20.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(stations, key = { it.id }) { station ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(station.id) },
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(station.name, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                station.lines.forEach { LineBadge(it) }
                            }
                        }
                        Text("›", color = Muted, fontSize = 28.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun StationDetail(
    station: StationDto,
    departures: List<DepartureDto>,
    status: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSaveAlert: (String, Int) -> Unit,
) {
    var showAlert by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(34.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Stations", color = Ink) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onRefresh) { Text("Refresh", color = Ink) }
        }
        Text(station.name, color = Ink, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            station.lines.forEach { LineBadge(it) }
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(status ?: "Local schedule ready", color = Muted, modifier = Modifier.weight(1f))
            Button(
                onClick = { showAlert = true },
                colors = ButtonDefaults.buttonColors(containerColor = Ink),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Set delay alert") }
        }
        if (departures.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                Text("No upcoming departures in the saved bundle.", color = Muted)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(departures, key = { "${it.tripId}-${it.departureSeconds}-${it.trainId}" }) { departure ->
                    DepartureRow(departure)
                }
            }
        }
    }
    if (showAlert) {
        AlertEditor(station, onDismiss = { showAlert = false }) { line, delay ->
            onSaveAlert(line, delay)
            showAlert = false
        }
    }
}

@Composable
private fun DepartureRow(departure: DepartureDto) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            LineBadge(departure.line)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(departure.destination, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                val detail = if (departure.realtime) "Live train ${departure.trainId.orEmpty()}" else "Saved schedule"
                Text(detail, color = Muted, fontSize = 13.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(departure.displayTime(), color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (departure.delaySeconds > 0) Text("+${departure.delaySeconds / 60} min", color = Color(0xFFB33A3A), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun AlertEditor(station: StationDto, onDismiss: () -> Unit, onSave: (String, Int) -> Unit) {
    var line by remember { mutableStateOf(station.lines.first()) }
    var delayMinutes by remember { mutableIntStateOf(2) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delay alert for ${station.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Line", color = Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    station.lines.forEach { option ->
                        OutlinedButton(onClick = { line = option }, enabled = line != option) { Text(option) }
                    }
                }
                Text("Notify when delayed at least", color = Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 5).forEach { minutes ->
                        OutlinedButton(onClick = { delayMinutes = minutes }, enabled = delayMinutes != minutes) { Text("$minutes min") }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(line, delayMinutes * 60) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun LineBadge(line: String) {
    val color = when (line.uppercase()) {
        "RED" -> Color(0xFFC83E4D)
        "GOLD" -> Color(0xFFD69E1A)
        "BLUE" -> Color(0xFF3178B8)
        "GREEN" -> Color(0xFF3A8D5D)
        else -> Muted
    }
    Text(
        line.take(1), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp,
        modifier = Modifier.background(color, RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 5.dp),
    )
}

private fun DepartureDto.displayTime(): String {
    waitingSeconds?.let { seconds ->
        if (seconds <= 30) return "Now"
        return "${ceil(seconds / 60.0).toInt()} min"
    }
    val zone = ZoneId.of("America/New_York")
    return LocalDate.parse(serviceDate).atStartOfDay(zone).plusSeconds(departureSeconds.toLong())
        .format(DateTimeFormatter.ofPattern("h:mm a"))
}
