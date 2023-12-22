package dev.hasali.luna.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PackageDao {
    @Query("SELECT * FROM Package")
    fun getAll(): Flow<List<Package>>

    @Query("SELECT * FROM Package WHERE packageName = :packageName")
    fun getByPackageName(packageName: String): Flow<Package>

    @Insert
    suspend fun insert(pkg: Package)

    @Query("UPDATE Package SET latestVersionName = :versionName, latestVersionCode = :versionCode WHERE packageName = :packageName")
    suspend fun updateLatestVersion(packageName: String, versionName: String, versionCode: Long)
}
