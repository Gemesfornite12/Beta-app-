package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.AudioProject
import com.example.data.model.ChatMessage
import com.example.data.model.DocumentItem
import com.example.data.model.UserAccount
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserAccount?

    @Query("SELECT * FROM users ORDER BY lastLoginTimestamp DESC")
    fun getAllUsers(): Flow<List<UserAccount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserAccount)

    @Update
    suspend fun updateUser(user: UserAccount)
}

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY lastModified DESC")
    fun getAllDocuments(): Flow<List<DocumentItem>>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentById(id: Long): DocumentItem?

    @Query("SELECT * FROM documents WHERE title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' ORDER BY lastModified DESC")
    fun searchDocuments(query: String): Flow<List<DocumentItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(doc: DocumentItem): Long

    @Update
    suspend fun updateDocument(doc: DocumentItem)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocument(id: Long)
}

@Dao
interface AudioProjectDao {
    @Query("SELECT * FROM audio_projects ORDER BY lastModified DESC")
    fun getAllAudioProjects(): Flow<List<AudioProject>>

    @Query("SELECT * FROM audio_projects WHERE id = :id LIMIT 1")
    suspend fun getAudioProjectById(id: Long): AudioProject?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudioProject(project: AudioProject): Long

    @Update
    suspend fun updateAudioProject(project: AudioProject)

    @Query("DELETE FROM audio_projects WHERE id = :id")
    suspend fun deleteAudioProject(id: Long)
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE channelId = :channelId ORDER BY timestamp ASC")
    fun getMessagesForChannel(channelId: String): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage): Long

    @Update
    suspend fun updateMessage(message: ChatMessage)

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteMessage(id: Long)
}
