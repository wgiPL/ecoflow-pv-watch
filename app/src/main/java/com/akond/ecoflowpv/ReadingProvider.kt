package com.akond.ecoflowpv

/**
 * Wspólne źródło odczytu z 5-minutowym cache. Używane przez ekran telefonu
 * i usługę Data Layer — telefon łączy się z MQTT tylko gdy cache wygasł.
 *
 * SN: jeśli ustawiony w secrets.properties (ECOFLOW_SN) — używamy go; w przeciwnym
 * razie wykrywamy pierwsze urządzenie z device/list (apka działa „z pudełka").
 */
object ReadingProvider {
    private const val TTL_MS = 5 * 60 * 1000L

    private val client by lazy {
        EcoFlowClient(BuildConfig.ECOFLOW_ACCESS_KEY, BuildConfig.ECOFLOW_SECRET_KEY)
    }

    @Volatile
    private var mqtt: EcoFlowMqtt? = null

    @Volatile
    private var cached: StreamReading? = null

    private suspend fun mqtt(): EcoFlowMqtt {
        mqtt?.let { return it }
        val sn = BuildConfig.ECOFLOW_SN.ifBlank {
            client.deviceList().firstOrNull()?.sn
                ?: throw EcoFlowException("Brak urządzeń na koncie EcoFlow")
        }
        return EcoFlowMqtt(client, sn).also { mqtt = it }
    }

    /** Zwraca świeży odczyt z cache, albo łączy się z MQTT gdy starszy niż TTL. */
    suspend fun get(force: Boolean = false): StreamReading {
        val c = cached
        if (!force && c != null && System.currentTimeMillis() - c.timestamp < TTL_MS) return c
        return mqtt().fetchReading().also { cached = it }
    }

    fun cachedOrNull(): StreamReading? = cached
}
