package com.akond.ecoflowpv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Ekran telefonu (krok 1): pełny pipeline na realnych danych Streama (MQTT).
 * Docelowy „ładny" UI jest na zegarku; tu chodzi o dowód, że dane lecą end-to-end.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { PvScreen() } }
    }
}

@Composable
fun PvScreen() {
    val scope = rememberCoroutineScope()

    var reading by remember { mutableStateOf<StreamReading?>(null) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            busy = true
            status = "Łączę z Stream przez MQTT…"
            runCatching { ReadingProvider.get(force = true) }
                .onSuccess { reading = it; status = "" }
                .onFailure { status = "Błąd: ${it.message}" }
            busy = false
        }
    }

    LaunchedEffect(Unit) {
        if (BuildConfig.ECOFLOW_ACCESS_KEY.isEmpty()) {
            status = "Brak kluczy! Uzupełnij secrets.properties i przebuduj."
        } else refresh()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("EcoFlow Stream — produkcja PV", style = MaterialTheme.typography.titleMedium)

            val r = reading
            if (r != null) {
                Text(
                    "${r.total.roundToInt()} W",
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text("całkowita moc paneli", style = MaterialTheme.typography.bodySmall)

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    PanelCell("Panel 1", r.pv1)
                    PanelCell("Panel 2", r.pv2)
                }

                Column(modifier = Modifier.padding(top = 16.dp)) {
                    r.gridPower?.let { Detail("Do sieci", "${it.roundToInt()} W") }
                    r.gridVol?.let { Detail("Napięcie sieci", "${it.roundToInt()} V") }
                    r.invTemp?.let { Detail("Temp. inwertera", "$it °C") }
                    r.wifiRssi?.let { Detail("WiFi", "$it dBm") }
                    Detail("Aktualizacja", timeFmt(r.timestamp))
                }
            }

            if (busy) CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
            if (status.isNotEmpty()) {
                Text(status, color = MaterialTheme.colorScheme.error)
            }

            Button(onClick = { refresh() }, enabled = !busy, modifier = Modifier.padding(top = 8.dp)) {
                Text("Odśwież")
            }

            // Wszystkie pola zwrócone przez API (surowy strumień MQTT)
            reading?.takeIf { it.raw.isNotEmpty() }?.let { rd ->
                Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
                    Text(
                        "Wszystkie pola (${rd.raw.size})",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    rd.raw.forEach { (k, v) -> Detail(k, v) }
                }
            }
        }
    }
}

@Composable
private fun PanelCell(label: String, watts: Double) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Text("${watts.roundToInt()} W", fontSize = 28.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private fun timeFmt(ts: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ts))
