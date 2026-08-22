package com.appathy.walknapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.preference.PreferenceManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.appathy.walknapp.data.AssetEntity
import com.appathy.walknapp.data.AssetEventEntity
import com.appathy.walknapp.data.AssetMetadata
import com.appathy.walknapp.data.AssetStatus
import com.appathy.walknapp.data.DailyQuotaEntity
import com.appathy.walknapp.data.LoadoutEntity
import com.appathy.walknapp.data.ShoeEntity
import com.appathy.walknapp.data.WalkDatabase
import com.appathy.walknapp.data.WalkSessionEntity
import com.appathy.walknapp.game.Balance
import com.appathy.walknapp.game.ItemRank
import com.appathy.walknapp.game.ShoeType
import com.appathy.walknapp.game.SpeedState
import com.appathy.walknapp.game.WalkEngine
import com.appathy.walknapp.game.WearType
import com.appathy.walknapp.session.SpeedFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ctx = applicationContext
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))
        Configuration.getInstance().userAgentValue = packageName
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) { RootScreen() }
            }
        }
    }
}

fun todayKey(): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

suspend fun ensureSeed(db: WalkDatabase) {
    if (db.dao().allShoes().isEmpty()) {
        ShoeType.values().forEachIndexed { i, t ->
            db.dao().insertShoe(
                ShoeEntity(
                    uuid = UUID.randomUUID().toString(),
                    shoeType = t.name,
                    durability = Balance.DURABILITY_MAX,
                    equipped = i == 1
                )
            )
        }
    }
    if (db.dao().loadout() == null) {
        db.dao().saveLoadout(LoadoutEntity(wearType = WearType.STANDARD.name))
    }
}

@Composable
fun RootScreen() {
    val context = LocalContext.current
    val db = remember { WalkDatabase.get(context) }
    var seeded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { ensureSeed(db); seeded = true }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { r -> hasPermission = r[Manifest.permission.ACCESS_FINE_LOCATION] == true }

    var screen by remember { mutableStateOf("walk") }

    if (!hasPermission) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(onClick = {
                launcher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACTIVITY_RECOGNITION
                    )
                )
            }) { Text("位置情報と歩数を許可して開始") }
        }
        return
    }
    if (!seeded) return

    when (screen) {
        "equip" -> EquipScreen { screen = "walk" }
        "bag" -> BagScreen { screen = "walk" }
        "history" -> HistoryScreen { screen = "walk" }
        else -> WalkScreen(
            onEquip = { screen = "equip" },
            onBag = { screen = "bag" },
            onHistory = { screen = "history" }
        )
    }
}

