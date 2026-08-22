package com.appathy.walknapp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appathy.walknapp.data.DailyQuotaEntity
import com.appathy.walknapp.data.RankStat
import com.appathy.walknapp.data.ShoeEntity
import com.appathy.walknapp.data.WalkDatabase
import com.appathy.walknapp.game.Balance
import com.appathy.walknapp.game.ItemRank
import com.appathy.walknapp.game.ShoeType
import com.appathy.walknapp.session.SpeedFormat

@Composable
fun StatsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { WalkDatabase.get(context) }

    val quota by db.dao().observeQuota(todayKey()).collectAsState(initial = null)
    val recent by db.dao().observeRecentQuotas().collectAsState(initial = emptyList())
    val ranks by db.dao().observeRankStats().collectAsState(initial = emptyList())
    val shoes by db.dao().observeShoes().collectAsState(initial = emptyList())
    val loadout by db.dao().observeLoadout().collectAsState(initial = null)
    val totalValid by db.dao().observeTotalValidSec().collectAsState(initial = 0L)
    val totalDistance by db.dao().observeTotalDistance().collectAsState(initial = 0.0)
    val sessionCount by db.dao().observeSessionCount().collectAsState(initial = 0)

    val streak = quota?.streakDays ?: 0
    val bonus = (streak * Balance.STREAK_BONUS_PER_DAY).coerceAtMost(Balance.STREAK_BONUS_MAX)
    val cap = Balance.DAILY_CAP_BASE + bonus
    val todayValid = quota?.validSec ?: 0L
    val earned = quota?.earnedPoints ?: 0

    LazyColumn(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onBack) { Text("戻る") }
                Text("  記録", fontSize = 18.sp, modifier = Modifier.padding(start = 6.dp))
            }
        }

        // 本日
        item {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("本日", fontSize = 15.sp)
                    Text(
                        "有効時間 ${SpeedFormat.clock(todayValid)} / 目標 60:00",
                        fontSize = 13.sp
                    )
                    LinearProgressIndicator(
                        progress = { (todayValid.toFloat() / Balance.STREAK_GOAL_SEC).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(7.dp).padding(top = 4.dp)
                    )
                    Text("獲得 $earned / $cap pt", fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    LinearProgressIndicator(
                        progress = { (earned.toFloat() / cap).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(7.dp).padding(top = 4.dp)
                    )
                }
            }
        }

        // 連続日数
        item {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.icon_streak), null, Modifier.size(36.dp))
                    Column(modifier = Modifier.padding(start = 10.dp)) {
                        Text("連続 ${streak}日", fontSize = 16.sp)
                        Text("上限ボーナス +$bonus（最大 +${Balance.STREAK_BONUS_MAX}）", fontSize = 12.sp)
                        Text(
                            if (quota?.achieved == true) "本日は達成済み"
                            else "あと ${SpeedFormat.clock((Balance.STREAK_GOAL_SEC - todayValid).coerceAtLeast(0))} で達成",
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // 直近14日
        item {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("直近14日の有効時間", fontSize = 15.sp)
                    Text("横線は60分の目標", fontSize = 11.sp, color = Color(0xFF757575))
                    DailyBars(recent.reversed(), modifier = Modifier.fillMaxWidth().height(110.dp))
                }
            }
        }

        // ランク別
        item {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("獲得アイテム", fontSize = 15.sp)
                    val map = ranks.associateBy { it.rank }
                    ItemRank.values().forEach { r ->
                        val st: RankStat? = map[r.name]
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painterResource(r.drawable), null,
                                Modifier.size(40.dp), contentScale = ContentScale.Fit
                            )
                            Text(
                                "  ${r.label}  ${st?.count ?: 0}個  累計 ${st?.points ?: 0}pt",
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // 靴の状態
        item {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("靴の使用状況", fontSize = 15.sp)
                    shoes.forEach { s: ShoeEntity ->
                        val t = ShoeType.valueOf(s.shoeType)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painterResource(t.artFor(s.durability)), null,
                                Modifier.size(46.dp), contentScale = ContentScale.Fit
                            )
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(
                                    t.label + (if (s.equipped) "（装備中）" else ""),
                                    fontSize = 13.sp
                                )
                                Text(
                                    "耐久 ${s.durability} / 累計 ${SpeedFormat.clock(s.totalValidSec)}",
                                    fontSize = 11.sp
                                )
                                LinearProgressIndicator(
                                    progress = { s.durability / 100f },
                                    modifier = Modifier.width(140.dp).height(5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 累計
        item {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("累計", fontSize = 15.sp)
                    Text("有効時間 ${SpeedFormat.clock(totalValid)}", fontSize = 13.sp)
                    Text("距離 ${(totalDistance / 1000).let { String.format("%.2f", it) }} km", fontSize = 13.sp)
                    Text("セッション ${sessionCount}回", fontSize = 13.sp)
                    Text(
                        "学習した歩幅 ${String.format("%.2f", loadout?.strideM ?: Balance.DEFAULT_STRIDE_M)} m",
                        fontSize = 13.sp
                    )
                    Text(
                        "修理ポイント残高 ${loadout?.repairWallet ?: 0}",
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyBars(days: List<DailyQuotaEntity>, modifier: Modifier = Modifier) {
    val goal = Balance.STREAK_GOAL_SEC.toFloat()
    val maxV = maxOf(goal, days.maxOfOrNull { it.validSec.toFloat() } ?: goal)
    Canvas(modifier = modifier) {
        if (days.isEmpty()) return@Canvas
        val n = days.size
        val gap = 6f
        val w = (size.width - gap * (n - 1)) / n
        val goalY = size.height * (1f - goal / maxV)
        drawLine(
            color = Color(0xFFBDBDBD),
            start = Offset(0f, goalY),
            end = Offset(size.width, goalY),
            strokeWidth = 2f
        )
        days.forEachIndexed { i, d ->
            val h = size.height * (d.validSec.toFloat() / maxV)
            val x = i * (w + gap)
            drawRect(
                color = if (d.achieved) Color(0xFF2E7D32) else Color(0xFF90A4AE),
                topLeft = Offset(x, size.height - h),
                size = Size(w, h)
            )
        }
    }
}
