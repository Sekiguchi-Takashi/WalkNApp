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
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "item_instance")
data class ItemInstanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "item_def_id") val itemDefId: String,
    @ColumnInfo(name = "acquired_at") val acquiredAt: Long,
    @ColumnInfo(name = "acquired_lat") val acquiredLat: Double,
    @ColumnInfo(name = "acquired_lng") val acquiredLng: Double,
    @ColumnInfo(name = "spawn_id") val spawnId: String
)

@Entity(tableName = "acquired_spawn")
data class AcquiredSpawnEntity(
    @PrimaryKey val spawnId: String,
    @ColumnInfo(name = "acquired_at") val acquiredAt: Long
)

@Dao
interface WalkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ItemInstanceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAcquiredSpawn(spawn: AcquiredSpawnEntity)

    @Query("SELECT spawnId FROM acquired_spawn")
    suspend fun acquiredSpawnIds(): List<String>

    @Query("SELECT * FROM item_instance ORDER BY acquired_at DESC")
    fun observeItems(): Flow<List<ItemInstanceEntity>>

    @Query("SELECT COUNT(*) FROM item_instance")
    fun observeItemCount(): Flow<Int>
}

@Database(
    entities = [ItemInstanceEntity::class, AcquiredSpawnEntity::class],
    version = 1,
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
