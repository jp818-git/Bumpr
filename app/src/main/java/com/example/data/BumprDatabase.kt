package com.example.data

import android.content.Context
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

@Entity(tableName = "lyrics")
data class LyricEntity(
    @PrimaryKey val trackId: String,
    val title: String,
    val artist: String,
    val lrcContent: String,
    val offsetMs: Long,
    val lastModified: Long = System.currentTimeMillis()
)

@Entity(tableName = "favorites")
data class FavoriteTrackEntity(
    @PrimaryKey val trackId: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Dao
interface LyricDao {
    @Query("SELECT * FROM lyrics WHERE trackId = :trackId LIMIT 1")
    suspend fun getLyricForTrack(trackId: String): LyricEntity?

    @Query("SELECT * FROM lyrics WHERE trackId = :trackId LIMIT 1")
    fun observeLyricForTrack(trackId: String): Flow<LyricEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLyric(lyric: LyricEntity)

    @Query("DELETE FROM lyrics WHERE trackId = :trackId")
    suspend fun deleteLyric(trackId: String)
}

@Dao
interface FavoriteDao {
    @Query("SELECT trackId FROM favorites")
    fun getAllFavoriteIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteTrackEntity)

    @Query("DELETE FROM favorites WHERE trackId = :trackId")
    suspend fun removeFavorite(trackId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE trackId = :trackId)")
    suspend fun isFavorite(trackId: String): Boolean
}

@Database(
    entities = [LyricEntity::class, FavoriteTrackEntity::class],
    version = 1,
    exportSchema = false
)
abstract class BumprDatabase : RoomDatabase() {
    abstract fun lyricDao(): LyricDao
    abstract fun favoriteDao(): FavoriteDao

    companion object {
        @Volatile
        private var INSTANCE: BumprDatabase? = null

        fun getDatabase(context: Context): BumprDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BumprDatabase::class.java,
                    "bumpr_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
