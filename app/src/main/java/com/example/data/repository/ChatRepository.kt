package com.example.data.repository

import android.util.Log
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.UserProfile
import com.example.data.remote.FirebaseManager
import com.example.data.repository.UserRepository.Companion.extractPhotoUrl
import com.example.data.repository.UserRepository.Companion.parseTimestamp
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
    private val chatsRef = db.collection("chats")

    /**
     * Finds existing 1-on-1 conversation or creates a new one in both collections
     */
    suspend fun getOrCreateOneToOneConversation(
        currentUser: UserProfile,
        otherUser: UserProfile
    ): Result<String> {
        val currentUid = currentUser.uid
        val otherUid = otherUser.uid
        return try {
            // 1. Check in 'conversations'
            val convQuery = conversationsRef
                .whereArrayContains("participants", currentUid)
                .get()
                .await()

            for (doc in convQuery.documents) {
                val participants = extractParticipantsList(doc.data ?: emptyMap())
                if (participants.contains(otherUid) && !(doc.getBoolean("isGroup") ?: false)) {
                    return Result.success(doc.id)
                }
            }

            // 2. Check in 'chats'
            val chatsQuery = chatsRef
                .whereArrayContains("participants", currentUid)
                .get()
                .await()

            for (doc in chatsQuery.documents) {
                val participants = extractParticipantsList(doc.data ?: emptyMap())
                if (participants.contains(otherUid) && !(doc.getBoolean("isGroup") ?: false)) {
                    return Result.success(doc.id)
                }
            }

            // 3. Create new conversation
            val convId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val convMap = mapOf(
                "id" to convId,
                "isGroup" to false,
                "participants" to listOf(currentUid, otherUid),
                "users" to listOf(currentUid, otherUid),
                "members" to listOf(currentUid, otherUid),
                "participantNames" to mapOf(
                    currentUid to currentUser.displayName.ifBlank { currentUser.username },
                    otherUid to otherUser.displayName.ifBlank { otherUser.username }
                ),
                "participantPhotos" to mapOf(
                    currentUid to currentUser.photoUrl,
                    otherUid to otherUser.photoUrl
                ),
                "lastMessageText" to "Started a conversation",
                "lastMessage" to "Started a conversation",
                "last_message" to "Started a conversation",
                "lastMessageTimestamp" to now,
                "timestamp" to now,
                "lastMessageSenderId" to currentUid,
                "createdBy" to currentUid,
                "createdAt" to now
            )

            conversationsRef.document(convId).set(convMap, SetOptions.merge()).await()
            chatsRef.document(convId).set(convMap, SetOptions.merge()).await()

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

            val now = System.currentTimeMillis()
            val convMap = mapOf(
                "id" to groupId,
                "isGroup" to true,
                "participants" to participants,
                "users" to participants,
                "members" to participants,
                "participantNames" to participantNames,
                "participantPhotos" to participantPhotos,
                "lastMessageText" to "Group created",
                "lastMessage" to "Group created",
                "lastMessageTimestamp" to now,
                "timestamp" to now,
                "lastMessageSenderId" to currentUser.uid,
                "groupName" to name,
                "name" to name,
                "groupPhotoUrl" to photoUrl,
                "groupDescription" to description,
                "createdBy" to currentUser.uid,
                "createdAt" to now
            )

            conversationsRef.document(groupId).set(convMap, SetOptions.merge()).await()
            chatsRef.document(groupId).set(convMap, SetOptions.merge()).await()

            Result.success(groupId)
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error creating group: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Real-time stream of all user conversations merging 'conversations' and 'chats'
     */
    fun getConversationsFlow(currentUserId: String): Flow<List<Conversation>> = callbackFlow {
        if (currentUserId.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val convMap = mutableMapOf<String, Conversation>()

        fun emitMerged() {
            val sorted = convMap.values.toList().sortedByDescending { it.lastMessageTimestamp }
            trySend(sorted)
        }

        // Listener 1: 'conversations'
        val listenerConv = conversationsRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("ChatRepository", "Listen conversations failed: ${error.message}")
                return@addSnapshotListener
            }
            snapshot?.documents?.forEach { doc ->
                val data = doc.data ?: return@forEach
                val participants = extractParticipantsList(data)
                if (participants.contains(currentUserId) || participants.isEmpty()) {
                    convMap[doc.id] = parseConversation(doc.id, data)
                }
            }
            emitMerged()
        }

        // Listener 2: 'chats' (supports existing previous Flutter / Firebase apps)
        val listenerChats = chatsRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("ChatRepository", "Listen chats failed: ${error.message}")
                return@addSnapshotListener
            }
            snapshot?.documents?.forEach { doc ->
                val data = doc.data ?: return@forEach
                val participants = extractParticipantsList(data)
                if (participants.contains(currentUserId) || participants.isEmpty()) {
                    if (!convMap.containsKey(doc.id) || parseConversation(doc.id, data).lastMessageTimestamp > (convMap[doc.id]?.lastMessageTimestamp ?: 0L)) {
                        convMap[doc.id] = parseConversation(doc.id, data)
                    }
                }
            }
            emitMerged()
        }

        awaitClose {
            listenerConv.remove()
            listenerChats.remove()
        }
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

        val messageMap = mutableMapOf<String, ChatMessage>()

        fun emitSorted() {
            val sorted = messageMap.values.toList().sortedBy { it.timestamp }
            trySend(sorted)
        }

        // 1. Listen under conversations/{id}/messages
        val listenerConv = conversationsRef.document(conversationId)
            .collection("messages")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("ChatRepository", "Listen conv messages failed: ${error.message}")
                    return@addSnapshotListener
                }
                snapshot?.documents?.forEach { doc ->
                    val data = doc.data ?: return@forEach
                    messageMap[doc.id] = parseMessage(doc.id, conversationId, data)
                }
                emitSorted()
            }

        // 2. Also listen under chats/{id}/messages
        val listenerChats = chatsRef.document(conversationId)
            .collection("messages")
            .addSnapshotListener { snapshot, error ->
                if (error == null) {
                    snapshot?.documents?.forEach { doc ->
                        val data = doc.data ?: return@forEach
                        messageMap[doc.id] = parseMessage(doc.id, conversationId, data)
                    }
                    emitSorted()
                }
            }

        awaitClose {
            listenerConv.remove()
            listenerChats.remove()
        }
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
            val senderName = currentUser.displayName.ifBlank { currentUser.username }

            val messageMap = mapOf(
                "id" to msgId,
                "conversationId" to conversationId,
                "senderId" to currentUser.uid,
                "sender_id" to currentUser.uid,
                "senderUid" to currentUser.uid,
                "senderName" to senderName,
                "senderPhotoUrl" to currentUser.photoUrl,
                "text" to text,
                "message" to text,
                "content" to text,
                "mediaUrl" to mediaUrl,
                "media_url" to mediaUrl,
                "imageUrl" to mediaUrl,
                "mediaType" to mediaType,
                "type" to mediaType,
                "voiceDurationSeconds" to voiceDurationSeconds,
                "timestamp" to now,
                "time" to now,
                "createdAt" to now,
                "status" to "sent",
                "replyToMessageId" to (replyTo?.id ?: ""),
                "replyToText" to (replyTo?.text ?: ""),
                "replyToSender" to (replyTo?.senderName ?: ""),
                "deletedForEveryone" to false,
                "deletedForUsers" to emptyList<String>()
            )

            // Save under conversations
            conversationsRef.document(conversationId)
                .collection("messages")
                .document(msgId)
                .set(messageMap)
                .await()

            // Save under chats as well for backward compatibility
            try {
                chatsRef.document(conversationId)
                    .collection("messages")
                    .document(msgId)
                    .set(messageMap)
                    .await()
            } catch (e: Exception) {
                // Ignore if chats collection has different rules
            }

            val previewText = when (mediaType) {
                "image" -> "📷 Photo"
                "video" -> "🎥 Video"
                "voice" -> "🎤 Voice message ($voiceDurationSeconds s)"
                "audio" -> "🎵 Audio"
                else -> text
            }

            val updatePayload = mapOf(
                "lastMessageText" to previewText,
                "lastMessage" to previewText,
                "last_message" to previewText,
                "lastMessageTimestamp" to now,
                "timestamp" to now,
                "lastMessageSenderId" to currentUser.uid
            )

            conversationsRef.document(conversationId).set(updatePayload, SetOptions.merge()).await()
            chatsRef.document(conversationId).set(updatePayload, SetOptions.merge()).await()

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

    suspend fun deleteMessageForEveryone(conversationId: String, messageId: String): Result<Unit> {
        return try {
            val updates = mapOf(
                "deletedForEveryone" to true,
                "text" to "This message was deleted",
                "message" to "This message was deleted",
                "mediaUrl" to ""
            )
            conversationsRef.document(conversationId).collection("messages").document(messageId).update(updates).await()
            chatsRef.document(conversationId).collection("messages").document(messageId).update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteMessageForMe(conversationId: String, messageId: String, currentUserId: String): Result<Unit> {
        return try {
            conversationsRef.document(conversationId).collection("messages").document(messageId)
                .update("deletedForUsers", FieldValue.arrayUnion(currentUserId)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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

    companion object {
        fun extractParticipantsList(data: Map<String, Any?>): List<String> {
            val fromParticipants = data["participants"] as? List<*>
            if (fromParticipants != null && fromParticipants.isNotEmpty()) {
                return fromParticipants.filterIsInstance<String>()
            }
            val fromUsers = data["users"] as? List<*>
            if (fromUsers != null && fromUsers.isNotEmpty()) {
                return fromUsers.filterIsInstance<String>()
            }
            val fromMembers = data["members"] as? List<*>
            if (fromMembers != null && fromMembers.isNotEmpty()) {
                return fromMembers.filterIsInstance<String>()
            }
            val fromIds = data["participantIds"] as? List<*>
            if (fromIds != null && fromIds.isNotEmpty()) {
                return fromIds.filterIsInstance<String>()
            }
            return emptyList()
        }

        fun parseConversation(id: String, data: Map<String, Any?>): Conversation {
            val participants = extractParticipantsList(data)
            @Suppress("UNCHECKED_CAST")
            val participantNames = (data["participantNames"] as? Map<String, String>)
                ?: (data["userNames"] as? Map<String, String>)
                ?: emptyMap()

            @Suppress("UNCHECKED_CAST")
            val participantPhotos = (data["participantPhotos"] as? Map<String, String>)
                ?: (data["userPhotos"] as? Map<String, String>)
                ?: emptyMap()

            val lastText = (data["lastMessageText"] as? String)
                ?: (data["lastMessage"] as? String)
                ?: (data["last_message"] as? String)
                ?: (data["message"] as? String)
                ?: ""

            val lastTime = parseTimestamp(
                data["lastMessageTimestamp"] ?: data["timestamp"] ?: data["lastMessageTime"] ?: data["updatedAt"] ?: data["time"]
            )

            val groupName = (data["groupName"] as? String)
                ?: (data["name"] as? String)
                ?: (data["group_name"] as? String)
                ?: ""

            return Conversation(
                id = id,
                isGroup = data["isGroup"] as? Boolean ?: (groupName.isNotBlank() && participants.size > 2),
                participants = participants,
                participantNames = participantNames,
                participantPhotos = participantPhotos,
                lastMessageText = lastText,
                lastMessageTimestamp = lastTime,
                lastMessageSenderId = (data["lastMessageSenderId"] as? String) ?: (data["senderId"] as? String) ?: "",
                groupName = groupName,
                groupPhotoUrl = extractPhotoUrl(data),
                groupDescription = (data["groupDescription"] as? String) ?: (data["description"] as? String) ?: "",
                createdBy = (data["createdBy"] as? String) ?: ""
            )
        }

        fun parseMessage(docId: String, convId: String, data: Map<String, Any?>): ChatMessage {
            val text = (data["text"] as? String)
                ?: (data["message"] as? String)
                ?: (data["content"] as? String)
                ?: (data["body"] as? String)
                ?: (data["msg"] as? String)
                ?: ""

            val mediaUrl = (data["mediaUrl"] as? String)
                ?: (data["media_url"] as? String)
                ?: (data["imageUrl"] as? String)
                ?: (data["attachmentUrl"] as? String)
                ?: (data["url"] as? String)
                ?: ""

            val mediaType = (data["mediaType"] as? String)
                ?: (data["type"] as? String)
                ?: if (mediaUrl.isNotEmpty()) "image" else "text"

            val senderId = (data["senderId"] as? String)
                ?: (data["sender_id"] as? String)
                ?: (data["senderUid"] as? String)
                ?: (data["uid"] as? String)
                ?: ""

            val timestamp = parseTimestamp(data["timestamp"] ?: data["time"] ?: data["createdAt"])

            @Suppress("UNCHECKED_CAST")
            val deletedForUsers = data["deletedForUsers"] as? List<String> ?: emptyList()

            return ChatMessage(
                id = docId,
                conversationId = convId,
                senderId = senderId,
                senderName = (data["senderName"] as? String) ?: (data["sender_name"] as? String) ?: "",
                senderPhotoUrl = extractPhotoUrl(data),
                text = text,
                mediaUrl = mediaUrl,
                mediaType = mediaType,
                voiceDurationSeconds = (data["voiceDurationSeconds"] as? Number)?.toInt() ?: 0,
                timestamp = timestamp,
                status = (data["status"] as? String) ?: "delivered",
                replyToMessageId = (data["replyToMessageId"] as? String) ?: "",
                replyToText = (data["replyToText"] as? String) ?: "",
                replyToSender = (data["replyToSender"] as? String) ?: "",
                deletedForEveryone = data["deletedForEveryone"] as? Boolean ?: false,
                deletedForUsers = deletedForUsers
            )
        }
    }
}
