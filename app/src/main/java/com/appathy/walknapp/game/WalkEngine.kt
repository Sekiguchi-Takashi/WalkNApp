package com.appathy.walknapp.game

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import org.json.JSONArray
import org.json.JSONObject

data class TrackPoint(val lat: Double, val lng: Double, val at: Long)

data class Tick(
    val speedKmh: Double,
    val state: SpeedState,
    val validSec: Long,
    val continuousSec: Long,
    val distanceM: Double,
    val steps: Int,
    val speedSource: String,
    val graceLeftSec: Long,
    val durabilityConsumed: Int,
    val grantReady: Boolean
)

object Geo {
    fun meters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2)
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    }
}

/**
 * 速度・有効時間・付与判定をまとめて持つ。
 * onTick を一定間隔で呼ぶと状態を進める。
 */
class WalkEngine(context: Context) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    private var baseSteps: Float? = null
    var steps: Int = 0
        private set

    var validSec: Long = 0
        private set
    var continuousSec: Long = 0
        private set
    var distanceM: Double = 0.0
        private set
    var durabilityConsumed: Int = 0
        private set
    var speedKmh: Double = 0.0
        private set
    var state: SpeedState = SpeedState.IDLE
        private set
    var speedSource: String = "GPS"
        private set

    private lateinit var shoe: ShoeType
    private lateinit var wear: WearType

    private val track = mutableListOf<TrackPoint>()
    private val window = mutableListOf<Pair<Double, Long>>()
    private var last: TrackPoint? = null
    private var lastTickAt: Long = 0
    private var graceUntil: Long = 0
    private var validSecCarry: Long = 0
    private var stepFallbackSec: Long = 0
    private var lastStepAt: Long = 0
    private var lastStepCount: Int = 0

    // 屋内判定用
    private val moveWindow = mutableListOf<Pair<Double, Long>>()

    var strideM: Double = Balance.DEFAULT_STRIDE_M

    fun start(shoe: ShoeType, wear: WearType, stride: Double) {
        this.shoe = shoe
        this.wear = wear
        this.strideM = if (stride > 0.3 && stride < 1.2) stride else Balance.DEFAULT_STRIDE_M
        steps = 0; baseSteps = null
        validSec = 0; continuousSec = 0; distanceM = 0.0
        durabilityConsumed = 0; validSecCarry = 0
        speedKmh = 0.0; state = SpeedState.TOO_SLOW; speedSource = "GPS"
        stepFallbackSec = 0; lastStepAt = 0; lastStepCount = 0
        track.clear(); window.clear(); moveWindow.clear()
        last = null; lastTickAt = 0; graceUntil = 0
        stepSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        state = SpeedState.IDLE
    }

    fun hasStepSensor(): Boolean = stepSensor != null

    fun onLocation(lat: Double, lng: Double, at: Long) {
        val prev = last
        val p = TrackPoint(lat, lng, at)
        if (prev == null) {
            track.add(p); last = p
            return
        }
        val d = Geo.meters(prev.lat, prev.lng, lat, lng)
        if (d < 3.0) return
        val dt = (at - prev.at) / 1000.0
        if (dt <= 0) return
        distanceM += d
        track.add(p)
        last = p
        window.add(d to at)
        moveWindow.add(d to at)
        speedSource = "GPS"
    }

    /** 一定間隔で呼ぶ。elapsed は前回 onTick からの経過秒 */
    fun onTick(now: Long, elapsedSec: Long): Tick {
        trimWindows(now)
        computeSpeed(now)
        val inBand = shoe.contains(speedKmh)
        val indoor = isIndoor(now)

        state = when {
            indoor -> SpeedState.INDOOR
            inBand -> SpeedState.OPTIMAL
            now < graceUntil -> SpeedState.GRACE
            speedKmh < shoe.minKmh -> SpeedState.TOO_SLOW
            else -> SpeedState.TOO_FAST
        }

        if (state == SpeedState.OPTIMAL) {
            validSec += elapsedSec
            continuousSec += elapsedSec
            validSecCarry += elapsedSec
            graceUntil = now + Balance.GRACE_SEC * 1000
            while (validSecCarry >= Balance.SEC_PER_DURABILITY) {
                validSecCarry -= Balance.SEC_PER_DURABILITY
                durabilityConsumed += 1
            }
        } else if (state != SpeedState.GRACE) {
            continuousSec = 0
            graceUntil = 0
        }

        val graceLeft =
            if (state == SpeedState.GRACE) ((graceUntil - now) / 1000).coerceAtLeast(0) else 0

        return Tick(
            speedKmh = speedKmh,
            state = state,
            validSec = validSec,
            continuousSec = continuousSec,
            distanceM = distanceM,
            steps = steps,
            speedSource = speedSource,
            graceLeftSec = graceLeft,
            durabilityConsumed = durabilityConsumed,
            grantReady = continuousSec >= wear.thresholdSec
        )
    }

    /** 付与が成立したら連続時間をリセットして次の周期へ */
    fun consumeGrant() {
        continuousSec = 0
    }

    private fun trimWindows(now: Long) {
        val cut = now - Balance.SPEED_WINDOW_SEC * 1000
        window.removeAll { it.second < cut }
        val cut2 = now - Balance.INDOOR_WINDOW_SEC * 1000
        moveWindow.removeAll { it.second < cut2 }
    }

    private fun computeSpeed(now: Long) {
        val fresh = last != null && (now - last!!.at) < 20_000
        if (fresh && window.size >= 2) {
            val span = (window.last().second - window.first().second) / 1000.0
            val dist = window.drop(1).sumOf { it.first }
            speedKmh = if (span > 0) dist / span * 3.6 else 0.0
            speedSource = "GPS"
            stepFallbackSec = 0
            return
        }
        // GPS が不良 or 未取得 → 歩数センサーから推定
        val cadence = cadencePerSec(now)
        if (cadence > 0 && stepFallbackSec < Balance.STEP_ESTIMATE_MAX_SEC) {
            speedKmh = cadence * strideM * 3.6
            speedSource = "STEP_ESTIMATE"
            stepFallbackSec += 2
        } else {
            speedKmh = 0.0
            speedSource = if (cadence > 0) "STEP_LIMIT" else "GPS"
        }
    }

    private fun cadencePerSec(now: Long): Double {
        if (lastStepAt == 0L) return 0.0
        val dt = (now - lastStepAt) / 1000.0
        if (dt <= 0 || dt > 30) return 0.0
        val d = steps - lastStepCount
        return if (d > 0) d / dt else 0.0
    }

    private fun isIndoor(now: Long): Boolean {
        if (moveWindow.isEmpty()) return false
        val span = now - moveWindow.first().second
        if (span < Balance.INDOOR_WINDOW_SEC * 1000) return false
        val moved = moveWindow.sumOf { it.first }
        return moved < Balance.INDOOR_MIN_MOVE_M && steps > lastStepCount
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_STEP_COUNTER) return
        val total = event.values.firstOrNull() ?: return
        val base = baseSteps
        if (base == null) {
            baseSteps = total
        } else {
            lastStepCount = steps
            lastStepAt = System.currentTimeMillis()
            steps = (total - base).toInt().coerceAtLeast(0)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun points(): List<TrackPoint> = track.toList()

    fun routeJson(): String {
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

    /** GPS良好区間の 距離÷歩数 から歩幅を学習した値を返す（サンプル不足なら null） */
    fun learnedStride(): Double? {
        if (steps < 200 || distanceM < 150) return null
        val s = distanceM / steps
        return if (s in 0.3..1.2) s else null
    }
}
