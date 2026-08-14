package com.appathy.walknapp.spawn

enum class Rarity(val label: String, val weight: Int, val colorHex: Int) {
    COMMON("コモン", 60, 0xFF9E9E9E.toInt()),
    UNCOMMON("アンコモン", 25, 0xFF4CAF50.toInt()),
    RARE("レア", 10, 0xFF2196F3.toInt()),
    EPIC("エピック", 4, 0xFF9C27B0.toInt()),
    LEGENDARY("レジェンダリー", 1, 0xFFFF9800.toInt())
}

enum class ItemCategory { DECORATION, MATERIAL, TICKET, KEY }

enum class MintPolicy { NEVER, ON_DEMAND, AUTO }

data class Collection(
    val id: String,
    val name: String,
    val symbol: String
)

object Collections {
    val WALK_BASIC = Collection("walkn.basic", "WalkN 基本素材", "WKB")
    val WALK_TREASURE = Collection("walkn.treasure", "WalkN 宝物", "WKT")
    val all = listOf(WALK_BASIC, WALK_TREASURE)
    fun byId(id: String): Collection? = all.firstOrNull { it.id == id }
}

data class ItemDefinition(
    val id: String,
    val name: String,
    val category: ItemCategory,
    val rarity: Rarity,
    val collectionId: String,
    val transferable: Boolean,
    val mintPolicy: MintPolicy,
    val capabilities: List<String> = emptyList()
)

object ItemCatalog {

    private fun basic(
        id: String, name: String, cat: ItemCategory, rarity: Rarity,
        caps: List<String> = emptyList()
    ) = ItemDefinition(
        id, name, cat, rarity,
        collectionId = Collections.WALK_BASIC.id,
        transferable = true,
        mintPolicy = MintPolicy.NEVER,
        capabilities = caps
    )

    private fun treasure(
        id: String, name: String, cat: ItemCategory, rarity: Rarity,
        policy: MintPolicy, caps: List<String> = emptyList()
    ) = ItemDefinition(
        id, name, cat, rarity,
        collectionId = Collections.WALK_TREASURE.id,
        transferable = true,
        mintPolicy = policy,
        capabilities = caps
    )

    val all: List<ItemDefinition> = listOf(
        basic("stone", "石ころ", ItemCategory.MATERIAL, Rarity.COMMON, listOf("craft.material")),
        basic("twig", "小枝", ItemCategory.MATERIAL, Rarity.COMMON, listOf("craft.material")),
        basic("leaf", "葉っぱ", ItemCategory.MATERIAL, Rarity.COMMON, listOf("craft.material")),
        basic("acorn", "どんぐり", ItemCategory.MATERIAL, Rarity.COMMON, listOf("craft.material")),
        basic("feather", "羽根", ItemCategory.DECORATION, Rarity.COMMON),
        basic("shell", "貝がら", ItemCategory.DECORATION, Rarity.UNCOMMON),
        basic("copper", "銅片", ItemCategory.MATERIAL, Rarity.UNCOMMON, listOf("craft.material")),
        basic("clover", "四つ葉", ItemCategory.DECORATION, Rarity.UNCOMMON),
        basic("bead", "ガラス玉", ItemCategory.DECORATION, Rarity.UNCOMMON),
        treasure("ticket_a", "散歩チケット", ItemCategory.TICKET, Rarity.RARE, MintPolicy.ON_DEMAND, listOf("event.entry")),
        treasure("silver", "銀片", ItemCategory.MATERIAL, Rarity.RARE, MintPolicy.ON_DEMAND, listOf("craft.material", "rpg.currency")),
        treasure("compass", "古い方位磁針", ItemCategory.DECORATION, Rarity.RARE, MintPolicy.ON_DEMAND, listOf("rpg.equipment")),
        treasure("gear", "歯車", ItemCategory.MATERIAL, Rarity.RARE, MintPolicy.ON_DEMAND, listOf("craft.material")),
        treasure("gold", "金片", ItemCategory.MATERIAL, Rarity.EPIC, MintPolicy.ON_DEMAND, listOf("craft.material", "rpg.currency")),
        treasure("crystal", "水晶", ItemCategory.DECORATION, Rarity.EPIC, MintPolicy.ON_DEMAND, listOf("rpg.equipment")),
        treasure("key_a", "錆びた鍵", ItemCategory.KEY, Rarity.EPIC, MintPolicy.ON_DEMAND, listOf("rpg.unlock")),
        treasure("starfrag", "星のかけら", ItemCategory.DECORATION, Rarity.LEGENDARY, MintPolicy.AUTO, listOf("rpg.equipment", "market.listable")),
        treasure("key_omega", "真鍮の鍵", ItemCategory.KEY, Rarity.LEGENDARY, MintPolicy.AUTO, listOf("rpg.unlock", "market.listable"))
    )

    fun byId(id: String): ItemDefinition? = all.firstOrNull { it.id == id }

    fun pickByRarity(rnd: java.util.Random): ItemDefinition {
        val total = Rarity.values().sumOf { it.weight }
        var roll = rnd.nextInt(total)
        var picked = Rarity.COMMON
        for (r in Rarity.values()) {
            if (roll < r.weight) { picked = r; break }
            roll -= r.weight
        }
        val pool = all.filter { it.rarity == picked }
        return pool[rnd.nextInt(pool.size)]
    }
}