@Composable
fun WalkScreen(onEquip: () -> Unit, onBag: () -> Unit, onHistory: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { WalkDatabase.get(context) }
    val engine = remember { WalkEngine(context) }

    val shoe by db.dao().observeEquippedShoe().collectAsState(initial = null)
    val loadout by db.dao().observeLoadout().collectAsState(initial = null)
    val quota by db.dao().observeQuota(todayKey()).collectAsState(initial = null)

    var running by remember { mutableStateOf(false) }
    var sessionId by remember { mutableStateOf<Long?>(null) }
    var speed by remember { mutableStateOf(0.0) }
    var state by remember { mutableStateOf(SpeedState.IDLE) }
    var validSec by remember { mutableStateOf(0L) }
    var continuousSec by remember { mutableStateOf(0L) }
    var distance by remember { mutableStateOf(0.0) }
    var steps by remember { mutableStateOf(0) }
    var source by remember { mutableStateOf("GPS") }
    var graceLeft by remember { mutableStateOf(0L) }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(18.0)
            controller.setCenter(GeoPoint(35.681236, 139.767125))
        }
    }
    val locationOverlay = remember {
        MyLocationNewOverlay(GpsMyLocationProvider(context), mapView).apply {
            enableMyLocation(); enableFollowLocation()
        }
    }
    val trackLine = remember {
        Polyline().apply {
            outlinePaint.color = 0xFF1E88E5.toInt()
            outlinePaint.strokeWidth = 9f
        }
    }

    val wear = WearType.valueOf(loadout?.wearType ?: WearType.STANDARD.name)
    val shoeType = shoe?.let { ShoeType.valueOf(it.shoeType) }
    val cap = Balance.DAILY_CAP_BASE +
            ((quota?.streakDays ?: 0) * Balance.STREAK_BONUS_PER_DAY)
                .coerceAtMost(Balance.STREAK_BONUS_MAX)
    val earned = quota?.earnedPoints ?: 0

    LaunchedEffect(Unit) {
        if (!mapView.overlays.contains(locationOverlay)) mapView.overlays.add(locationOverlay)
        if (!mapView.overlays.contains(trackLine)) mapView.overlays.add(0, trackLine)
        var lastAt = System.currentTimeMillis()
        while (true) {
            delay(2000)
            val now = System.currentTimeMillis()
            val elapsed = ((now - lastAt) / 1000).coerceAtLeast(1)
            lastAt = now
            if (!running) continue
            locationOverlay.myLocation?.let { engine.onLocation(it.latitude, it.longitude, now) }
            val t = engine.onTick(now, elapsed)
            speed = t.speedKmh; state = t.state; validSec = t.validSec
            continuousSec = t.continuousSec; distance = t.distanceM
            steps = t.steps; source = t.speedSource; graceLeft = t.graceLeftSec
            trackLine.setPoints(engine.points().map { GeoPoint(it.lat, it.lng) })
            mapView.invalidate()

            if (t.grantReady) {
                val sid = sessionId ?: continue
                val q = db.dao().quotaOf(todayKey()) ?: DailyQuotaEntity(todayKey())
                if (q.earnedPoints >= cap) {
                    db.dao().insertEvent(
                        AssetEventEntity(
                            assetUuid = null, kind = "OVERFLOW", at = now, detail = "cap=$cap"
                        )
                    )
                    engine.consumeGrant()
                } else {
                    val rank = wear.rank
                    val pt = rank.rollPoint().coerceAtMost(cap - q.earnedPoints)
                    val here = locationOverlay.myLocation
                    val uuid = UUID.randomUUID().toString()
                    db.dao().insertAsset(
                        AssetEntity(
                            uuid = uuid,
                            rank = rank.name,
                            repairPoint = pt,
                            acquiredAt = now,
                            acquiredLat = here?.latitude ?: 0.0,
                            acquiredLng = here?.longitude ?: 0.0,
                            validSecAtGrant = t.continuousSec,
                            avgSpeedKmh = t.speedKmh,
                            shoeType = shoeType?.name ?: "",
                            wearType = wear.name,
                            speedSource = t.speedSource,
                            sessionId = sid,
                            status = AssetStatus.INTERNAL.name
                        )
                    )
                    db.dao().insertEvent(
                        AssetEventEntity(
                            assetUuid = uuid, kind = "GRANT", at = now,
                            detail = "${rank.name}+$pt"
                        )
                    )
                    db.dao().loadout()?.let { lo ->
                        db.dao().saveLoadout(lo.copy(repairWallet = lo.repairWallet + pt))
                    }
                    db.dao().saveQuota(q.copy(earnedPoints = q.earnedPoints + pt))
                    engine.consumeGrant()
                    Toast.makeText(
                        context, "${rank.label}を獲得（修理ポイント +$pt）", Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .background(Color(0xE6FFFFFF))
                .padding(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(state.icon), null, Modifier.size(22.dp))
                Spacer(Modifier.width(6.dp))
                Text(state.label, fontSize = 14.sp)
            }
            Text(
                SpeedFormat.kmh(speed) + "  " + SpeedFormat.pace(speed) +
                        (if (source == "STEP_ESTIMATE") "  (歩数推定)" else ""),
                fontSize = 13.sp
            )
            shoeType?.let { Text("靴の適正 ${it.rangeLabel}", fontSize = 12.sp) }
            if (state == SpeedState.GRACE) {
                Text("あと${graceLeft}秒で連続が途切れます", fontSize = 13.sp, color = Color(0xFFB00020))
            }
            Text(
                "有効時間 ${SpeedFormat.clock(validSec)} / 連続 ${SpeedFormat.clock(continuousSec)}",
                fontSize = 13.sp
            )
            val need = (wear.thresholdSec - continuousSec).coerceAtLeast(0)
            Text("次の${wear.rank.label}まで ${SpeedFormat.clock(need)}", fontSize = 13.sp)
            LinearProgressIndicator(
                progress = { (continuousSec.toFloat() / wear.thresholdSec).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp)
            )
            Text("本日 $earned / $cap pt", fontSize = 13.sp)
            Text("${distance.toInt()}m ${steps}歩", fontSize = 12.sp)
        }

        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(14.dp),
            horizontalAlignment = Alignment.End
        ) {
            Button(onClick = {
                scope.launch {
                    if (!running) {
                        val s = db.dao().equippedShoe()
                        if (s == null || s.durability <= 0) {
                            Toast.makeText(
                                context, "使える靴がありません。装備画面で修理してください", Toast.LENGTH_LONG
                            ).show()
                            return@launch
                        }
                        engine.start(ShoeType.valueOf(s.shoeType), wear, Balance.DEFAULT_STRIDE_M)
                        sessionId = db.dao().insertSession(
                            WalkSessionEntity(
                                startAt = System.currentTimeMillis(),
                                shoeType = s.shoeType,
                                wearType = wear.name
                            )
                        )
                        running = true
                        Toast.makeText(
                            context,
                            if (engine.hasStepSensor()) "記録を開始しました" else "記録を開始（歩数センサーなし）",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        running = false
                        engine.stop()
                        sessionId?.let { sid ->
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
                                    durability = (s.durability - engine.durabilityConsumed)
                                        .coerceAtLeast(0),
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
                        }
                        db.dao().saveQuota(
                            q.copy(validSec = total, achieved = achieved, streakDays = streak)
                        )
                        Toast.makeText(
                            context,
                            "終了: 有効 ${SpeedFormat.clock(engine.validSec)} / 耐久 -${engine.durabilityConsumed}",
                            Toast.LENGTH_LONG
                        ).show()
                        sessionId = null
                    }
                }
            }) { Text(if (running) "ストップ" else "スタート") }

            Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = onEquip) { Text("装備") }
                OutlinedButton(onClick = onBag, modifier = Modifier.padding(start = 6.dp)) { Text("持ち物") }
                OutlinedButton(onClick = onHistory, modifier = Modifier.padding(start = 6.dp)) { Text("履歴") }
            }
        }
    }
}

