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

@Entity(tableName = "asset")
data class AssetEntity(
    @PrimaryKey val uuid: String,
    @ColumnInfo(name = "item_def_id") val itemDefId: String,
    @ColumnInfo(name = "collection_id") val collectionId: String,
    @ColumnInfo(name = "acquired_at") val acquiredAt: Long,
    @ColumnInfo(name = "acquired_lat") val acquiredLat: Double,
    @ColumnInfo(name = "acquired_lng") val acquiredLng: Double,
    @ColumnInfo(name = "acquired_steps") val acquiredSteps: Int = 0,
    @ColumnInfo(name = "spawn_id") val spawnId: String,
    @ColumnInfo(name = "status") val status: String = AssetStatus.INTERNAL.name,
    @ColumnInfo(name = "owner_ref") val ownerRef: String? = null,
    @ColumnInfo(name = "chain_ref") val chainRef: String? = null,
    @ColumnInfo(name = "metadata_uri") val metadataUri: String? = null
)

@Entity(tableName = "acquired_spawn")
data class AcquiredSpawnEntity(
    @PrimaryKey val spawnId: String,
    @ColumnInfo(name = "acquired_at") val acquiredAt: Long
)

@Entity(tableName = "walk_session")
data class WalkSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "start_at") val startAt: Long,
    @ColumnInfo(name = "end_at") val endAt: Long? = null,
    @ColumnInfo(name = "steps") val steps: Int = 0,
    @ColumnInfo(name = "distance_m") val distanceM: Double = 0.0,
    @ColumnInfo(name = "track_json") val trackJson: String = "[]",
    @ColumnInfo(name = "invalid_segments") val invalidSegments: Int = 0,
    @ColumnInfo(name = "item_count") val itemCount: Int = 0
)

@Entity(tableName = "asset_event")
data class AssetEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "asset_uuid") val assetUuid: String,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "at") val at: Long,
    @ColumnInfo(name = "detail") val detail: String? = null
)

data class CollectedCount(
    @ColumnInfo(name = "item_def_id") val itemDefId: String,
    @ColumnInfo(name = "cnt") val count: Int
)

@Dao
interface WalkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAsset(asset: AssetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAcquiredSpawn(spawn: AcquiredSpawnEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: AssetEventEntity)

    @Query("SELECT spawnId FROM acquired_spawn")
    suspend fun acquiredSpawnIds(): List<String>

    @Query("SELECT * FROM asset ORDER BY acquired_at DESC")
    fun observeAssets(): Flow<List<AssetEntity>>

    @Query("SELECT * FROM asset ORDER BY acquired_at DESC")
    suspend fun allAssets(): List<AssetEntity>

    @Query("SELECT COUNT(*) FROM asset")
    fun observeAssetCount(): Flow<Int>

    @Query("UPDATE asset SET status = :status WHERE uuid = :uuid")
    suspend fun updateStatus(uuid: String, status: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: WalkSessionEntity): Long

    @Update
    suspend fun updateSession(session: WalkSessionEntity)

    @Query("SELECT * FROM walk_session ORDER BY start_at DESC")
    fun observeSessions(): Flow<List<WalkSessionEntity>>

    @Query("SELECT * FROM walk_session WHERE id = :id")
    suspend fun sessionById(id: Long): WalkSessionEntity?

    @Query("SELECT item_def_id, COUNT(*) AS cnt FROM asset GROUP BY item_def_id")
    fun observeCollected(): Flow<List<CollectedCount>>

    @Query("SELECT IFNULL(SUM(steps),0) FROM walk_session")
    fun observeTotalSteps(): Flow<Int>

    @Query("SELECT IFNULL(SUM(distance_m),0) FROM walk_session")
    fun observeTotalDistance(): Flow<Double>
}

@Database(
    entities = [
        AssetEntity::class,
        AcquiredSpawnEntity::class,
        AssetEventEntity::class,
        WalkSessionEntity::class
    ],
    version = 3,
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
