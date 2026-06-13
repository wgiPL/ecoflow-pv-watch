package com.akond.ecoflowpv

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** Jeden odczyt telemetrii Streama (zob. API_NOTES.md). */
data class StreamReading(
    val pv1: Double,
    val pv2: Double,
    val gridPower: Double?,
    val gridVol: Double?,
    val invTemp: Int?,
    val wifiRssi: Int?,
    val timestamp: Long,
    val raw: Map<String, String> = emptyMap(),
) {
    val total: Double get() = pv1 + pv2
}

/**
 * Pobiera telemetrię Streama przez MQTT. Łączy się na żądanie, akumuluje
 * przyrostowe wiadomości aż uzbiera komplet (powGetPv + powGetPv2), po czym
 * rozłącza i zwraca odczyt. Lazy-fetch — zero pracy w tle.
 */
class EcoFlowMqtt(
    private val client: EcoFlowClient,
    private val sn: String,
) {
    suspend fun fetchReading(timeoutMs: Long = 10_000): StreamReading =
        withContext(Dispatchers.IO) {
            val cert = client.certification()
            val state = ConcurrentHashMap<String, Any>()
            val gotBoth = CompletableDeferred<Unit>()

            val clientId = "${cert.account}-${(1000..9999).random()}"
            val mqtt = MqttClient("ssl://${cert.url}:${cert.port}", clientId, MemoryPersistence())

            mqtt.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {}
                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    val json = runCatching { JSONObject(message.toString()) }.getOrNull() ?: return
                    json.keys().forEach { k -> state[k] = json.get(k) }
                    if (state.containsKey("powGetPv") && state.containsKey("powGetPv2")) {
                        gotBoth.complete(Unit)
                    }
                }
            })

            val opts = MqttConnectOptions().apply {
                userName = cert.account
                password = cert.password.toCharArray()
                isCleanSession = true
                connectionTimeout = 15
                keepAliveInterval = 30
            }

            try {
                mqtt.connect(opts)
                mqtt.subscribe("/open/${cert.account}/$sn/quota", 0)
                val got = withTimeoutOrNull(timeoutMs) { gotBoth.await() }
                if (got != null) delay(1200) // dozbieraj pełniejszy snapshot pól
            } finally {
                runCatching { if (mqtt.isConnected) mqtt.disconnect() }
                runCatching { mqtt.close() }
            }

            fun num(key: String): Double? = (state[key] as? Number)?.toDouble()

            val pv1 = num("powGetPv")
            val pv2 = num("powGetPv2")
            if (pv1 == null && pv2 == null && num("gridConnectionPower") == null) {
                throw EcoFlowException("Brak danych z MQTT w ${timeoutMs}ms (Stream śpi?)")
            }
            StreamReading(
                pv1 = pv1 ?: 0.0,
                pv2 = pv2 ?: 0.0,
                gridPower = num("gridConnectionPower"),
                gridVol = num("gridConnectionVol"),
                invTemp = num("invNtcTemp3")?.toInt(),
                wifiRssi = num("moduleWifiRssi")?.toInt(),
                timestamp = System.currentTimeMillis(),
                raw = state.toSortedMap().mapValues { it.value.toString() },
            )
        }
}
