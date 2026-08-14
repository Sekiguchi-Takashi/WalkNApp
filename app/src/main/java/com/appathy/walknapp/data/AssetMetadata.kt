package com.appathy.walknapp.data

import com.appathy.walknapp.spawn.Collections
import com.appathy.walknapp.spawn.ItemCatalog
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
        val def = ItemCatalog.byId(asset.itemDefId)
        val col = Collections.byId(asset.collectionId)
        val attrs = JSONArray()
        attrs.put(JSONObject().put("trait_type", "rarity").put("value", def?.rarity?.name ?: "UNKNOWN"))
        attrs.put(JSONObject().put("trait_type", "category").put("value", def?.category?.name ?: "UNKNOWN"))
        attrs.put(JSONObject().put("trait_type", "acquired_at").put("value", iso(asset.acquiredAt)))
        attrs.put(JSONObject().put("trait_type", "acquired_lat").put("value", round(asset.acquiredLat)))
        attrs.put(JSONObject().put("trait_type", "acquired_lng").put("value", round(asset.acquiredLng)))
        attrs.put(JSONObject().put("trait_type", "acquired_steps").put("value", asset.acquiredSteps))

        val caps = JSONArray()
        def?.capabilities?.forEach { caps.put(it) }

        return JSONObject()
            .put("name", def?.name ?: asset.itemDefId)
            .put("symbol", col?.symbol ?: "WKN")
            .put("description", "WalkNApp で歩いて発見した資産")
            .put("image", "")
            .put("attributes", attrs)
            .put(
                "properties",
                JSONObject()
                    .put("category", "image")
                    .put("collection", JSONObject().put("name", col?.name ?: "").put("family", "WalkN"))
            )
            .put(
                "walkn",
                JSONObject()
                    .put("asset_uuid", asset.uuid)
                    .put("item_def_id", asset.itemDefId)
                    .put("collection_id", asset.collectionId)
                    .put("status", asset.status)
                    .put("mint_policy", def?.mintPolicy?.name ?: "NEVER")
                    .put("transferable", def?.transferable ?: false)
                    .put("capabilities", caps)
                    .put("source_app", "WalkNApp")
                    .put("schema_version", 1)
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
