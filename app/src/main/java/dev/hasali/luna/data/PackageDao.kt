package dev.hasali.luna.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PackageDao {
    @Query("SELECT * FROM Package")
    fun getAll(): Flow<List<Package>>

    @Insert
    suspend fun insert(pgk: Package)
}
