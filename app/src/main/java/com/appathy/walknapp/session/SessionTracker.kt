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

    private val track = mutableListOf<TrackPoint>()
    private var last: TrackPoint? = null

    fun start() {
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
