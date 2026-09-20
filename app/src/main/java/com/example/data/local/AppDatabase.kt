package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.data.model.AudioProject
import com.example.data.model.ChatMessage
import com.example.data.model.DocumentFormat
import com.example.data.model.DocumentItem
import com.example.data.model.DocumentType
import com.example.data.model.UserAccount

class Converters {
    @TypeConverter
    fun fromDocType(value: DocumentType): String = value.name

    @TypeConverter
    fun toDocType(value: String): DocumentType = try {
        DocumentType.valueOf(value)
    } catch (_: Exception) {
        DocumentType.DOC
    }

    @TypeConverter
    fun fromDocFormat(value: DocumentFormat): String = value.name

    @TypeConverter
    fun toDocFormat(value: String): DocumentFormat = try {
        DocumentFormat.valueOf(value)
    } catch (_: Exception) {
        DocumentFormat.DOCX
    }
}

@Database(
    entities = [
        UserAccount::class,
        DocumentItem::class,
        AudioProject::class,
        ChatMessage::class
    ],
    version = 6,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun documentDao(): DocumentDao
    abstract fun audioProjectDao(): AudioProjectDao
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "omni_studio_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
