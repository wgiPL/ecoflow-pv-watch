package com.akond.ecoflowpv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Minimalny klient EcoFlow IoT Open API.
 *
 * Podpis ustalony empirycznie (signtest.py, patrz API_NOTES.md):
 * dla GET-ów signStr to WYŁĄCZNIE "accessKey=..&nonce=..&timestamp=..",
 * parametry zapytania (np. sn) idą tylko do query stringa - wbrew dokumentacji.
 */
class EcoFlowClient(
    private val accessKey: String,
    private val secretKey: String,
    private val baseUrl: String = "https://api.ecoflow.com",
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val secureRandom = SecureRandom()

    data class Device(val sn: String, val name: String, val online: Boolean)

    data class MqttCert(val account: String, val password: String, val url: String, val port: Int)

    /** Dane dostępowe do brokera MQTT (jedyne źródło telemetrii Streama). */
    suspend fun certification(): MqttCert {
        val d = get("/iot-open/sign/certification").optJSONObject("data")
            ?: throw EcoFlowException("certification: brak pola data")
        return MqttCert(
            account = d.getString("certificateAccount"),
            password = d.getString("certificatePassword"),
            url = d.getString("url"),
            port = d.optString("port", "8883").toInt(),
        )
    }

    suspend fun deviceList(): List<Device> {
        val json = get("/iot-open/sign/device/list")
        val data = json.optJSONArray("data") ?: return emptyList()
        return (0 until data.length()).mapNotNull { i ->
            val d = data.optJSONObject(i) ?: return@mapNotNull null
            val sn = d.optString("sn", d.optString("deviceSn"))
            if (sn.isEmpty()) return@mapNotNull null
            Device(
                sn = sn,
                name = d.optString("deviceName", sn),
                online = d.optInt("online", 0) == 1,
            )
        }
    }

    /** Pełna telemetria urządzenia. Gdy urządzenie śpi, "data" może nie istnieć. */
    suspend fun quotaAllRaw(sn: String): JSONObject =
        get("/iot-open/sign/device/quota/all", mapOf("sn" to sn))

    private suspend fun get(path: String, params: Map<String, String> = emptyMap()): JSONObject =
        withContext(Dispatchers.IO) {
            val nonce = (secureRandom.nextInt(900_000) + 100_000).toString()
            val timestamp = System.currentTimeMillis().toString()
            val signStr = "accessKey=$accessKey&nonce=$nonce&timestamp=$timestamp"

            val urlBuilder = (baseUrl + path).toHttpUrl().newBuilder()
            params.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }

            val request = Request.Builder()
                .url(urlBuilder.build())
                .header("accessKey", accessKey)
                .header("nonce", nonce)
                .header("timestamp", timestamp)
                .header("sign", hmacSha256Hex(secretKey, signStr))
                .get()
                .build()

            http.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw EcoFlowException("HTTP ${resp.code}: ${body.take(200)}")
                }
                val json = JSONObject(body)
                val code = json.optString("code")
                if (code != "0") {
                    throw EcoFlowException("EcoFlow code=$code: ${json.optString("message")}")
                }
                json
            }
        }

    private fun hmacSha256Hex(secret: String, message: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(message.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}

class EcoFlowException(message: String) : Exception(message)
