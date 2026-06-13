package com.akond.ecoflowpv.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

private val Amber = Color(0xFFFFC107)

class WearMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { WearApp() } }
    }
}

private sealed interface UiState {
    data object Loading : UiState
    data class Data(val r: WatchReading) : UiState
    data class Error(val msg: String, val last: WatchReading?) : UiState
}

@Composable
fun WearApp() {
    val context = LocalContext.current
    var state by remember { mutableStateOf<UiState>(UiState.Loading) }
    var refreshKey by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        val listener = MessageClient.OnMessageReceivedListener { event ->
            if (event.path == WPaths.READING) {
                parseReading(String(event.data))
                    .onSuccess { ReadingStore.save(context, it); state = UiState.Data(it) }
                    .onFailure { e ->
                        val last = (state as? UiState.Data)?.r
                        state = UiState.Error(e.message ?: "błąd danych", last)
                    }
            }
        }
        val mc = Wearable.getMessageClient(context)
        mc.addListener(listener)
        onDispose { mc.removeListener(listener) }
    }

    LaunchedEffect(refreshKey) {
        val last = (state as? UiState.Data)?.r
        state = UiState.Loading
        val sent = runCatching {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            val phone = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
            ?: error("Nie znaleziono telefonu")
            Wearable.getMessageClient(context)
                .sendMessage(phone.id, WPaths.REQUEST, ByteArray(0)).await()
        }
        if (sent.isFailure) {
            state = UiState.Error(sent.exceptionOrNull()?.message ?: "błąd połączenia", last)
            return@LaunchedEffect
        }
        withTimeoutOrNull(15_000) { while (state is UiState.Loading) delay(100) }
            ?: run { if (state is UiState.Loading) state = UiState.Error("Brak odpowiedzi", last) }
    }

    Scaffold(timeText = { TimeText() }) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (val s = state) {
                is UiState.Loading -> CircularProgressIndicator(indicatorColor = Amber)
                is UiState.Data -> ReadingView(s.r, stale = false) { refreshKey++ }
                is UiState.Error ->
                    if (s.last != null) ReadingView(s.last, stale = true) { refreshKey++ }
                    else ErrorView(s.msg) { refreshKey++ }
            }
        }
    }
}

@Composable
private fun ReadingView(r: WatchReading, stale: Boolean, onRefresh: () -> Unit) {
    val sleeping = r.total < 1.0
    val alpha = if (stale || sleeping) 0.45f else 1f
    val accent = Amber.copy(alpha = alpha)
    val maxPv = max(max(r.pv1, r.pv2), 1.0)

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Total na tle przyćmionej ikonki Streama (inwertera)
        Box(contentAlignment = Alignment.Center) {
            InverterIcon(
                modifier = Modifier.size(86.dp),
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.10f),
            )
            Text(
                "${r.total.roundToInt()} W",
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
        }

        Row(
            modifier = Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            PanelCell("PV1", r.pv1, maxPv, accent, alpha)
            PanelCell("PV2", r.pv2, maxPv, accent, alpha)
        }

        Text(
            text = (if (stale) "⚠ " else "") + timeFmt(r.ts),
            fontSize = 11.sp,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.55f),
            modifier = Modifier.padding(top = 8.dp),
        )
        Button(
            onClick = onRefresh,
            modifier = Modifier.padding(top = 4.dp).size(34.dp),
        ) { Text("↻", fontSize = 16.sp) }
    }
}

@Composable
private fun PanelCell(label: String, watts: Double, maxPv: Double, accent: Color, alpha: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SolarPanelIcon(
                modifier = Modifier.size(13.dp),
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.55f * (alpha / 1f)),
            )
            Text(
                "  $label",
                fontSize = 10.sp,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.55f),
            )
        }
        Text(
            "${watts.roundToInt()} W",
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colors.onBackground.copy(alpha = alpha),
        )
        // pasek proporcjonalny do mocy
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .width(46.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colors.onBackground.copy(alpha = 0.15f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((watts / maxPv).toFloat().coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent),
            )
        }
    }
}

@Composable
private fun ErrorView(msg: String, onRefresh: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        Text("Błąd", fontWeight = FontWeight.Bold, color = MaterialTheme.colors.error)
        Text(msg, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        Button(onClick = onRefresh, modifier = Modifier.padding(top = 8.dp).size(36.dp)) {
            Text("↻", fontSize = 16.sp)
        }
    }
}

/** Ikonka panelu PV: prostokąt z siatką ogniw. */
@Composable
private fun SolarPanelIcon(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = w * 0.08f
        drawRect(color, topLeft = Offset(0f, 0f), size = Size(w, h), style = Stroke(width = sw))
        drawLine(color, Offset(w / 2f, 0f), Offset(w / 2f, h), strokeWidth = sw * 0.8f)
        drawLine(color, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth = sw * 0.8f)
        drawLine(color, Offset(0f, 2f * h / 3f), Offset(w, 2f * h / 3f), strokeWidth = sw * 0.8f)
    }
}

/** Ikonka inwertera Streama (tło): zaokrąglony prostokąt z „błyskawicą" mocy. */
@Composable
private fun InverterIcon(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = w * 0.05f
        // obudowa
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.1f, h * 0.18f),
            size = Size(w * 0.8f, h * 0.64f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f, w * 0.08f),
            style = Stroke(width = sw),
        )
        // błyskawica
        val cx = w / 2f
        drawLine(color, Offset(cx + w * 0.05f, h * 0.30f), Offset(cx - w * 0.10f, h * 0.52f), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(color, Offset(cx - w * 0.10f, h * 0.52f), Offset(cx + w * 0.06f, h * 0.52f), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(color, Offset(cx + w * 0.06f, h * 0.52f), Offset(cx - w * 0.05f, h * 0.72f), strokeWidth = sw, cap = StrokeCap.Round)
    }
}

private fun timeFmt(ts: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
