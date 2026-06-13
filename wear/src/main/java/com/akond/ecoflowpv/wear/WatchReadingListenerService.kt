package com.akond.ecoflowpv.wear

import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/**
 * Nasłuchuje odpowiedzi telefonu (READING) także gdy apka jest zamknięta —
 * zapisuje odczyt do store i prosi system o odświeżenie kafelka.
 */
class WatchReadingListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WPaths.READING) return
        parseReading(String(event.data)).onSuccess {
            ReadingStore.save(this, it)
            TileService.getUpdater(this).requestUpdate(PvTileService::class.java)
        }
    }
}
