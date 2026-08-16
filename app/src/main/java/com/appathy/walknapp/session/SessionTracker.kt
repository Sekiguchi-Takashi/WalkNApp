package com.appathy.walknapp.session

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.appathy.walknapp.spawn.SpawnEngine
import org.json.JSONArray
import org.json.JSONObject

data class TrackPoint(val lat: Double, val lng: Double, val at: Long)

const val MAX_SPEED_MPS = 4.17

class SessionTracker(context: Context) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    private var baseStepCount: Float? = null

    var steps: Int = 0
        private set
    var distanceM: Double = 0.0
        private set
    var invalidSegments: Int = 0
        private set
    var itemCount: Int = 0
        private set
    var currentSpeedMps: Double = 0.0
        private set
    var startedAt: Long = 0L
        private set

    private val track = mutableListOf<TrackPoint>()
    private var last: TrackPoint? = null
    private val recent = mutableListOf<Pair<Double, Long>>()

    fun start() {
        startedAt = System.currentTimeMillis()
        currentSpeedMps = 0.0
        recent.clear()
        steps = 0
        distanceM = 0.0
        invalidSegments = 0
        itemCount = 0
        baseStepCount = null
        track.clear()
        last = null
        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    fun countItem() {
        itemCount += 1
    }

    fun onLocation(lat: Double, lng: Double, at: Long) {
        val p = TrackPoint(lat, lng, at)
        val prev = last
        if (prev != null) {
            val d = SpawnEngine.distanceMeters(prev.lat, prev.lng, lat, lng)
            val dtSec = (at - prev.at) / 1000.0
            if (dtSec > 0) {
                val speed = d / dtSec
                if (speed > MAX_SPEED_MPS) {
                    invalidSegments += 1
                } else if (d >= 3.0) {
                    distanceM += d
                    track.add(p)
                    last = p
                    pushRecent(d, at)
                    return
                }
            }
            if (d >= 3.0) {
                last = p
            }
        } else {
            track.add(p)
            last = p
        }
    }

    private fun pushRecent(d: Double, at: Long) {
        recent.add(d to at)
        val cutoff = at - 30_000
        recent.removeAll { it.second < cutoff }
        if (recent.size >= 2) {
            val span = (recent.last().second - recent.first().second) / 1000.0
            val dist = recent.drop(1).sumOf { it.first }
            currentSpeedMps = if (span > 0) dist / span else 0.0
        } else {
            currentSpeedMps = 0.0
        }
    }

    fun decayIfIdle(now: Long) {
        val lastAt = recent.lastOrNull()?.second ?: return
        if (now - lastAt > 15_000) {
            currentSpeedMps = 0.0
            recent.clear()
        }
    }

    fun averageSpeedMps(now: Long): Double {
        if (startedAt == 0L) return 0.0
        val sec = (now - startedAt) / 1000.0
        return if (sec > 0) distanceM / sec else 0.0
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) return
        val total = event.values.firstOrNull() ?: return
        val base = baseStepCount
        if (base == null) {
            baseStepCount = total
        } else {
            steps = (total - base).toInt().coerceAtLeast(0)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun hasStepSensor(): Boolean = stepSensor != null

    fun points(): List<TrackPoint> = track.toList()

    fun trackJson(): String {
        val arr = JSONArray()
        track.forEach {
            arr.put(
                JSONObject()
                    .put("lat", Math.round(it.lat * 100000.0) / 100000.0)
                    .put("lng", Math.round(it.lng * 100000.0) / 100000.0)
                    .put("at", it.at)
            )
        }
        return arr.toString()
    }
}
