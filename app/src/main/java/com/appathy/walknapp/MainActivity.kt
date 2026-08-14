package com.appathy.walknapp

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.Bitmap
import android.os.Bundle
import android.preference.PreferenceManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.appathy.walknapp.spawn.SpawnEngine
import com.appathy.walknapp.spawn.SpawnPoint
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ctx = applicationContext
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx))
        Configuration.getInstance().userAgentValue = packageName
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WalkMapScreen()
                }
            }
        }
    }
}

@Composable
fun WalkMapScreen() {
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

    if (hasPermission) {
        MapContent()
    } else {
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
    }
}

private fun dotDrawable(colorInt: Int): BitmapDrawable {
    val size = 48
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = 0x66000000
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2f, paint)
    paint.color = colorInt
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 7f, paint)
    return BitmapDrawable(null, bmp)
}

@Composable
fun MapContent() {
    val context = LocalContext.current
    var spawnCount by remember { mutableStateOf(0) }
    var nearestText by remember { mutableStateOf("現在地を取得中…") }

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

    LaunchedEffect(Unit) {
        if (!mapView.overlays.contains(locationOverlay)) {
            mapView.overlays.add(locationOverlay)
        }
        var lastCell = ""
        var shown = listOf<SpawnPoint>()
        while (true) {
            val loc = locationOverlay.myLocation
            if (loc != null) {
                val cellKey = "${SpawnEngine.cellXOf(loc.longitude)}:${SpawnEngine.cellYOf(loc.latitude)}"
                if (cellKey != lastCell) {
                    lastCell = cellKey
                    shown = SpawnEngine.spawnsAround(loc.latitude, loc.longitude)
                    mapView.overlays.removeAll { it is Marker }
                    shown.forEach { sp ->
                        val m = Marker(mapView)
                        m.position = GeoPoint(sp.lat, sp.lng)
                        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        m.icon = dotDrawable(sp.itemDef.rarity.colorHex)
                        m.title = "${sp.itemDef.name} (${sp.itemDef.rarity.label})"
                        m.setOnMarkerClickListener { _, _ ->
                            val d = SpawnEngine.distanceMeters(
                                loc.latitude, loc.longitude, sp.lat, sp.lng
                            )
                            Toast.makeText(
                                context,
                                "${sp.itemDef.name} / ${sp.itemDef.rarity.label} / ${d.toInt()}m",
                                Toast.LENGTH_SHORT
                            ).show()
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
                    "最寄り: ${nearest.itemDef.name} ${d}m"
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
        }
        Button(
            onClick = {
                locationOverlay.enableFollowLocation()
                locationOverlay.myLocation?.let { mapView.controller.animateTo(it) }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Text("現在地")
        }
    }
}
