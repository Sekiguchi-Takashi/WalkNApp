package com.appathy.walknapp.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object AssetMetadata {

    private fun iso(ts: Long): String {
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        return f.format(Date(ts))
    }

    private fun round(v: Double): Double = Math.round(v * 1000.0) / 1000.0

    fun toMetaplexJson(asset: AssetEntity): JSONObject {
        val attrs = JSONArray()
        attrs.put(JSONObject().put("trait_type", "rank").put("value", asset.rank))
        attrs.put(JSONObject().put("trait_type", "repair_point").put("value", asset.repairPoint))
        attrs.put(JSONObject().put("trait_type", "acquired_at").put("value", iso(asset.acquiredAt)))
        attrs.put(JSONObject().put("trait_type", "acquired_lat").put("value", round(asset.acquiredLat)))
        attrs.put(JSONObject().put("trait_type", "acquired_lng").put("value", round(asset.acquiredLng)))
        attrs.put(JSONObject().put("trait_type", "continuous_valid_sec").put("value", asset.validSecAtGrant))
        attrs.put(JSONObject().put("trait_type", "avg_speed_kmh").put("value", round(asset.avgSpeedKmh)))
        attrs.put(JSONObject().put("trait_type", "shoe_type").put("value", asset.shoeType))
        attrs.put(JSONObject().put("trait_type", "wear_type").put("value", asset.wearType))
        attrs.put(JSONObject().put("trait_type", "speed_source").put("value", asset.speedSource))

        return JSONObject()
            .put("name", "WalkN Repair Item (${asset.rank})")
            .put("symbol", "WKN")
            .put("description", "WalkNApp で歩いて獲得した資産")
            .put("image", "")
            .put("attributes", attrs)
            .put(
                "properties",
                JSONObject()
                    .put("category", "image")
                    .put("collection", JSONObject().put("name", "WalkN Repair").put("family", "WalkN"))
            )
            .put(
                "walkn",
                JSONObject()
                    .put("asset_uuid", asset.uuid)
                    .put("status", asset.status)
                    .put("source_app", "WalkNApp")
                    .put("schema_version", 2)
            )
    }

    fun exportAll(assets: List<AssetEntity>): String {
        val arr = JSONArray()
        assets.forEach { arr.put(toMetaplexJson(it)) }
        return JSONObject()
            .put("schema", "universal-asset/v1")
            .put("source_app", "WalkNApp")
            .put("exported_at", iso(System.currentTimeMillis()))
            .put("assets", arr)
            .toString(2)
    }
}
