package com.appathy.walknapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.preference.PreferenceManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import android.content.Intent
import com.appathy.walknapp.data.AcquiredSpawnEntity
import com.appathy.walknapp.data.AssetEntity
import com.appathy.walknapp.data.AssetEventEntity
import com.appathy.walknapp.data.AssetMetadata
import com.appathy.walknapp.data.AssetStatus
import com.appathy.walknapp.data.WalkDatabase
import java.util.UUID
import com.appathy.walknapp.data.CollectedCount
import com.appathy.walknapp.data.WalkSessionEntity
import com.appathy.walknapp.session.SessionTracker
import com.appathy.walknapp.session.SpeedFormat
import com.appathy.walknapp.spawn.ItemCatalog
import com.appathy.walknapp.spawn.Rarity
import com.appathy.walknapp.spawn.SpawnEngine
import com.appathy.walknapp.spawn.SpawnPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val PICKUP_RADIUS_M = 30.0

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ctx = applicationContext
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))
        Configuration.getInstance().userAgentValue = packageName
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RootScreen()
                }
            }
        }
    }
}

@Composable
fun RootScreen() {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermission = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }
    var screen by remember { mutableStateOf("map") }

    if (!hasPermission) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(onClick = {
                launcher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }) {
                Text("位置情報を許可して開始")
            }
        }
        return
    }

    when (screen) {
        "inventory" -> InventoryScreen(onBack = { screen = "map" })
        "history" -> HistoryScreen(onBack = { screen = "map" })
        "zukan" -> ZukanScreen(onBack = { screen = "map" })
        else -> MapContent(
            onOpenInventory = { screen = "inventory" },
            onOpenHistory = { screen = "history" },
            onOpenZukan = { screen = "zukan" }
        )
    }
}

private fun rarityIcon(mapView: MapView, colorInt: Int): Drawable? {
    val base = Marker(mapView).icon ?: return null
    val d = base.constantState?.newDrawable()?.mutate() ?: base.mutate()
    d.setColorFilter(colorInt, PorterDuff.Mode.SRC_IN)
    return d
}

