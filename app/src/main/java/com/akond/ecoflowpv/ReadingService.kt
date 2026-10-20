package com.akond.ecoflowpv

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

/**
 * Słucha próśb z zegarka (Data Layer). Po otrzymaniu REQUEST pobiera odczyt
 * (z cache albo świeży przez MQTT) i odsyła na zegarek jako READING.
 *
 * onMessageReceived leci na wątku roboczym usługi, więc runBlocking jest OK.
 */
class ReadingService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WearPaths.REQUEST) return
        if (!isTrustedSender(event.sourceNodeId)) return

        val payload = runCatching {
            runBlocking { ReadingProvider.get().toWireJson() }
        }.getOrElse { e ->
            // gdy świeży odczyt się nie udał, oddaj ostatni znany albo błąd
            ReadingProvider.cachedOrNull()?.toWireJson()
                ?: errorWireJson(e.message ?: "nieznany błąd")
        }

        Wearable.getMessageClient(this)
            .sendMessage(event.sourceNodeId, WearPaths.READING, payload.toByteArray())
    }

    /**
     * Defense-in-depth: odpowiadaj tylko węzłom faktycznie połączonym z tym
     * urządzeniem (sparowany zegarek), nie dowolnemu nadawcy z sieci Wearable.
     */
    private fun isTrustedSender(sourceNodeId: String): Boolean = runCatching {
        runBlocking {
            Wearable.getNodeClient(this@ReadingService).connectedNodes.await()
                .any { it.id == sourceNodeId }
        }
    }.getOrDefault(false)
}
