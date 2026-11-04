package com.example.data.repository

import android.util.Log
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.UserProfile
import com.example.data.remote.FirebaseManager
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ChatRepository {

    private val db = FirebaseManager.firestore
    private val conversationsRef = db.collection("conversations")

    /**
     * Finds existing 1-on-1 conversation or creates a new one
     */
    suspend fun getOrCreateOneToOneConversation(
        currentUser: UserProfile,
        otherUser: UserProfile
    ): Result<String> {
        val currentUid = currentUser.uid
        val otherUid = otherUser.uid
        return try {
            val query = conversationsRef
                .whereEqualTo("isGroup", false)
                .whereArrayContains("participants", currentUid)
                .get()
                .await()

            for (doc in query.documents) {
                val participants = doc.get("participants") as? List<*>
                if (participants != null && participants.contains(otherUid)) {
                    return Result.success(doc.id)
                }
            }

            // Create new conversation
            val convId = UUID.randomUUID().toString()
            val newConv = Conversation(
                id = convId,
                isGroup = false,
                participants = listOf(currentUid, otherUid),
                participantNames = mapOf(
                    currentUid to currentUser.displayName.ifBlank { currentUser.username },
                    otherUid to otherUser.displayName.ifBlank { otherUser.username }
                ),
                participantPhotos = mapOf(
                    currentUid to currentUser.photoUrl,
                    otherUid to otherUser.photoUrl
                ),
                lastMessageText = "Started a conversation",
                lastMessageTimestamp = System.currentTimeMillis(),
                lastMessageSenderId = currentUid,
                createdBy = currentUid
            )
            conversationsRef.document(convId).set(newConv.toMap()).await()
            Result.success(convId)
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error getting/creating conversation: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Create a new Group conversation
     */
    suspend fun createGroup(
        name: String,
        description: String,
        photoUrl: String,
        currentUser: UserProfile,
        selectedUsers: List<UserProfile>
    ): Result<String> {
        return try {
            val groupId = UUID.randomUUID().toString()
            val participants = mutableListOf(currentUser.uid)
            val participantNames = mutableMapOf(currentUser.uid to currentUser.displayName.ifBlank { currentUser.username })
            val participantPhotos = mutableMapOf(currentUser.uid to currentUser.photoUrl)

            selectedUsers.forEach { user ->
                participants.add(user.uid)
                participantNames[user.uid] = user.displayName.ifBlank { user.username }
                participantPhotos[user.uid] = user.photoUrl
            }

            val conversation = Conversation(
                id = groupId,
                isGroup = true,
                participants = participants,
                participantNames = participantNames,
                participantPhotos = participantPhotos,
                lastMessageText = "Group created",
                lastMessageTimestamp = System.currentTimeMillis(),
                lastMessageSenderId = currentUser.uid,
                groupName = name,
                groupPhotoUrl = photoUrl,
                groupDescription = description,
                createdBy = currentUser.uid
            )

            conversationsRef.document(groupId).set(conversation.toMap()).await()
            Result.success(groupId)
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error creating group: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Real-time stream of all user conversations
     */
    fun getConversationsFlow(currentUserId: String): Flow<List<Conversation>> = callbackFlow {
        if (currentUserId.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = conversationsRef
            .whereArrayContains("participants", currentUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("ChatRepository", "Listen conversations failed: ${error.message}")
                    return@addSnapshotListener
                }
                val list = mutableListOf<Conversation>()
                snapshot?.documents?.forEach { doc ->
                    val data = doc.data ?: return@forEach
                    @Suppress("UNCHECKED_CAST")
                    val participants = data["participants"] as? List<String> ?: emptyList()
                    @Suppress("UNCHECKED_CAST")
                    val participantNames = data["participantNames"] as? Map<String, String> ?: emptyMap()
                    @Suppress("UNCHECKED_CAST")
                    val participantPhotos = data["participantPhotos"] as? Map<String, String> ?: emptyMap()
                    @Suppress("UNCHECKED_CAST")
                    val unreadCounts = (data["unreadCounts"] as? Map<String, Number>)?.mapValues { it.value.toLong() } ?: emptyMap()

                    list.add(
                        Conversation(
                            id = doc.id,
                            isGroup = data["isGroup"] as? Boolean ?: false,
                            participants = participants,
                            participantNames = participantNames,
                            participantPhotos = participantPhotos,
                            lastMessageText = data["lastMessageText"] as? String ?: "",
                            lastMessageTimestamp = (data["lastMessageTimestamp"] as? Number)?.toLong() ?: 0L,
                            lastMessageSenderId = data["lastMessageSenderId"] as? String ?: "",
                            unreadCounts = unreadCounts,
                            groupName = data["groupName"] as? String ?: "",
                            groupPhotoUrl = data["groupPhotoUrl"] as? String ?: "",
                            groupDescription = data["groupDescription"] as? String ?: "",
                            createdBy = data["createdBy"] as? String ?: ""
                        )
                    )
                }
                list.sortByDescending { it.lastMessageTimestamp }
                trySend(list)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Real-time stream of messages in a conversation
     */
    fun getMessagesFlow(conversationId: String): Flow<List<ChatMessage>> = callbackFlow {
        if (conversationId.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = conversationsRef.document(conversationId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("ChatRepository", "Listen messages failed: ${error.message}")
                    return@addSnapshotListener
                }
                val messages = mutableListOf<ChatMessage>()
                snapshot?.documents?.forEach { doc ->
                    val data = doc.data ?: return@forEach
                    @Suppress("UNCHECKED_CAST")
                    val deletedForUsers = data["deletedForUsers"] as? List<String> ?: emptyList()
                    messages.add(
                        ChatMessage(
                            id = doc.id,
                            conversationId = conversationId,
                            senderId = data["senderId"] as? String ?: "",
                            senderName = data["senderName"] as? String ?: "",
                            senderPhotoUrl = data["senderPhotoUrl"] as? String ?: "",
                            text = data["text"] as? String ?: "",
                            mediaUrl = data["mediaUrl"] as? String ?: "",
                            mediaType = data["mediaType"] as? String ?: "text",
                            voiceDurationSeconds = (data["voiceDurationSeconds"] as? Number)?.toInt() ?: 0,
                            timestamp = (data["timestamp"] as? Number)?.toLong() ?: 0L,
                            status = data["status"] as? String ?: "sent",
                            replyToMessageId = data["replyToMessageId"] as? String ?: "",
                            replyToText = data["replyToText"] as? String ?: "",
                            replyToSender = data["replyToSender"] as? String ?: "",
                            deletedForEveryone = data["deletedForEveryone"] as? Boolean ?: false,
                            deletedForUsers = deletedForUsers
                        )
                    )
                }
                trySend(messages)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Send message
     */
    suspend fun sendMessage(
        conversationId: String,
        currentUser: UserProfile,
        text: String,
        mediaUrl: String = "",
        mediaType: String = "text",
        voiceDurationSeconds: Int = 0,
        replyTo: ChatMessage? = null
    ): Result<Unit> {
        return try {
            val msgId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val message = ChatMessage(
                id = msgId,
                conversationId = conversationId,
                senderId = currentUser.uid,
                senderName = currentUser.displayName.ifBlank { currentUser.username },
                senderPhotoUrl = currentUser.photoUrl,
                text = text,
                mediaUrl = mediaUrl,
                mediaType = mediaType,
                voiceDurationSeconds = voiceDurationSeconds,
                timestamp = now,
                status = "sent",
                replyToMessageId = replyTo?.id ?: "",
                replyToText = replyTo?.text ?: "",
                replyToSender = replyTo?.senderName ?: ""
            )

            // Save message
            conversationsRef.document(conversationId)
                .collection("messages")
                .document(msgId)
                .set(message.toMap())
                .await()

            // Update conversation last message preview
            val previewText = when (mediaType) {
                "image" -> "📷 Photo"
                "video" -> "🎥 Video"
                "voice" -> "🎤 Voice message ($voiceDurationSeconds s)"
                "audio" -> "🎵 Audio"
                else -> text
            }
            conversationsRef.document(conversationId).update(
                mapOf(
                    "lastMessageText" to previewText,
                    "lastMessageTimestamp" to now,
                    "lastMessageSenderId" to currentUser.uid
                )
            ).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error sending message: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Marks unread messages as seen
     */
    suspend fun markMessagesAsSeen(conversationId: String, currentUserId: String) {
        try {
            val unread = conversationsRef.document(conversationId)
                .collection("messages")
                .whereNotEqualTo("senderId", currentUserId)
                .get()
                .await()

            val batch = db.batch()
            for (doc in unread.documents) {
                val status = doc.getString("status")
                if (status != "seen") {
                    batch.update(doc.reference, "status", "seen")
                }
            }
            batch.commit().await()
        } catch (e: Exception) {
            Log.w("ChatRepository", "Error marking seen: ${e.message}")
        }
    }

    /**
     * Delete message for everyone
     */
    suspend fun deleteMessageForEveryone(conversationId: String, messageId: String): Result<Unit> {
        return try {
            conversationsRef.document(conversationId)
                .collection("messages")
                .document(messageId)
                .update(
                    mapOf(
                        "deletedForEveryone" to true,
                        "text" to "This message was deleted",
                        "mediaUrl" to ""
                    )
                ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete message for me
     */
    suspend fun deleteMessageForMe(conversationId: String, messageId: String, currentUserId: String): Result<Unit> {
        return try {
            conversationsRef.document(conversationId)
                .collection("messages")
                .document(messageId)
                .update("deletedForUsers", FieldValue.arrayUnion(currentUserId))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Export chat history to formatted text
     */
    fun exportChatHistory(conversation: Conversation, messages: List<ChatMessage>): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val sb = StringBuilder()
        sb.append("=== NexChat Pro Export ===\n")
        sb.append("Conversation: ${if (conversation.isGroup) conversation.groupName else "Direct Chat"}\n")
        sb.append("Exported: ${sdf.format(Date())}\n")
        sb.append("Total Messages: ${messages.size}\n\n")

        for (msg in messages) {
            val time = sdf.format(Date(msg.timestamp))
            val sender = msg.senderName.ifBlank { msg.senderId }
            val content = if (msg.deletedForEveryone) {
                "[Deleted message]"
            } else if (msg.mediaUrl.isNotEmpty()) {
                "[${msg.mediaType.uppercase()}] ${msg.text} (${msg.mediaUrl})"
            } else {
                msg.text
            }
            sb.append("[$time] $sender: $content\n")
        }
        return sb.toString()
    }
}
