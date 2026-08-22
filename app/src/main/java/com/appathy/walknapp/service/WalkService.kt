package com.appathy.walknapp.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.appathy.walknapp.MainActivity
import com.appathy.walknapp.R
import com.appathy.walknapp.data.AssetEntity
import com.appathy.walknapp.data.AssetEventEntity
import com.appathy.walknapp.data.AssetStatus
import com.appathy.walknapp.data.DailyQuotaEntity
import com.appathy.walknapp.data.WalkDatabase
import com.appathy.walknapp.data.WalkSessionEntity
import com.appathy.walknapp.game.Balance
import com.appathy.walknapp.game.ShoeType
import com.appathy.walknapp.game.SpeedState
import com.appathy.walknapp.game.WalkEngine
import com.appathy.walknapp.game.WalkRuntime
import com.appathy.walknapp.game.WearType
import com.appathy.walknapp.session.SpeedFormat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class WalkService : Service() {

    companion object {
        const val CHANNEL_ID = "walk_session"
        const val NOTIFICATION_ID = 21
        const val ACTION_START = "com.appathy.walknapp.START"
        const val ACTION_STOP = "com.appathy.walknapp.STOP"

        fun start(context: Context) {
            val i = Intent(context, WalkService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            val i = Intent(context, WalkService::class.java).setAction(ACTION_STOP)
            context.startService(i)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null
    private lateinit var engine: WalkEngine
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            engine.onLocation(loc.latitude, loc.longitude, System.currentTimeMillis())
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        engine = WalkEngine(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSession()
                return START_NOT_STICKY
            }
            else -> startSession()
        }
        return START_STICKY
    }

    private fun todayKey(): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    private fun startSession() {
        if (loop != null) return
        startForeground(NOTIFICATION_ID, buildNotification("記録を開始しています", ""))

        loop = scope.launch {
            val db = WalkDatabase.get(applicationContext)
            val shoeRow = db.dao().equippedShoe()
            val loadout = db.dao().loadout()
            if (shoeRow == null || loadout == null || shoeRow.durability <= 0) {
                WalkRuntime.notify("使える靴がありません")
                stopSession()
                return@launch
            }
            val shoe = ShoeType.valueOf(shoeRow.shoeType)
            val wear = WearType.valueOf(loadout.wearType)
            engine.start(
                shoe, wear,
                mapOf(
                    "LOW" to loadout.strideLow,
                    "MID" to loadout.strideMid,
                    "HIGH" to loadout.strideHigh
                )
            )

            val sid = db.dao().insertSession(
                WalkSessionEntity(
                    startAt = System.currentTimeMillis(),
                    shoeType = shoe.name,
                    wearType = wear.name
                )
            )
            WalkRuntime.sessionId = sid
            WalkRuntime.setRunning(true)
            requestLocation()

            var lastAt = System.currentTimeMillis()
            while (true) {
                delay(2000)
                val now = System.currentTimeMillis()
                val elapsed = ((now - lastAt) / 1000).coerceAtLeast(1)
                lastAt = now
                val t = engine.onTick(now, elapsed)
                WalkRuntime.publish(t, engine.points())
                updateNotification(t.state, t)

                if (t.grantReady) {
                    grant(db, sid, wear, t.continuousSec, t.speedKmh, t.speedSource, shoe)
                }
            }
        }
    }

    private suspend fun grant(
        db: WalkDatabase,
        sid: Long,
        wear: WearType,
        continuousSec: Long,
        speedKmh: Double,
        source: String,
        shoe: ShoeType
    ) {
        val now = System.currentTimeMillis()
        val q = db.dao().quotaOf(todayKey()) ?: DailyQuotaEntity(todayKey())
        val cap = Balance.DAILY_CAP_BASE +
                (q.streakDays * Balance.STREAK_BONUS_PER_DAY)
                    .coerceAtMost(Balance.STREAK_BONUS_MAX)
        if (q.earnedPoints >= cap) {
            db.dao().insertEvent(
                AssetEventEntity(assetUuid = null, kind = "OVERFLOW", at = now, detail = "cap=$cap")
            )
            WalkRuntime.notify("本日の上限（${cap}pt）に達しました")
            engine.consumeGrant()
            return
        }
        val rank = wear.rank
        val pt = rank.rollPoint().coerceAtMost(cap - q.earnedPoints)
        val last = engine.points().lastOrNull()
        val uuid = UUID.randomUUID().toString()
        db.dao().insertAsset(
            AssetEntity(
                uuid = uuid,
                rank = rank.name,
                repairPoint = pt,
                acquiredAt = now,
                acquiredLat = last?.lat ?: 0.0,
                acquiredLng = last?.lng ?: 0.0,
                validSecAtGrant = continuousSec,
                avgSpeedKmh = speedKmh,
                shoeType = shoe.name,
                wearType = wear.name,
                speedSource = source,
                sessionId = sid,
                status = AssetStatus.INTERNAL.name
            )
        )
        db.dao().insertEvent(
            AssetEventEntity(assetUuid = uuid, kind = "GRANT", at = now, detail = "${rank.name}+$pt")
        )
        db.dao().loadout()?.let { lo ->
            db.dao().saveLoadout(lo.copy(repairWallet = lo.repairWallet + pt))
        }
        db.dao().saveQuota(q.copy(earnedPoints = q.earnedPoints + pt))
        db.dao().sessionById(sid)?.let { db.dao().updateSession(it.copy(grants = it.grants + 1)) }
        engine.consumeGrant()
        WalkRuntime.notify("${rank.label}を獲得（修理ポイント +$pt）")
        notifyGrant(rank.label, pt)
    }

    private fun stopSession() {
        val job = loop
        loop = null
        scope.launch {
            runCatching { fused.removeLocationUpdates(locationCallback) }
            job?.cancel()
            engine.stop()
            val db = WalkDatabase.get(applicationContext)
            val sid = WalkRuntime.sessionId
            if (sid != null) {
                db.dao().sessionById(sid)?.let { s ->
                    db.dao().updateSession(
                        s.copy(
                            endAt = System.currentTimeMillis(),
                            validSec = engine.validSec,
                            distanceM = engine.distanceM,
                            steps = engine.steps,
                            routeJson = engine.routeJson(),
                            durabilityUsed = engine.durabilityConsumed
                        )
                    )
                }
            }
            db.dao().equippedShoe()?.let { s ->
                db.dao().updateShoe(
                    s.copy(
                        durability = (s.durability - engine.durabilityConsumed).coerceAtLeast(0),
                        totalValidSec = s.totalValidSec + engine.validSec
                    )
                )
            }
            val q = db.dao().quotaOf(todayKey()) ?: DailyQuotaEntity(todayKey())
            val total = q.validSec + engine.validSec
            var streak = q.streakDays
            var achieved = q.achieved
            if (!achieved && total >= Balance.STREAK_GOAL_SEC) {
                achieved = true
                streak = q.streakDays + 1
                WalkRuntime.notify("本日の60分を達成。明日から上限が上がります")
            }
            db.dao().saveQuota(q.copy(validSec = total, achieved = achieved, streakDays = streak))

            // 歩幅の学習（GPSが十分に取れたセッションのみ）
            val learned = engine.learnedStrides()
            if (learned.isNotEmpty()) {
                db.dao().loadout()?.let { lo ->
                    fun blend(old: Double, new: Double?): Double =
                        if (new == null) old
                        else Math.round((old * 0.7 + new * 0.3) * 1000.0) / 1000.0
                    db.dao().saveLoadout(
                        lo.copy(
                            strideLow = blend(lo.strideLow, learned["LOW"]),
                            strideMid = blend(lo.strideMid, learned["MID"]),
                            strideHigh = blend(lo.strideHigh, learned["HIGH"])
                        )
                    )
                }
            }

            WalkRuntime.notify(
                "終了: 有効 ${SpeedFormat.clock(engine.validSec)} / 耐久 -${engine.durabilityConsumed}"
            )
            WalkRuntime.sessionId = null
            WalkRuntime.setRunning(false)
            stopForegroundCompat()
            stopSelf()
        }
    }

    private fun requestLocation() {
        val fine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fine) return
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
            .setMinUpdateDistanceMeters(3f)
            .build()
        runCatching { fused.requestLocationUpdates(req, locationCallback, mainLooper) }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID, "ウォーキング記録", NotificationManager.IMPORTANCE_LOW
                    )
                )
            }
        }
    }

    private fun buildNotification(title: String, body: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, WalkService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pi)
            .addAction(0, "ストップ", stopPi)
            .build()
    }

    private fun updateNotification(state: SpeedState, t: com.appathy.walknapp.game.Tick) {
        val nm = getSystemService(NotificationManager::class.java)
        val body = "${SpeedFormat.kmh(t.speedKmh)} / 有効 ${SpeedFormat.clock(t.validSec)} / 連続 ${SpeedFormat.clock(t.continuousSec)}"
        nm.notify(NOTIFICATION_ID, buildNotification(state.label, body))
    }

    private fun notifyGrant(label: String, pt: Int) {
        val nm = getSystemService(NotificationManager::class.java)
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("${label}を獲得")
            .setContentText("修理ポイント +$pt")
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID + 1, n)
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        runCatching { fused.removeLocationUpdates(locationCallback) }
        loop?.cancel()
        scope.cancel()
        WalkRuntime.setRunning(false)
        super.onDestroy()
    }
}
