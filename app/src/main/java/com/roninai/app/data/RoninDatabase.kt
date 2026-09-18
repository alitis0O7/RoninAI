package com.roninai.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.roninai.app.data.dao.MessageDao
import com.roninai.app.data.dao.SessionDao
import com.roninai.app.data.entity.MessageEntity
import com.roninai.app.data.entity.SessionEntity

@Database(
    entities = [
        SessionEntity::class,
        MessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class RoninDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile
        private var INSTANCE: RoninDatabase? = null

        fun getInstance(context: Context): RoninDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RoninDatabase::class.java,
                    "ronin_ai.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
