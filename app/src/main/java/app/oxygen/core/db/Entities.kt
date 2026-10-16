package app.oxygen.core.db
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val titleIsAuto: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val modelId: String,
    val systemPrompt: String? = null,
    val paramsJson: String? = null,
    val currentLeafId: String? = null,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val ephemeral: Boolean = false,
)
@Entity(
    tableName = "messages",
    indices = [
        Index("conversationId"),
        Index("parentId"),
    ],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val parentId: String? = null,
    val role: String,
    val content: String = "",
    val reasoning: String? = null,
    val toolCallsJson: String? = null,
    val toolCallId: String? = null,
    val attachmentsJson: String? = null,
    val citationsJson: String? = null,
    val modelId: String? = null,
    val provider: String? = null,
    val status: String = "complete",
    val error: String? = null,
    val finishReason: String? = null,
    val generationId: String? = null,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val cachedTokens: Int? = null,
    val reasoningTokens: Int? = null,
    val costUsd: Double? = null,
    val createdAt: Long,
    val latencyMs: Long? = null,
)
@Entity(tableName = "message_fts")
@Fts4
data class MessageFtsEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "rowid") val rowId: Int = 0,
    val messageId: String,
    val conversationId: String,
    val content: String,
)
@Entity(tableName = "generations", indices = [Index("createdAt"), Index("modelId")])
data class GenerationEntity(
    @PrimaryKey val id: String,
    val generationId: String?,
    val conversationId: String,
    val messageId: String,
    val modelId: String,
    val provider: String?,
    val costUsd: Double?,
    val promptTokens: Int?,
    val completionTokens: Int?,
    val cachedTokens: Int?,
    val reasoningTokens: Int?,
    val latencyMs: Long?,
    val ttftMs: Long?,
    val createdAt: Long,
)
@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val systemPrompt: String? = null,
    val modelId: String? = null,
    val paramsJson: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
data class ConversationSnippet(
    val conversationId: String,
    val content: String,
    val role: String,
    val createdAt: Long,
)
data class ModelAggregate(
    val modelId: String,
    val cost: Double,
    val tokens: Long,
    val count: Int,
)
data class DayAggregate(
    val day: String,
    val cost: Double,
    val count: Int,
    val promptTokens: Long,
    val completionTokens: Long,
)
data class FtsHit(
    val conversationId: String,
    val messageId: String,
    val content: String,
)
