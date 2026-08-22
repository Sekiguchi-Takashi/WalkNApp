package com.appathy.walknapp.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

enum class AssetStatus { INTERNAL, PENDING_MINT, MINTED, EXPORTED }

@Entity(tableName = "shoe")
data class ShoeEntity(
    @PrimaryKey val uuid: String,
    @ColumnInfo(name = "shoe_type") val shoeType: String,
    @ColumnInfo(name = "durability") val durability: Int,
    @ColumnInfo(name = "equipped") val equipped: Boolean,
    @ColumnInfo(name = "total_valid_sec") val totalValidSec: Long = 0
)

@Entity(tableName = "loadout")
data class LoadoutEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "wear_type") val wearType: String,
    @ColumnInfo(name = "avatar_girl") val avatarGirl: Boolean = false,
    @ColumnInfo(name = "repair_wallet") val repairWallet: Int = 0,
    @ColumnInfo(name = "stride_low") val strideLow: Double = 0.62,
    @ColumnInfo(name = "stride_mid") val strideMid: Double = 0.70,
    @ColumnInfo(name = "stride_high") val strideHigh: Double = 0.78
)

@Entity(tableName = "asset")
data class AssetEntity(
    @PrimaryKey val uuid: String,
    @ColumnInfo(name = "rank") val rank: String,
    @ColumnInfo(name = "repair_point") val repairPoint: Int,
    @ColumnInfo(name = "acquired_at") val acquiredAt: Long,
    @ColumnInfo(name = "acquired_lat") val acquiredLat: Double,
    @ColumnInfo(name = "acquired_lng") val acquiredLng: Double,
    @ColumnInfo(name = "valid_sec_at_grant") val validSecAtGrant: Long,
    @ColumnInfo(name = "avg_speed_kmh") val avgSpeedKmh: Double,
    @ColumnInfo(name = "shoe_type") val shoeType: String,
    @ColumnInfo(name = "wear_type") val wearType: String,
    @ColumnInfo(name = "speed_source") val speedSource: String,
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "status") val status: String = AssetStatus.INTERNAL.name,
    @ColumnInfo(name = "owner_ref") val ownerRef: String? = null,
    @ColumnInfo(name = "chain_ref") val chainRef: String? = null,
    @ColumnInfo(name = "metadata_uri") val metadataUri: String? = null
)

@Entity(tableName = "walk_session")
data class WalkSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "start_at") val startAt: Long,
    @ColumnInfo(name = "end_at") val endAt: Long? = null,
    @ColumnInfo(name = "shoe_type") val shoeType: String,
    @ColumnInfo(name = "wear_type") val wearType: String,
    @ColumnInfo(name = "valid_sec") val validSec: Long = 0,
    @ColumnInfo(name = "distance_m") val distanceM: Double = 0.0,
    @ColumnInfo(name = "steps") val steps: Int = 0,
    @ColumnInfo(name = "route_json") val routeJson: String = "[]",
    @ColumnInfo(name = "grants") val grants: Int = 0,
    @ColumnInfo(name = "durability_used") val durabilityUsed: Int = 0
)

@Entity(tableName = "daily_quota")
data class DailyQuotaEntity(
    @PrimaryKey val date: String,
    @ColumnInfo(name = "earned_points") val earnedPoints: Int = 0,
    @ColumnInfo(name = "valid_sec") val validSec: Long = 0,
    @ColumnInfo(name = "streak_days") val streakDays: Int = 0,
    @ColumnInfo(name = "achieved") val achieved: Boolean = false
)

@Entity(tableName = "asset_event")
data class AssetEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "asset_uuid") val assetUuid: String?,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "at") val at: Long,
    @ColumnInfo(name = "detail") val detail: String? = null
)

data class RankStat(
    @ColumnInfo(name = "rank") val rank: String,
    @ColumnInfo(name = "cnt") val count: Int,
    @ColumnInfo(name = "pts") val points: Int
)

@Dao
interface WalkDao {

    @Query("SELECT * FROM shoe")
    fun observeShoes(): Flow<List<ShoeEntity>>

    @Query("SELECT * FROM shoe")
    suspend fun allShoes(): List<ShoeEntity>

    @Query("SELECT * FROM shoe WHERE equipped = 1 LIMIT 1")
    suspend fun equippedShoe(): ShoeEntity?

    @Query("SELECT * FROM shoe WHERE equipped = 1 LIMIT 1")
    fun observeEquippedShoe(): Flow<ShoeEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShoe(shoe: ShoeEntity)

    @Update
    suspend fun updateShoe(shoe: ShoeEntity)

    @Query("UPDATE shoe SET equipped = 0")
    suspend fun unequipAll()

    @Query("UPDATE shoe SET equipped = 1 WHERE uuid = :uuid")
    suspend fun equip(uuid: String)

    @Query("SELECT * FROM loadout WHERE id = 1")
    suspend fun loadout(): LoadoutEntity?

    @Query("SELECT * FROM loadout WHERE id = 1")
    fun observeLoadout(): Flow<LoadoutEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLoadout(loadout: LoadoutEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAsset(asset: AssetEntity)

    @Query("SELECT * FROM asset ORDER BY acquired_at DESC")
    fun observeAssets(): Flow<List<AssetEntity>>

    @Query("SELECT * FROM asset ORDER BY acquired_at DESC")
    suspend fun allAssets(): List<AssetEntity>

    @Query("SELECT COUNT(*) FROM asset")
    fun observeAssetCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: WalkSessionEntity): Long

    @Update
    suspend fun updateSession(session: WalkSessionEntity)

    @Query("SELECT * FROM walk_session WHERE id = :id")
    suspend fun sessionById(id: Long): WalkSessionEntity?

    @Query("SELECT * FROM walk_session ORDER BY start_at DESC")
    fun observeSessions(): Flow<List<WalkSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveQuota(quota: DailyQuotaEntity)

    @Query("SELECT * FROM daily_quota WHERE date = :date")
    suspend fun quotaOf(date: String): DailyQuotaEntity?

    @Query("SELECT * FROM daily_quota WHERE date = :date")
    fun observeQuota(date: String): Flow<DailyQuotaEntity?>

    @Query("SELECT * FROM daily_quota ORDER BY date DESC LIMIT 30")
    suspend fun recentQuotas(): List<DailyQuotaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: AssetEventEntity)

    @Query("SELECT rank, COUNT(*) AS cnt, SUM(repair_point) AS pts FROM asset GROUP BY rank")
    fun observeRankStats(): Flow<List<RankStat>>

    @Query("SELECT * FROM daily_quota ORDER BY date DESC LIMIT 14")
    fun observeRecentQuotas(): Flow<List<DailyQuotaEntity>>

    @Query("SELECT IFNULL(SUM(valid_sec),0) FROM walk_session")
    fun observeTotalValidSec(): Flow<Long>

    @Query("SELECT IFNULL(SUM(distance_m),0) FROM walk_session")
    fun observeTotalDistance(): Flow<Double>

    @Query("SELECT COUNT(*) FROM walk_session WHERE end_at IS NOT NULL")
    fun observeSessionCount(): Flow<Int>
}

@Database(
    entities = [
        ShoeEntity::class,
        LoadoutEntity::class,
        AssetEntity::class,
        WalkSessionEntity::class,
        DailyQuotaEntity::class,
        AssetEventEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class WalkDatabase : RoomDatabase() {
    abstract fun dao(): WalkDao

    companion object {
        @Volatile private var instance: WalkDatabase? = null

        fun get(context: Context): WalkDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WalkDatabase::class.java,
                    "walkn.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
