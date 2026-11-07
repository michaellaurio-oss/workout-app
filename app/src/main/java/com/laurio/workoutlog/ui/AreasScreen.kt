package com.laurio.workoutlog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.laurio.workoutlog.data.AppData
import com.laurio.workoutlog.data.Area
import com.laurio.workoutlog.logic.ALSO_CREDIT
import com.laurio.workoutlog.logic.MAIN_CREDIT
import com.laurio.workoutlog.logic.formatCredits

/** How often each area should come up, and how much work in one day counts as doing it. */
@Composable
fun AreasScreen(data: AppData, vm: AppViewModel) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column {
                ScreenTitle("Areas")
                Text(
                    "Each movement you log gives ${formatCredits(MAIN_CREDIT)} credit to its main area and " +
                        "${formatCredits(ALSO_CREDIT)} to each area it also works. A day counts as working an area " +
                        "once it reaches that area's credits needed; then its clock starts over.",
                    style = MaterialTheme.typography.bodySmall, color = Muted,
                )
            }
        }
        items(data.areas, key = { it.id }) { a ->
            AreaSettingsCard(
                area = a,
                onTarget = { vm.setTarget(a.id, it) },
                onCredits = { vm.setCredits(a.id, it) },
            )
        }
    }
}

@Composable
private fun AreaSettingsCard(area: Area, onTarget: (Int) -> Unit, onCredits: (Double) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(area.label.uppercase(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Stepper(
                label = "Every",
                value = "${area.target} days",
                canDown = area.target > 1,
                canUp = area.target < 30,
                onDown = { onTarget(area.target - 1) },
                onUp = { onTarget(area.target + 1) },
            )
            Stepper(
                label = "Credits needed",
                value = formatCredits(area.credits),
                canDown = area.credits > Area.MIN_CREDITS,
                canUp = area.credits < Area.MAX_CREDITS,
                onDown = { onCredits(area.credits - 0.5) },
                onUp = { onCredits(area.credits + 0.5) },
            )
            Text(example(area.credits), style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, canDown: Boolean, canUp: Boolean, onDown: () -> Unit, onUp: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = onDown, enabled = canDown, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(48.dp)) {
            Text("−", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            value, Modifier.width(76.dp), textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
        )
        OutlinedButton(onClick = onUp, enabled = canUp, contentPadding = PaddingValues(0.dp), modifier = Modifier.width(48.dp)) {
            Text("+", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Plain-language meaning of a credits number, e.g. 2 -> "2 main movements, or 1 main + 2 also-works". */
private fun example(credits: Double): String {
    val mains = credits / MAIN_CREDIT
    val whole = Math.floor(mains).toInt()
    val alsos = Math.round((credits - whole * MAIN_CREDIT) / ALSO_CREDIT).toInt()
    val first = when {
        alsos == 0 -> "$whole main movement${if (whole == 1) "" else "s"}"
        whole == 0 -> if (alsos == 1) "1 movement that also works it" else "$alsos movements that also work it"
        else -> "$whole main + $alsos that also work${if (alsos == 1) "s" else ""} it"
    }
    val alt = if (whole >= 1 && alsos == 0) {
        val m = whole - 1
        " (or ${if (m > 0) "$m main + " else ""}2 that also work it)"
    } else ""
    return "That's $first in one day$alt."
}
