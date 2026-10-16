package app.oxygen.core.db
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE archived = 0 AND ephemeral = 0 ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>
    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observe(id: String): Flow<ConversationEntity?>
    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun get(id: String): ConversationEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ConversationEntity)
    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: String)
    @Query("DELETE FROM messages WHERE conversationId = :id")
    suspend fun deleteMessages(id: String)
    @Query("DELETE FROM message_fts WHERE conversationId = :id")
    suspend fun deleteFts(id: String)
    @Query("UPDATE conversations SET title = :title, titleIsAuto = :auto WHERE id = :id")
    suspend fun updateTitle(id: String, title: String, auto: Boolean)
    @Query("UPDATE conversations SET updatedAt = :ts WHERE id = :id")
    suspend fun touch(id: String, ts: Long)
    @Query("UPDATE conversations SET currentLeafId = :leaf WHERE id = :id")
    suspend fun updateLeaf(id: String, leaf: String?)
    @Query("UPDATE conversations SET modelId = :modelId WHERE id = :id")
    suspend fun updateModel(id: String, modelId: String)
    @Query("UPDATE conversations SET systemPrompt = :prompt WHERE id = :id")
    suspend fun updateSystemPrompt(id: String, prompt: String?)
    @Query("UPDATE conversations SET paramsJson = :params WHERE id = :id")
    suspend fun updateParams(id: String, params: String?)
    @Query("UPDATE conversations SET pinned = :pinned WHERE id = :id")
    suspend fun updatePinned(id: String, pinned: Boolean)
    @Query("UPDATE conversations SET ephemeral = 0 WHERE id = :id")
    suspend fun clearEphemeral(id: String)
    @Query(
        """
        SELECT m.conversationId AS conversationId, m.content AS content, m.role AS role, m.createdAt AS createdAt
        FROM messages m
        WHERE m.createdAt = (SELECT MAX(x.createdAt) FROM messages x WHERE x.conversationId = m.conversationId)
        """,
    )
    fun observeSnippets(): Flow<List<ConversationSnippet>>
    @Query("SELECT * FROM conversations")
    suspend fun getAll(): List<ConversationEntity>
    @Query("SELECT id FROM conversations WHERE ephemeral = 1")
    suspend fun ephemeralIds(): List<String>
}
@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeByConversation(conversationId: String): Flow<List<MessageEntity>>
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun allByConversation(conversationId: String): List<MessageEntity>
    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun get(id: String): MessageEntity?
    @Query("SELECT * FROM messages WHERE parentId = :parentId ORDER BY createdAt ASC")
    suspend fun childrenOf(parentId: String): List<MessageEntity>
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND parentId IS :parentId ORDER BY createdAt ASC, id ASC")
    suspend fun siblingsOf(conversationId: String, parentId: String?): List<MessageEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MessageEntity)
    @Query(
        """
        UPDATE messages SET content = :content, reasoning = :reasoning, status = :status
        WHERE id = :id
        """,
    )
    suspend fun checkpoint(id: String, content: String, reasoning: String?, status: String)
    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: String)
    @Query("UPDATE messages SET status = 'interrupted' WHERE status = 'streaming'")
    suspend fun markStreamingInterrupted()
    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId AND role = 'user'")
    suspend fun countUserMessages(conversationId: String): Int
    @Query("SELECT content FROM messages WHERE conversationId = :conversationId AND role = 'user' ORDER BY createdAt ASC LIMIT 1")
    suspend fun firstUserContent(conversationId: String): String?
}
@Dao
interface FtsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MessageFtsEntity)
    @Query("DELETE FROM message_fts WHERE messageId = :messageId")
    suspend fun deleteByMessage(messageId: String)
    @Query(
        """
        SELECT conversationId, messageId, content FROM message_fts
        WHERE content MATCH :matchQuery
        LIMIT 80
        """,
    )
    suspend fun search(matchQuery: String): List<FtsHit>
}
@Dao
interface GenerationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: GenerationEntity)
    @Query("SELECT SUM(costUsd) FROM generations WHERE createdAt >= :since")
    suspend fun sumSince(since: Long): Double?
    @Query("SELECT SUM(costUsd) FROM generations")
    suspend fun sumAll(): Double?
    @Query("SELECT COUNT(*) FROM generations")
    suspend fun countAll(): Int
    @Query(
        """
        SELECT modelId AS modelId, SUM(costUsd) AS cost,
               SUM(promptTokens + completionTokens) AS tokens, COUNT(*) AS count
        FROM generations GROUP BY modelId ORDER BY cost DESC LIMIT 40
        """,
    )
    suspend fun byModel(): List<ModelAggregate>
    @Query(
        """
        SELECT date(createdAt / 1000, 'unixepoch', 'localtime') AS day,
               SUM(costUsd) AS cost, COUNT(*) AS count,
               SUM(promptTokens) AS promptTokens, SUM(completionTokens) AS completionTokens
        FROM generations
        WHERE createdAt >= :since
        GROUP BY day ORDER BY day ASC
        """,
    )
    suspend fun daily(since: Long): List<DayAggregate>
    @Query("SELECT SUM(costUsd) FROM generations WHERE conversationId = :conversationId")
    suspend fun sumForConversation(conversationId: String): Double?
    @Query("DELETE FROM generations")
    suspend fun clear()
}
@Dao
interface PresetDao {
    @Query("SELECT * FROM presets ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<PresetEntity>>
    @Query("SELECT * FROM presets WHERE id = :id")
    suspend fun get(id: String): PresetEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PresetEntity)
    @Query("DELETE FROM presets WHERE id = :id")
    suspend fun delete(id: String)
}
