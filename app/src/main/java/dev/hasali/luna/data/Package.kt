package dev.hasali.luna.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(indices = [Index("packageName", unique = true)])
data class Package(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val label: String,
    val packageName: String,
    val manifestUrl: String,
)
