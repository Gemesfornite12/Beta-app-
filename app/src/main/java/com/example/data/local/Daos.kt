package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.AudioProject
import com.example.data.model.ChatMessage
import com.example.data.model.DocumentItem
import com.example.data.model.RecordedAudioSample
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

    @Query("SELECT * FROM audio_projects WHERE isPublic = 1 ORDER BY lastModified DESC")
    fun getPublicAudioProjects(): Flow<List<AudioProject>>

    @Query("SELECT * FROM audio_projects WHERE id = :id LIMIT 1")
    suspend fun getAudioProjectById(id: Long): AudioProject?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudioProject(project: AudioProject): Long

    @Update
    suspend fun updateAudioProject(project: AudioProject)

    @Query("UPDATE audio_projects SET title = :newTitle, lastModified = :lastModified WHERE id = :id")
    suspend fun updateTitle(id: Long, newTitle: String, lastModified: Long = System.currentTimeMillis())

    @Query("UPDATE audio_projects SET isPublic = :isPublic, lastModified = :lastModified WHERE id = :id")
    suspend fun updatePublicStatus(id: Long, isPublic: Boolean, lastModified: Long = System.currentTimeMillis())

    @Query("DELETE FROM audio_projects WHERE id = :id")
    suspend fun deleteAudioProject(id: Long)
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE channelId = :channelId ORDER BY timestamp ASC")
    fun getMessagesForChannel(channelId: String): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages WHERE isSyncedFirestore = 0 OR firestoreId = ''")
    suspend fun getUnsyncedMessages(): List<ChatMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessage>)

    @Update
    suspend fun updateMessage(message: ChatMessage)

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteMessage(id: Long)

    @Query("DELETE FROM chat_messages WHERE firestoreId = :firestoreId")
    suspend fun deleteMessageByFirestoreId(firestoreId: String)

    @Query("DELETE FROM chat_messages WHERE channelId = :channelId")
    suspend fun deleteMessagesForChannel(channelId: String)
}

@Dao
interface RecordedAudioSampleDao {
    @Query("SELECT * FROM recorded_audio_samples ORDER BY createdAt DESC")
    fun getAllSamples(): Flow<List<RecordedAudioSample>>

    @Query("SELECT * FROM recorded_audio_samples WHERE id = :id LIMIT 1")
    suspend fun getSampleById(id: Long): RecordedAudioSample?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSample(sample: RecordedAudioSample): Long

    @Update
    suspend fun updateSample(sample: RecordedAudioSample)

    @Query("DELETE FROM recorded_audio_samples WHERE id = :id")
    suspend fun deleteSample(id: Long)
}
