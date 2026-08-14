package com.appathy.walknapp.spawn

enum class Rarity(val label: String, val weight: Int, val colorHex: Int) {
    COMMON("コモン", 60, 0xFF9E9E9E.toInt()),
    UNCOMMON("アンコモン", 25, 0xFF4CAF50.toInt()),
    RARE("レア", 10, 0xFF2196F3.toInt()),
    EPIC("エピック", 4, 0xFF9C27B0.toInt()),
    LEGENDARY("レジェンダリー", 1, 0xFFFF9800.toInt())
}

enum class ItemCategory { DECORATION, MATERIAL, TICKET, KEY }

data class ItemDefinition(
    val id: String,
    val name: String,
    val category: ItemCategory,
    val rarity: Rarity,
    val capabilities: List<String> = emptyList()
)

object ItemCatalog {
    val all: List<ItemDefinition> = listOf(
        ItemDefinition("stone", "石ころ", ItemCategory.MATERIAL, Rarity.COMMON),
        ItemDefinition("twig", "小枝", ItemCategory.MATERIAL, Rarity.COMMON),
        ItemDefinition("leaf", "葉っぱ", ItemCategory.MATERIAL, Rarity.COMMON),
        ItemDefinition("acorn", "どんぐり", ItemCategory.MATERIAL, Rarity.COMMON),
        ItemDefinition("feather", "羽根", ItemCategory.DECORATION, Rarity.COMMON),
        ItemDefinition("shell", "貝がら", ItemCategory.DECORATION, Rarity.UNCOMMON),
        ItemDefinition("copper", "銅片", ItemCategory.MATERIAL, Rarity.UNCOMMON),
        ItemDefinition("clover", "四つ葉", ItemCategory.DECORATION, Rarity.UNCOMMON),
        ItemDefinition("bead", "ガラス玉", ItemCategory.DECORATION, Rarity.UNCOMMON),
        ItemDefinition("ticket_a", "散歩チケット", ItemCategory.TICKET, Rarity.RARE),
        ItemDefinition("silver", "銀片", ItemCategory.MATERIAL, Rarity.RARE),
        ItemDefinition("compass", "古い方位磁針", ItemCategory.DECORATION, Rarity.RARE),
        ItemDefinition("gear", "歯車", ItemCategory.MATERIAL, Rarity.RARE),
        ItemDefinition("gold", "金片", ItemCategory.MATERIAL, Rarity.EPIC),
        ItemDefinition("crystal", "水晶", ItemCategory.DECORATION, Rarity.EPIC),
        ItemDefinition("key_a", "錆びた鍵", ItemCategory.KEY, Rarity.EPIC),
        ItemDefinition("starfrag", "星のかけら", ItemCategory.DECORATION, Rarity.LEGENDARY),
        ItemDefinition("key_omega", "真鍮の鍵", ItemCategory.KEY, Rarity.LEGENDARY)
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
