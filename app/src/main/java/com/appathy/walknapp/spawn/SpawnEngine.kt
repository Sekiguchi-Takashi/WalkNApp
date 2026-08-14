package com.appathy.walknapp.spawn

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Random
import kotlin.math.floor

data class SpawnPoint(
    val id: String,
    val cellX: Int,
    val cellY: Int,
    val lat: Double,
    val lng: Double,
    val itemDef: ItemDefinition,
    val dateKey: String
)

object SpawnEngine {

    const val CELL_SIZE_DEG = 0.0015

    fun cellXOf(lng: Double): Int = floor(lng / CELL_SIZE_DEG).toInt()
    fun cellYOf(lat: Double): Int = floor(lat / CELL_SIZE_DEG).toInt()

    fun todayKey(): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    private fun seedOf(cellX: Int, cellY: Int, dateKey: String): Long {
        var h = 1125899906842597L
        val s = "$cellX:$cellY:$dateKey"
        for (c in s) {
            h = 31 * h + c.code
        }
        return h
    }

    fun spawnsInCell(cellX: Int, cellY: Int, dateKey: String, bonus: Int = 0): List<SpawnPoint> {
        val rnd = Random(seedOf(cellX, cellY, dateKey))
        val base = when (rnd.nextInt(100)) {
            in 0..44 -> 0
            in 45..79 -> 1
            in 80..94 -> 2
            else -> 3
        }
        val count = (base + bonus).coerceAtMost(5)
        if (count == 0) return emptyList()
        return (0 until count).map { i ->
            val lat = (cellY + rnd.nextDouble()) * CELL_SIZE_DEG
            val lng = (cellX + rnd.nextDouble()) * CELL_SIZE_DEG
            SpawnPoint(
                id = "$cellX:$cellY:$dateKey:$i",
                cellX = cellX,
                cellY = cellY,
                lat = lat,
                lng = lng,
                itemDef = ItemCatalog.pickByRarity(rnd),
                dateKey = dateKey
            )
        }
    }

    fun spawnsAround(lat: Double, lng: Double, dateKey: String = todayKey(), bonus: Int = 0): List<SpawnPoint> {
        val cx = cellXOf(lng)
        val cy = cellYOf(lat)
        val result = mutableListOf<SpawnPoint>()
        for (dx in -1..1) {
            for (dy in -1..1) {
                result += spawnsInCell(cx + dx, cy + dy, dateKey, bonus)
            }
        }
        return result
    }

    fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2)
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }
}