@Composable
fun EquipScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { WalkDatabase.get(context) }
    val shoes by db.dao().observeShoes().collectAsState(initial = emptyList())
    val loadout by db.dao().observeLoadout().collectAsState(initial = null)
    var repairTarget by remember { mutableStateOf<String?>(null) }
    var repairAmount by remember { mutableStateOf(0f) }

    val wallet = loadout?.repairWallet ?: 0

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack) { Text("戻る") }
            Text("  装備", fontSize = 18.sp, modifier = Modifier.padding(start = 6.dp))
        }
        Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.icon_wallet), null, Modifier.size(24.dp))
            Text("  修理ポイント $wallet", fontSize = 15.sp)
        }

        LazyColumn(modifier = Modifier.padding(top = 10.dp)) {
            item { Text("靴", fontSize = 15.sp, modifier = Modifier.padding(vertical = 6.dp)) }
            items(shoes) { s: ShoeEntity ->
                val t = ShoeType.valueOf(s.shoeType)
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painterResource(t.artFor(s.durability)), null,
                                Modifier.size(84.dp), contentScale = ContentScale.Fit
                            )
                            Column(modifier = Modifier.padding(start = 10.dp)) {
                                Text(t.label + (if (s.equipped) "（装備中）" else ""), fontSize = 15.sp)
                                Text("適正 ${t.rangeLabel}", fontSize = 12.sp)
                                Text("耐久 ${s.durability} / ${Balance.DURABILITY_MAX}", fontSize = 12.sp)
                                LinearProgressIndicator(
                                    progress = { s.durability / 100f },
                                    modifier = Modifier.width(150.dp).height(6.dp)
                                )
                            }
                        }
                        Row(modifier = Modifier.padding(top = 6.dp)) {
                            if (!s.equipped) {
                                OutlinedButton(onClick = {
                                    scope.launch {
                                        if (s.durability <= 0) {
                                            Toast.makeText(
                                                context, "耐久が0の靴は装備できません", Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            db.dao().unequipAll()
                                            db.dao().equip(s.uuid)
                                        }
                                    }
                                }) { Text("履き替える") }
                            }
                            OutlinedButton(
                                onClick = {
                                    repairTarget = if (repairTarget == s.uuid) null else s.uuid
                                    repairAmount = 0f
                                },
                                modifier = Modifier.padding(start = 6.dp)
                            ) { Text("修理") }
                        }
                        if (repairTarget == s.uuid) {
                            val maxAdd = minOf(wallet, Balance.DURABILITY_MAX - s.durability)
                            if (maxAdd <= 0) {
                                Text("修理できません（ポイント不足か耐久が満タン）", fontSize = 12.sp)
                            } else {
                                Text("投入 ${repairAmount.toInt()} / 最大 $maxAdd", fontSize = 13.sp)
                                Slider(
                                    value = repairAmount,
                                    onValueChange = { repairAmount = it },
                                    valueRange = 0f..maxAdd.toFloat()
                                )
                                Button(onClick = {
                                    val amt = repairAmount.toInt()
                                    if (amt > 0) scope.launch {
                                        db.dao().updateShoe(s.copy(durability = s.durability + amt))
                                        db.dao().loadout()?.let { lo ->
                                            db.dao().saveLoadout(
                                                lo.copy(repairWallet = lo.repairWallet - amt)
                                            )
                                        }
                                        db.dao().insertEvent(
                                            AssetEventEntity(
                                                assetUuid = null, kind = "REPAIR",
                                                at = System.currentTimeMillis(),
                                                detail = "${t.name}+$amt"
                                            )
                                        )
                                        repairTarget = null
                                    }
                                }) { Text("修理する") }
                            }
                        }
                    }
                }
            }

            item {
                Text("トレーニングウェア", fontSize = 15.sp, modifier = Modifier.padding(vertical = 6.dp))
            }
            items(WearType.values().toList()) { w ->
                val girl = loadout?.avatarGirl ?: false
                val current = loadout?.wearType == w.name
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painterResource(w.art(girl)), null,
                            Modifier.size(96.dp), contentScale = ContentScale.Fit
                        )
                        Column(modifier = Modifier.padding(start = 10.dp)) {
                            Text(w.label + (if (current) "（着用中）" else ""), fontSize = 15.sp)
                            Text("${w.thresholdMin}分ごとに ${w.rank.label}", fontSize = 12.sp)
                            Text("修理 ${w.rank.minPoint}〜${w.rank.maxPoint}pt", fontSize = 12.sp)
                            if (!current) {
                                OutlinedButton(onClick = {
                                    scope.launch {
                                        db.dao().loadout()?.let { lo ->
                                            db.dao().saveLoadout(lo.copy(wearType = w.name))
                                        }
                                    }
                                }) { Text("着替える") }
                            }
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            db.dao().loadout()?.let { lo ->
                                db.dao().saveLoadout(lo.copy(avatarGirl = !lo.avatarGirl))
                            }
                        }
                    },
                    modifier = Modifier.padding(top = 4.dp)
                ) { Text("キャラクターを切り替える") }
            }
        }
    }
}

