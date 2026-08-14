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
import com.appathy.walknapp.spawn.ItemCatalog
import com.appathy.walknapp.spawn.SpawnEngine
import com.appathy.walknapp.spawn.SpawnPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
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
    var showInventory by remember { mutableStateOf(false) }

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

    if (showInventory) {
        InventoryScreen(onBack = { showInventory = false })
    } else {
        MapContent(onOpenInventory = { showInventory = true })
    }
}

private fun rarityIcon(mapView: MapView, colorInt: Int): Drawable? {
    val base = Marker(mapView).icon ?: return null
    val d = base.constantState?.newDrawable()?.mutate() ?: base.mutate()
    d.setColorFilter(colorInt, PorterDuff.Mode.SRC_IN)
    return d
}

@Composable
fun MapContent(onOpenInventory: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { WalkDatabase.get(context) }
    val itemCount by db.dao().observeAssetCount().collectAsState(initial = 0)

    var spawnCount by remember { mutableStateOf(0) }
    var nearestText by remember { mutableStateOf("現在地を取得中…") }
    var refreshKey by remember { mutableStateOf(0) }

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

    LaunchedEffect(refreshKey) {
        if (!mapView.overlays.contains(locationOverlay)) {
            mapView.overlays.add(locationOverlay)
        }
        var lastCell = ""
        var shown = listOf<SpawnPoint>()
        while (true) {
            val loc = locationOverlay.myLocation
            if (loc != null) {
                val cellKey =
                    "${SpawnEngine.cellXOf(loc.longitude)}:${SpawnEngine.cellYOf(loc.latitude)}"
                if (cellKey != lastCell) {
                    lastCell = cellKey
                    val acquired = db.dao().acquiredSpawnIds().toSet()
                    shown = SpawnEngine.spawnsAround(loc.latitude, loc.longitude)
                        .filter { it.id !in acquired }
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
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Button(onClick = onOpenInventory) { Text("持ち物") }
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
