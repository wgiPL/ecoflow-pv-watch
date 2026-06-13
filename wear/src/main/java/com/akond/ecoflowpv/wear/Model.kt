package com.akond.ecoflowpv.wear

import android.content.Context
import org.json.JSONObject

/** Ścieżki Data Layer (zgodne z telefonem). */
object WPaths {
    const val REQUEST = "/request-reading"
    const val READING = "/reading"
}

data class WatchReading(
    val pv1: Double,
    val pv2: Double,
    val grid: Double?,
    val temp: Int?,
    val ts: Long,
) {
    val total get() = pv1 + pv2
}

fun parseReading(json: String): Result<WatchReading> = runCatching {
    val o = JSONObject(json)
    if (o.has("error")) error(o.getString("error"))
    WatchReading(
        pv1 = o.optDouble("pv1", 0.0),
        pv2 = o.optDouble("pv2", 0.0),
        grid = if (o.has("grid")) o.getDouble("grid") else null,
        temp = if (o.has("temp")) o.getInt("temp") else null,
        ts = o.optLong("ts", System.currentTimeMillis()),
    )
}

/** Ostatni odczyt zapisany lokalnie — żeby Tile miał co pokazać natychmiast. */
object ReadingStore {
    private const val PREF = "pv_store"

    fun save(ctx: Context, r: WatchReading) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putFloat("pv1", r.pv1.toFloat())
            .putFloat("pv2", r.pv2.toFloat())
            .putLong("ts", r.ts)
            .apply()
    }

    fun load(ctx: Context): WatchReading? {
        val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        if (!p.contains("ts")) return null
        return WatchReading(
            pv1 = p.getFloat("pv1", 0f).toDouble(),
            pv2 = p.getFloat("pv2", 0f).toDouble(),
            grid = null,
            temp = null,
            ts = p.getLong("ts", 0L),
        )
    }
}