@Composable
fun BagScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { WalkDatabase.get(context) }
    val assets by db.dao().observeAssets().collectAsState(initial = emptyList())
    val fmt = remember { SimpleDateFormat("MM/dd HH:mm", Locale.JAPAN) }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack) { Text("戻る") }
            Text("  持ち物 ${assets.size}個", fontSize = 18.sp, modifier = Modifier.padding(start = 6.dp))
        }
        OutlinedButton(
            onClick = {
                scope.launch {
                    val json = AssetMetadata.exportAll(db.dao().allAssets())
                    val i = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_SUBJECT, "WalkNApp assets")
                        putExtra(Intent.EXTRA_TEXT, json)
                    }
                    context.startActivity(Intent.createChooser(i, "資産データを書き出す"))
                }
            },
            modifier = Modifier.padding(top = 6.dp)
        ) { Text("資産データを書き出す (JSON)") }

        if (assets.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painterResource(R.drawable.state_empty_boy), null,
                        Modifier.size(220.dp), contentScale = ContentScale.Fit
                    )
                    Text("まだ何も手に入れていません")
                }
            }
        } else {
            LazyColumn(modifier = Modifier.padding(top = 10.dp)) {
                items(assets) { a: AssetEntity ->
                    val rank = runCatching { ItemRank.valueOf(a.rank) }.getOrNull()
                    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            rank?.let {
                                Image(
                                    painterResource(it.drawable), null,
                                    Modifier.size(64.dp), contentScale = ContentScale.Fit
                                )
                            }
                            Column(modifier = Modifier.padding(start = 10.dp)) {
                                Text(
                                    "${rank?.label ?: a.rank}  修理 +${a.repairPoint}pt",
                                    fontSize = 15.sp
                                )
                                Text(fmt.format(Date(a.acquiredAt)), fontSize = 12.sp)
                                Text(
                                    "連続 ${SpeedFormat.clock(a.validSecAtGrant)} / ${SpeedFormat.kmh(a.avgSpeedKmh)}",
                                    fontSize = 12.sp
                                )
                                Text("${a.shoeType} / ${a.wearType} / ${a.speedSource}", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { WalkDatabase.get(context) }
    val sessions by db.dao().observeSessions().collectAsState(initial = emptyList())
    val quota by db.dao().observeQuota(todayKey()).collectAsState(initial = null)
    val fmt = remember { SimpleDateFormat("MM/dd HH:mm", Locale.JAPAN) }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onBack) { Text("戻る") }
            Text("  履歴 ${sessions.size}件", fontSize = 18.sp, modifier = Modifier.padding(start = 6.dp))
        }
        Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(painterResource(R.drawable.icon_streak), null, Modifier.size(30.dp))
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text("連続 ${quota?.streakDays ?: 0}日", fontSize = 15.sp)
                    Text(
                        "本日の有効時間 ${SpeedFormat.clock(quota?.validSec ?: 0)} / 目標 60:00",
                        fontSize = 12.sp
                    )
                }
            }
        }
        if (sessions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("まだ記録がありません")
            }
        } else {
            LazyColumn(modifier = Modifier.padding(top = 10.dp)) {
                items(sessions) { s: WalkSessionEntity ->
                    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(fmt.format(Date(s.startAt)), fontSize = 15.sp)
                            Text(
                                if (s.endAt == null) "記録中"
                                else "有効 ${SpeedFormat.clock(s.validSec)} / ${s.distanceM.toInt()}m / ${s.steps}歩",
                                fontSize = 13.sp
                            )
                            Text(
                                "耐久 -${s.durabilityUsed} / ${s.shoeType} / ${s.wearType}",
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
