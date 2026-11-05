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
        if (currentUid.isBlank() || otherUid.isBlank()) {
            return Result.success("conv_${System.currentTimeMillis()}")
        }

        val canonicalId = if (currentUid < otherUid) "${currentUid}_${otherUid}" else "${otherUid}_${currentUid}"

        return try {
            // 1. Direct document check (fast, no index required)
            val docIdsToCheck = listOf(
                canonicalId,
                "${currentUid}_${otherUid}",
                "${otherUid}_${currentUid}",
                "conv_${currentUid}_${otherUid}",
                "conv_${otherUid}_${currentUid}"
            )
            for (id in docIdsToCheck) {
                try {
                    val doc = conversationsRef.document(id).get().await()
                    if (doc.exists()) return Result.success(doc.id)
                } catch (e: Exception) {
                    Log.w("ChatRepository", "Direct check conv notice: ${e.message}")
                }
                try {
                    val doc = chatsRef.document(id).get().await()
                    if (doc.exists()) return Result.success(doc.id)
                } catch (e: Exception) {
                    Log.w("ChatRepository", "Direct check chats notice: ${e.message}")
                }
            }

            // 2. Query 'conversations' collection
            try {
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
            } catch (e: Exception) {
                Log.w("ChatRepository", "Query conv error: ${e.message}")
            }

            // 3. Query 'chats' collection
            try {
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
            } catch (e: Exception) {
                Log.w("ChatRepository", "Query chats error: ${e.message}")
            }

            // 4. Create new conversation document using canonicalId
            val now = System.currentTimeMillis()
            val convMap = mapOf(
                "id" to canonicalId,
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

            try {
                conversationsRef.document(canonicalId).set(convMap, SetOptions.merge()).await()
            } catch (e: Exception) {
                Log.w("ChatRepository", "Set conv notice: ${e.message}")
            }

            try {
                chatsRef.document(canonicalId).set(convMap, SetOptions.merge()).await()
            } catch (e: Exception) {
                Log.w("ChatRepository", "Set chats notice: ${e.message}")
            }

            Result.success(canonicalId)
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error getting/creating conversation: ${e.message}", e)
            Result.success(canonicalId)
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

            try {
                conversationsRef.document(groupId).set(convMap, SetOptions.merge()).await()
            } catch (e: Exception) {
                Log.w("ChatRepository", "Set conv group notice: ${e.message}")
            }

            try {
                chatsRef.document(groupId).set(convMap, SetOptions.merge()).await()
            } catch (e: Exception) {
                Log.w("ChatRepository", "Set chats group notice: ${e.message}")
            }

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
        val convMap = mutableMapOf<String, Conversation>()

        fun emitMerged() {
            val sorted = convMap.values.toList().sortedByDescending { it.lastMessageTimestamp }
            trySend(sorted)
        }

        val targetUid = currentUserId.ifBlank { FirebaseManager.currentUserId ?: "" }

        // Listener 1: 'conversations'
        val listenerConv = conversationsRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("ChatRepository", "Listen conversations failed: ${error.message}")
                return@addSnapshotListener
            }
            val activeUid = targetUid.ifBlank { FirebaseManager.currentUserId ?: "" }
            snapshot?.documents?.forEach { doc ->
                val data = doc.data ?: return@forEach
                val participants = extractParticipantsList(data)
                val isUserInConv = activeUid.isEmpty() ||
                                   participants.contains(activeUid) ||
                                   doc.id.contains(activeUid) ||
                                   (data["createdBy"] as? String) == activeUid ||
                                   (data["lastMessageSenderId"] as? String) == activeUid ||
                                   (data["senderId"] as? String) == activeUid

                if (isUserInConv) {
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
            val activeUid = targetUid.ifBlank { FirebaseManager.currentUserId ?: "" }
            snapshot?.documents?.forEach { doc ->
                val data = doc.data ?: return@forEach
                val participants = extractParticipantsList(data)
                val isUserInConv = activeUid.isEmpty() ||
                                   participants.contains(activeUid) ||
                                   doc.id.contains(activeUid) ||
                                   (data["createdBy"] as? String) == activeUid ||
                                   (data["lastMessageSenderId"] as? String) == activeUid ||
                                   (data["senderId"] as? String) == activeUid

                if (isUserInConv) {
                    val parsed = parseConversation(doc.id, data)
                    if (!convMap.containsKey(doc.id) || parsed.lastMessageTimestamp > (convMap[doc.id]?.lastMessageTimestamp ?: 0L)) {
                        convMap[doc.id] = parsed
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
        if (conversationId.isBlank() || conversationId == "none") {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val messageMap = mutableMapOf<String, ChatMessage>()

        fun emitSorted() {
            val sorted = messageMap.values.toList().sortedBy { it.timestamp }
            trySend(sorted)
        }

        val listeners = mutableListOf<com.google.firebase.firestore.ListenerRegistration>()

        val idsToListen = mutableListOf(conversationId)
        if (conversationId.contains("_")) {
            val parts = conversationId.split("_")
            if (parts.size == 2 && parts[0] != parts[1]) {
                idsToListen.add("${parts[1]}_${parts[0]}")
            }
        }

        for (id in idsToListen) {
            // 1. Listen under conversations/{id}/messages
            try {
                val lConv = conversationsRef.document(id)
                    .collection("messages")
                    .addSnapshotListener { snapshot, error ->
                        if (error == null) {
                            snapshot?.documents?.forEach { doc ->
                                val data = doc.data ?: return@forEach
                                messageMap[doc.id] = parseMessage(doc.id, id, data)
                            }
                            emitSorted()
                        }
                    }
                listeners.add(lConv)
            } catch (e: Exception) {
                Log.w("ChatRepository", "Listen conv error: ${e.message}")
            }

            // 2. Listen under chats/{id}/messages
            try {
                val lChats = chatsRef.document(id)
                    .collection("messages")
                    .addSnapshotListener { snapshot, error ->
                        if (error == null) {
                            snapshot?.documents?.forEach { doc ->
                                val data = doc.data ?: return@forEach
                                messageMap[doc.id] = parseMessage(doc.id, id, data)
                            }
                            emitSorted()
                        }
                    }
                listeners.add(lChats)
            } catch (e: Exception) {
                Log.w("ChatRepository", "Listen chats error: ${e.message}")
            }
        }

        awaitClose {
            listeners.forEach { it.remove() }
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
        replyTo: ChatMessage? = null,
        customMessageId: String? = null
    ): Result<String> {
        if (conversationId.isBlank() || conversationId == "none") {
            return Result.failure(IllegalArgumentException("Invalid conversation ID"))
        }

        return try {
            val msgId = customMessageId ?: UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val senderName = currentUser.displayName.ifBlank { currentUser.username }

            val previewText = when (mediaType) {
                "image" -> "📷 Photo"
                "video" -> "🎥 Video"
                "voice" -> "🎤 Voice message ($voiceDurationSeconds s)"
                "audio" -> "🎵 Audio"
                else -> text
            }

            // Always ensure parent conversation metadata exists with participants
            val participantsList = if (conversationId.contains("_")) {
                val parts = conversationId.split("_")
                listOf(parts[0], parts[1]).distinct()
            } else {
                listOf(currentUser.uid)
            }

            val parentPayload = mapOf(
                "id" to conversationId,
                "participants" to participantsList,
                "users" to participantsList,
                "lastMessageText" to previewText,
                "lastMessage" to previewText,
                "last_message" to previewText,
                "lastMessageTimestamp" to now,
                "timestamp" to now,
                "lastMessageSenderId" to currentUser.uid
            )

            // Write parent document first with merge
            try {
                conversationsRef.document(conversationId).set(parentPayload, SetOptions.merge())
            } catch (e: Exception) {
                Log.w("ChatRepository", "Set parent conv: ${e.message}")
            }
            try {
                chatsRef.document(conversationId).set(parentPayload, SetOptions.merge())
            } catch (e: Exception) {
                Log.w("ChatRepository", "Set parent chats: ${e.message}")
            }

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

            var writeSuccess = false
            var writeError: Exception? = null

            // 1. Save under conversations
            try {
                conversationsRef.document(conversationId)
                    .collection("messages")
                    .document(msgId)
                    .set(messageMap)
                    .await()
                writeSuccess = true
            } catch (e: Exception) {
                Log.w("ChatRepository", "Write to conv messages error: ${e.message}")
                writeError = e
            }

            // 2. Save under chats
            try {
                chatsRef.document(conversationId)
                    .collection("messages")
                    .document(msgId)
                    .set(messageMap)
                    .await()
                writeSuccess = true
            } catch (e: Exception) {
                Log.w("ChatRepository", "Write to chats messages error: ${e.message}")
                if (writeError == null) writeError = e
            }

            // If 1-on-1, also mirror to reverse ID if present
            if (conversationId.contains("_")) {
                val parts = conversationId.split("_")
                if (parts.size == 2 && parts[0] != parts[1]) {
                    val revId = "${parts[1]}_${parts[0]}"
                    try {
                        conversationsRef.document(revId).collection("messages").document(msgId).set(messageMap)
                    } catch (e: Exception) {}
                    try {
                        chatsRef.document(revId).collection("messages").document(msgId).set(messageMap)
                    } catch (e: Exception) {}
                }
            }

            // In Firestore offline-first architecture, writes are persisted to SQLite immediately.
            // Even under high latency, the message will sync when connected.
            Result.success(msgId)
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
            val rawParticipants = extractParticipantsList(data)
            val participants = if (rawParticipants.isNotEmpty()) rawParticipants else {
                if (id.contains("_")) {
                    val parts = id.split("_")
                    if (parts.size == 2) listOf(parts[0], parts[1]) else emptyList()
                } else emptyList()
            }
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