@Composable
fun MapContent(
    onOpenInventory: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenZukan: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { WalkDatabase.get(context) }
    val itemCount by db.dao().observeAssetCount().collectAsState(initial = 0)

    var spawnCount by remember { mutableStateOf(0) }
    var nearestText by remember { mutableStateOf("現在地を取得中…") }
    var refreshKey by remember { mutableStateOf(0) }

    val tracker = remember { SessionTracker(context) }
    var sessionId by remember { mutableStateOf<Long?>(null) }
    var sessionSteps by remember { mutableStateOf(0) }
    var sessionDistance by remember { mutableStateOf(0.0) }
    var bonusLevel by remember { mutableStateOf(0) }
    var currentSpeed by remember { mutableStateOf(0.0) }
    var avgSpeed by remember { mutableStateOf(0.0) }

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
            enableMyLocation()
            enableFollowLocation()
        }
    }

    val trackLine = remember {
        Polyline().apply {
            outlinePaint.color = 0xFF1E88E5.toInt()
            outlinePaint.strokeWidth = 8f
        }
    }

    LaunchedEffect(refreshKey) {
        if (!mapView.overlays.contains(locationOverlay)) {
            mapView.overlays.add(locationOverlay)
        }
        if (!mapView.overlays.contains(trackLine)) {
            mapView.overlays.add(0, trackLine)
        }
        var lastCell = ""
        var lastBonus = -1
        var shown = listOf<SpawnPoint>()
        while (true) {
            val loc = locationOverlay.myLocation
            if (loc != null) {
                val cellKey =
                    "${SpawnEngine.cellXOf(loc.longitude)}:${SpawnEngine.cellYOf(loc.latitude)}"
                if (cellKey != lastCell || bonusLevel != lastBonus) {
                    lastCell = cellKey
                    lastBonus = bonusLevel
                    val acquired = db.dao().acquiredSpawnIds().toSet()
                    shown = SpawnEngine.spawnsAround(
                        loc.latitude, loc.longitude, bonus = bonusLevel
                    ).filter { it.id !in acquired }
                    mapView.overlays.removeAll { it is Marker }
                    shown.forEach { sp ->
                        val m = Marker(mapView)
                        m.position = GeoPoint(sp.lat, sp.lng)
                        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        rarityIcon(mapView, sp.itemDef.rarity.colorHex)?.let { m.icon = it }
                        m.title = sp.itemDef.name
                        m.infoWindow = null
                        m.setOnMarkerClickListener { marker, _ ->
                            val here = locationOverlay.myLocation
                            if (here == null) {
                                Toast.makeText(context, "現在地が未取得です", Toast.LENGTH_SHORT).show()
                                return@setOnMarkerClickListener true
                            }
                            val d = SpawnEngine.distanceMeters(
                                here.latitude, here.longitude, sp.lat, sp.lng
                            )
                            if (d > PICKUP_RADIUS_M) {
                                Toast.makeText(
                                    context,
                                    "${sp.itemDef.name} まで ${d.toInt()}m（${PICKUP_RADIUS_M.toInt()}m以内で拾えます）",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                scope.launch {
                                    val now = System.currentTimeMillis()
                                    val uuid = UUID.randomUUID().toString()
                                    db.dao().insertAsset(
                                        AssetEntity(
                                            uuid = uuid,
                                            itemDefId = sp.itemDef.id,
                                            collectionId = sp.itemDef.collectionId,
                                            acquiredAt = now,
                                            acquiredLat = here.latitude,
                                            acquiredLng = here.longitude,
                                            spawnId = sp.id,
                                            acquiredSteps = tracker.steps,
                                            status = AssetStatus.INTERNAL.name
                                        )
                                    )
                                    db.dao().insertAcquiredSpawn(
                                        AcquiredSpawnEntity(sp.id, now)
                                    )
                                    db.dao().insertEvent(
                                        AssetEventEntity(
                                            assetUuid = uuid,
                                            kind = "ACQUIRE",
                                            at = now,
                                            detail = sp.id
                                        )
                                    )
                                    tracker.countItem()
                                    mapView.overlays.remove(marker)
                                    mapView.invalidate()
                                    spawnCount = (spawnCount - 1).coerceAtLeast(0)
                                    Toast.makeText(
                                        context,
                                        "${sp.itemDef.name} を手に入れた！（${sp.itemDef.rarity.label}）",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            true
                        }
                        mapView.overlays.add(m)
                    }
                    spawnCount = shown.size
                    mapView.invalidate()
                }
                if (sessionId != null) {
                    tracker.onLocation(loc.latitude, loc.longitude, System.currentTimeMillis())
                    sessionSteps = tracker.steps
                    sessionDistance = tracker.distanceM
                    bonusLevel = when {
                        sessionSteps >= 10000 -> 3
                        sessionSteps >= 6000 -> 2
                        sessionSteps >= 3000 -> 1
                        else -> 0
                    }
                    val now = System.currentTimeMillis()
                    tracker.decayIfIdle(now)
                    currentSpeed = tracker.currentSpeedMps
                    avgSpeed = tracker.averageSpeedMps(now)
                    trackLine.setPoints(
                        tracker.points().map { GeoPoint(it.lat, it.lng) }
                    )
                    mapView.invalidate()
                }
                val nearest = shown.minByOrNull {
                    SpawnEngine.distanceMeters(loc.latitude, loc.longitude, it.lat, it.lng)
                }
                nearestText = if (nearest == null) {
                    "周辺にアイテムなし"
                } else {
                    val d = SpawnEngine.distanceMeters(
                        loc.latitude, loc.longitude, nearest.lat, nearest.lng
                    ).toInt()
                    if (d <= PICKUP_RADIUS_M) {
                        "拾えます: ${nearest.itemDef.name} ${d}m"
                    } else {
                        "最寄り: ${nearest.itemDef.name} ${d}m"
                    }
                }
            }
            delay(2000)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .background(Color(0xCCFFFFFF))
                .padding(10.dp)
        ) {
            Text("周辺アイテム: ${spawnCount}個", fontSize = 14.sp)
            Text(nearestText, fontSize = 14.sp)
            Text("所持: ${itemCount}個", fontSize = 14.sp)
            if (sessionId != null) {
                Text(
                    "記録中: ${sessionSteps}歩 / ${sessionDistance.toInt()}m",
                    fontSize = 14.sp
                )
                Text(
                    "速度 ${SpeedFormat.kmh(currentSpeed)} (${SpeedFormat.label(currentSpeed)})",
                    fontSize = 14.sp
                )
                Text(
                    "ペース ${SpeedFormat.pace(currentSpeed)} / 平均 ${SpeedFormat.kmh(avgSpeed)}",
                    fontSize = 14.sp
                )
                if (bonusLevel > 0) {
                    Text("歩数ボーナス +${bonusLevel}", fontSize = 14.sp)
                }
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalAlignment = Alignment.End
        ) {
            Button(
                onClick = {
                    scope.launch {
                        val current = sessionId
                        if (current == null) {
                            tracker.start()
                            val now = System.currentTimeMillis()
                            val id = db.dao().insertSession(WalkSessionEntity(startAt = now))
                            sessionId = id
                            sessionSteps = 0
                            sessionDistance = 0.0
                            val msg = if (tracker.hasStepSensor()) {
                                "記録を開始しました"
                            } else {
                                "記録を開始（歩数センサーなし・距離のみ）"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        } else {
                            val saved = db.dao().sessionById(current)
                            if (saved != null) {
                                db.dao().updateSession(
                                    saved.copy(
                                        endAt = System.currentTimeMillis(),
                                        steps = tracker.steps,
                                        distanceM = tracker.distanceM,
                                        trackJson = tracker.trackJson(),
                                        invalidSegments = tracker.invalidSegments,
                                        itemCount = tracker.itemCount
                                    )
                                )
                            }
                            tracker.stop()
                            Toast.makeText(
                                context,
                                "記録を終了: ${tracker.steps}歩 / ${tracker.distanceM.toInt()}m / ${tracker.itemCount}個",
                                Toast.LENGTH_LONG
                            ).show()
                            sessionId = null
                        }
                    }
                }
            ) { Text(if (sessionId == null) "記録開始" else "記録終了") }
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Button(onClick = onOpenZukan) { Text("図鑑") }
                Button(
                    onClick = onOpenHistory,
                    modifier = Modifier.padding(start = 8.dp)
                ) { Text("履歴") }
                Button(
                    onClick = onOpenInventory,
                    modifier = Modifier.padding(start = 8.dp)
                ) { Text("持ち物") }
                Button(
                    onClick = {
                        locationOverlay.enableFollowLocation()
                        locationOverlay.myLocation?.let { mapView.controller.animateTo(it) }
                    },
                    modifier = Modifier.padding(start = 8.dp)
                ) { Text("現在地") }
            }
        }
    }
}

@Composable
fun InventoryScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { WalkDatabase.get(context) }
    val assets by db.dao().observeAssets().collectAsState(initial = emptyList())
    val fmt = remember { SimpleDateFormat("MM/dd HH:mm", Locale.JAPAN) }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onBack) { Text("戻る") }
            Text(
                "  持ち物 ${assets.size}個",
                fontSize = 18.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Button(
            onClick = {
                scope.launch {
                    val json = AssetMetadata.exportAll(db.dao().allAssets())
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_SUBJECT, "WalkNApp assets")
                        putExtra(Intent.EXTRA_TEXT, json)
                    }
                    context.startActivity(Intent.createChooser(intent, "資産データを書き出す"))
                }
            },
            modifier = Modifier.padding(top = 8.dp)
        ) { Text("資産データを書き出す (JSON)") }

        if (assets.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("まだ何も拾っていません")
            }
        } else {
            LazyColumn(modifier = Modifier.padding(top = 12.dp)) {
                items(assets) { asset: AssetEntity ->
                    val def = ItemCatalog.byId(asset.itemDefId)
                    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "${def?.name ?: asset.itemDefId}  [${def?.rarity?.label ?: "-"}]",
                                fontSize = 16.sp
                            )
                            Text("状態: ${asset.status}", fontSize = 12.sp)
                            Text(
                                "取得: ${fmt.format(Date(asset.acquiredAt))}",
                                fontSize = 12.sp
                            )
                            Text(
                                "地点: ${"%.5f".format(asset.acquiredLat)}, ${"%.5f".format(asset.acquiredLng)}",
                                fontSize = 12.sp
                            )
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
    val fmt = remember { SimpleDateFormat("MM/dd HH:mm", Locale.JAPAN) }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onBack) { Text("戻る") }
            Text("  履歴 ${sessions.size}件", fontSize = 18.sp, modifier = Modifier.padding(start = 8.dp))
        }
        if (sessions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("まだ記録がありません")
            }
        } else {
            LazyColumn(modifier = Modifier.padding(top = 12.dp)) {
                items(sessions) { s: WalkSessionEntity ->
                    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(fmt.format(Date(s.startAt)), fontSize = 16.sp)
                            Text(
                                if (s.endAt == null) "記録中" else "${s.steps}歩 / ${s.distanceM.toInt()}m / ${s.itemCount}個",
                                fontSize = 14.sp
                            )
                            if (s.endAt != null) {
                                val sec = (s.endAt - s.startAt) / 1000.0
                                val avg = if (sec > 0) s.distanceM / sec else 0.0
                                val min = (sec / 60).toInt()
                                Text(
                                    "${min}分 / 平均 ${SpeedFormat.kmh(avg)} / ${SpeedFormat.pace(avg)}",
                                    fontSize = 12.sp
                                )
                            }
                            if (s.invalidSegments > 0) {
                                Text("除外区間: ${s.invalidSegments}", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ZukanScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { WalkDatabase.get(context) }
    val collected by db.dao().observeCollected().collectAsState(initial = emptyList())
    val totalSteps by db.dao().observeTotalSteps().collectAsState(initial = 0)
    val totalDistance by db.dao().observeTotalDistance().collectAsState(initial = 0.0)

    val counts: Map<String, Int> = collected.associate { c: CollectedCount -> c.itemDefId to c.count }
    val discovered = ItemCatalog.all.count { counts.containsKey(it.id) }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onBack) { Text("戻る") }
            Text(
                "  図鑑 ${discovered}/${ItemCatalog.all.size}",
                fontSize = 18.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("総歩数 ${totalSteps}歩 / 総距離 ${totalDistance.toInt()}m", fontSize = 14.sp)
                val byRarity = Rarity.values().joinToString("  ") { r ->
                    val n = ItemCatalog.all.filter { it.rarity == r }.sumOf { counts[it.id] ?: 0 }
                    "${r.label}:${n}"
                }
                Text(byRarity, fontSize = 12.sp)
            }
        }
        LazyColumn(modifier = Modifier.padding(top = 12.dp)) {
            items(ItemCatalog.all) { def ->
                val n = counts[def.id] ?: 0
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (n > 0) {
                            Text("${def.name}  [${def.rarity.label}]  x${n}", fontSize = 16.sp)
                            Text(
                                "分類: ${def.category.name} / NFT化: ${def.mintPolicy.name}",
                                fontSize = 12.sp
                            )
                            if (def.capabilities.isNotEmpty()) {
                                Text("用途: ${def.capabilities.joinToString(", ")}", fontSize = 12.sp)
                            }
                        } else {
                            Text("？？？  [${def.rarity.label}]", fontSize = 16.sp)
                            Text("未発見", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
