package com.akond.ecoflowpv.wear

import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.FONT_WEIGHT_BOLD
import androidx.wear.protolayout.LayoutElementBuilders.FONT_WEIGHT_NORMAL
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.VERTICAL_ALIGN_CENTER
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.Wearable
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlin.math.roundToInt

/**
 * Kafelek (Tile) z produkcją PV. Renderuje ostatni znany odczyt natychmiast,
 * a w tle prosi telefon o świeże dane — gdy przyjdą, WatchReadingListenerService
 * zapisuje je i woła requestUpdate, więc kafelek się odświeża.
 */
class PvTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> {
        requestFreshReading()
        val r = ReadingStore.load(applicationContext)
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RES_VERSION)
            .setFreshnessIntervalMillis(10 * 60 * 1000L)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layoutFor(r)))
            .build()
        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(
            ResourceBuilders.Resources.Builder().setVersion(RES_VERSION).build()
        )

    private fun requestFreshReading() {
        val ctx = applicationContext
        Wearable.getNodeClient(ctx).connectedNodes.addOnSuccessListener { nodes ->
            val phone = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
            if (phone != null) {
                Wearable.getMessageClient(ctx)
                    .sendMessage(phone.id, WPaths.REQUEST, ByteArray(0))
            }
        }
    }

    private fun layoutFor(r: WatchReading?): LayoutElementBuilders.LayoutElement {
        val amber = argb(0xFFFFC107.toInt())
        val muted = argb(0xFFB0BEC5.toInt())
        val totalText = if (r == null) "—" else "${r.total.roundToInt()} W"
        val subText = if (r == null) "otwórz aplikację"
        else "${r.pv1.roundToInt()} W · ${r.pv2.roundToInt()} W"

        val column = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .addContent(text(totalText, 36f, amber, bold = true))
            .addContent(
                LayoutElementBuilders.Spacer.Builder().setHeight(dp(4f)).build()
            )
            .addContent(text(subText, 15f, muted))
            .build()

        return LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .addContent(column)
            .build()
    }

    private fun text(
        s: String,
        size: Float,
        color: androidx.wear.protolayout.ColorBuilders.ColorProp,
        bold: Boolean = false,
    ): LayoutElementBuilders.Text =
        LayoutElementBuilders.Text.Builder()
            .setText(s)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(size))
                    .setColor(color)
                    .setWeight(if (bold) FONT_WEIGHT_BOLD else FONT_WEIGHT_NORMAL)
                    .build()
            )
            .build()

    companion object {
        private const val RES_VERSION = "1"
    }
}
