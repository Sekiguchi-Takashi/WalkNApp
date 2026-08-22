package com.appathy.walknapp.game

import com.appathy.walknapp.R

enum class ItemRank(val label: String, val drawable: Int, val minPoint: Int, val maxPoint: Int) {
    LOW("低ランク", R.drawable.item_low, 1, 1),
    MID("中ランク", R.drawable.item_mid, 2, 5),
    HIGH("高ランク", R.drawable.item_high, 5, 10);

    fun rollPoint(): Int =
        if (minPoint >= maxPoint) minPoint
        else (minPoint..maxPoint).random()
}

enum class ShoeType(
    val label: String,
    val minKmh: Double,
    val maxKmh: Double,
    val drawable: Int,
    val wornDrawable: Int
) {
    STROLLER("ストローラー", 2.0, 4.5, R.drawable.shoe_stroller, R.drawable.shoe_stroller_worn),
    WALKER("ウォーカー", 4.0, 7.0, R.drawable.shoe_walker, R.drawable.shoe_walker_worn),
    SPEEDWALKER("スピードウォーカー", 6.0, 9.5, R.drawable.shoe_speedwalker, R.drawable.shoe_speedwalker_worn);

    val rangeLabel: String get() = "${minKmh} 〜 ${maxKmh} km/h"

    fun contains(kmh: Double): Boolean = kmh >= minKmh && kmh <= maxKmh

    fun artFor(durability: Int): Int = when {
        durability <= 0 -> R.drawable.shoe_broken
        durability <= 40 -> wornDrawable
        else -> drawable
    }
}

enum class WearType(
    val label: String,
    val thresholdMin: Int,
    val rank: ItemRank,
    val boyArt: Int,
    val girlArt: Int
) {
    LIGHT("軽装", 5, ItemRank.LOW, R.drawable.wear_light_boy, R.drawable.wear_light_girl),
    STANDARD("標準", 15, ItemRank.MID, R.drawable.wear_standard_boy, R.drawable.wear_standard_girl),
    SERIOUS("本格", 30, ItemRank.HIGH, R.drawable.wear_serious_boy, R.drawable.wear_serious_girl);

    val thresholdSec: Long get() = thresholdMin * 60L

    fun art(girl: Boolean): Int = if (girl) girlArt else boyArt
}

enum class SpeedState(val label: String, val icon: Int) {
    TOO_SLOW("もう少し速く", R.drawable.icon_speed_slow),
    OPTIMAL("ちょうどいい速さ", R.drawable.icon_speed_ok),
    TOO_FAST("速すぎます", R.drawable.icon_speed_fast),
    GRACE("速度を戻してください", R.drawable.icon_speed_slow),
    INDOOR("屋外のウォーキングが対象です", R.drawable.icon_indoor),
    IDLE("記録していません", R.drawable.icon_speed_slow)
}

object Balance {
    const val DAILY_CAP_BASE = 18
    const val STREAK_BONUS_PER_DAY = 2
    const val STREAK_BONUS_MAX = 10
    const val STREAK_GOAL_SEC = 60 * 60L

    const val DURABILITY_MAX = 100
    const val SEC_PER_DURABILITY = 5 * 60L

    const val GRACE_SEC = 60L
    const val SPEED_WINDOW_SEC = 30L

    const val INDOOR_WINDOW_SEC = 5 * 60L
    const val INDOOR_MIN_MOVE_M = 50.0
    const val STEP_ESTIMATE_MAX_SEC = 10 * 60L
    const val DEFAULT_STRIDE_M = 0.70
}
