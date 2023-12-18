package dev.hasali.luna.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Package::class], version = 1)
abstract class LunaDatabase : RoomDatabase() {
    abstract fun packageDao(): PackageDao

    companion object {
        fun open(context: Context): LunaDatabase {
            return Room.databaseBuilder(context, LunaDatabase::class.java, "luna")
                .build()
        }
    }
}
