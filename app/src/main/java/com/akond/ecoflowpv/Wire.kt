package com.akond.ecoflowpv

import org.json.JSONObject

/** Ścieżki Data Layer (muszą się zgadzać po stronie zegarka). */
object WearPaths {
    const val REQUEST = "/request-reading"
    const val READING = "/reading"
}

/** Serializacja odczytu do wysyłki na zegarek. */
fun StreamReading.toWireJson(): String = JSONObject().apply {
    put("pv1", pv1)
    put("pv2", pv2)
    gridPower?.let { put("grid", it) }
    gridVol?.let { put("vol", it) }
    invTemp?.let { put("temp", it) }
    wifiRssi?.let { put("rssi", it) }
    put("ts", timestamp)
}.toString()

fun errorWireJson(message: String): String =
    JSONObject().apply { put("error", message) }.toString()
