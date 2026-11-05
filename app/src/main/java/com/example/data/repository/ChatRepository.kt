package com.example.data.repository

import android.util.Log
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.UserProfile
import com.example.data.remote.FirebaseManager
import com.example.data.repository.UserRepository.Companion.extractPhotoUrl
import com.example.data.repository.UserRepository.Companion.parseTimestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ChatRepository {

    private val db = FirebaseManager.firestore
    private val conversationsRef = db.collection("conversations")
    private val chatsRef = db.collection("chats")

    /**
     * Canonical 1-on-1 conversation ID without prefix so Android & Web always match
     */
    fun getCanonicalOneToOneId(uid1: String, uid2: String): String {
        val clean1 = uid1.removePrefix("conv_").trim()
        val clean2 = uid2.removePrefix("conv_").trim()
        return if (clean1 < clean2) "${clean1}_${clean2}" else "${clean2}_${clean1}"
    }

    /**
     * Finds existing 1-on-1 conversation or creates a new canonical one in 'conversations'
     */
    suspend fun getOrCreateOneToOneConversation(
        currentUser: UserProfile,
        otherUser: UserProfile
    ): Result<String> {
        val currentUid = currentUser.uid.ifBlank { FirebaseManager.currentUserId ?: "" }
        val otherUid = otherUser.uid
        if (currentUid.isBlank() || otherUid.isBlank()) {
            return Result.failure(IllegalArgumentException("Invalid user UIDs for conversation"))
        }

        val canonicalId = getCanonicalOneToOneId(currentUid, otherUid)

        return try {
            // 1. Check canonical document first
            try {
                val doc = conversationsRef.document(canonicalId).get().await()
                if (doc.exists()) return Result.success(canonicalId)
            } catch (e: Exception) {
                Log.w("ChatRepository", "Direct check canonical conv notice: ${e.message}")
            }

            // 2. Check legacy IDs for backward compatibility
            val legacyIds = listOf(
                "${otherUid}_${currentUid}",
                "conv_${canonicalId}",
                "conv_${otherUid}_${currentUid}"
            ).distinct().filter { it != canonicalId }

            for (legacyId in legacyIds) {
                try {
                    val doc = conversationsRef.document(legacyId).get().await()
                    if (doc.exists()) return Result.success(doc.id)
                } catch (_: Exception) {}
                try {
                    val doc = chatsRef.document(legacyId).get().await()
                    if (doc.exists()) return Result.success(doc.id)
                } catch (_: Exception) {}
            }

            // 3. Create canonical conversation document
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
                "typingUsers" to mapOf(currentUid to false, otherUid to false),
                "createdBy" to currentUid,
                "createdAt" to now
            )

            conversationsRef.document(canonicalId).set(convMap, SetOptions.merge()).await()
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
            val groupId = "group_${UUID.randomUUID()}"
            val participants = mutableListOf(currentUser.uid)
            val participantNames = mutableMapOf(currentUser.uid to currentUser.displayName.ifBlank { currentUser.username })
            val participantPhotos = mutableMapOf(currentUser.uid to currentUser.photoUrl)

            selectedUsers.forEach { user ->
                if (!participants.contains(user.uid)) {
                    participants.add(user.uid)
                }
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
            Result.success(groupId)
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error creating group: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Real-time stream of a single conversation (used for typing indicator & metadata)
     */
    fun getConversationFlow(conversationId: String): Flow<Conversation?> = callbackFlow {
        if (conversationId.isBlank() || conversationId == "none") {
            trySend(null)
            close()
            return@callbackFlow
        }
        val listener = conversationsRef.document(conversationId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val data = snapshot.data
                if (data != null) {
                    trySend(parseConversation(snapshot.id, data))
                }
            }
        }
        awaitClose { listener.remove() }
    }

    /**
     * Update typing indicator in conversation document
     */
    suspend fun setTypingStatus(conversationId: String, userId: String, isTyping: Boolean) {
        if (conversationId.isBlank() || conversationId == "none" || userId.isBlank()) return
        try {
            conversationsRef.document(conversationId).set(
                mapOf("typingUsers" to mapOf(userId to isTyping)),
                SetOptions.merge()
            ).await()
        } catch (e: Exception) {
            Log.w("ChatRepository", "Typing status update notice: ${e.message}")
        }
    }

    /**
     * Real-time stream of all user conversations merging canonical 'conversations' and legacy 'chats'
     */
    fun getConversationsFlow(currentUserId: String): Flow<List<Conversation>> = callbackFlow {
        val convMap = ConcurrentHashMap<String, Conversation>()

        fun emitMerged() {
            // Deduplicate 1-on-1 conversations with same participant pair
            val deduplicated = mutableMapOf<String, Conversation>()
            for (conv in convMap.values) {
                val key = if (!conv.isGroup && conv.participants.size == 2) {
                    val p0 = conv.participants[0]
                    val p1 = conv.participants[1]
                    if (p0 < p1) "${p0}_${p1}" else "${p1}_${p0}"
                } else {
                    conv.id.removePrefix("conv_")
                }
                val existing = deduplicated[key]
                if (existing == null || conv.lastMessageTimestamp >= existing.lastMessageTimestamp) {
                    deduplicated[key] = conv
                }
            }
            val sorted = deduplicated.values.toList().sortedByDescending { it.lastMessageTimestamp }
            trySend(sorted)
        }

        val targetUid = currentUserId.ifBlank { FirebaseManager.currentUserId ?: "" }
        if (targetUid.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listenerConv = conversationsRef
            .whereArrayContains("participants", targetUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("ChatRepository", "Listen conversations failed: ${error.message}")
                    return@addSnapshotListener
                }
                snapshot?.documents?.forEach { doc ->
                    val data = doc.data ?: return@forEach
                    convMap[doc.id] = parseConversation(doc.id, data)
                }
                emitMerged()
            }

        val listenerChats = chatsRef
            .whereArrayContains("participants", targetUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("ChatRepository", "Listen chats failed: ${error.message}")
                    return@addSnapshotListener
                }
                snapshot?.documents?.forEach { doc ->
                    val data = doc.data ?: return@forEach
                    val parsed = parseConversation(doc.id, data)
                    val existing = convMap[doc.id]
                    if (existing == null || parsed.lastMessageTimestamp > existing.lastMessageTimestamp) {
                        convMap[doc.id] = parsed
                    }
                }
                emitMerged()
            }

        registerActiveListener(listenerConv)
        registerActiveListener(listenerChats)

        awaitClose {
            listenerConv.remove()
            listenerChats.remove()
            unregisterActiveListener(listenerConv)
            unregisterActiveListener(listenerChats)
        }
    }

    /**
     * Real-time stream of messages in a conversation (canonical + legacy read support)
     */
    fun getMessagesFlow(conversationId: String): Flow<List<ChatMessage>> = callbackFlow {
        if (conversationId.isBlank() || conversationId == "none") {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val messageMap = ConcurrentHashMap<String, ChatMessage>()

        fun emitSorted() {
            val sorted = messageMap.values.toList().sortedBy { it.timestamp }
            trySend(sorted)
        }

        val listeners = mutableListOf<ListenerRegistration>()
        val baseId = conversationId.removePrefix("conv_")
        val idsToListen = mutableSetOf(conversationId, baseId, "conv_$baseId")
        if (baseId.contains("_")) {
            val parts = baseId.split("_")
            if (parts.size == 2 && parts[0] != parts[1]) {
                val rev = "${parts[1]}_${parts[0]}"
                idsToListen.add(rev)
                idsToListen.add("conv_$rev")
            }
        }

        for (id in idsToListen) {
            try {
                val lConv = conversationsRef.document(id)
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
                listeners.add(lConv)
                registerActiveListener(lConv)
            } catch (e: Exception) {
                Log.w("ChatRepository", "Listen conv messages error: ${e.message}")
            }

            try {
                val lChats = chatsRef.document(id)
                    .collection("messages")
                    .addSnapshotListener { snapshot, error ->
                        if (error == null) {
                            snapshot?.documents?.forEach { doc ->
                                val data = doc.data ?: return@forEach
                                if (!messageMap.containsKey(doc.id)) {
                                    messageMap[doc.id] = parseMessage(doc.id, conversationId, data)
                                }
                            }
                            emitSorted()
                        }
                    }
                listeners.add(lChats)
                registerActiveListener(lChats)
            } catch (e: Exception) {
                Log.w("ChatRepository", "Listen chats messages error: ${e.message}")
            }
        }

        awaitClose {
            listeners.forEach {
                it.remove()
                unregisterActiveListener(it)
            }
        }
    }

    /**
     * Send message to canonical 'conversations/{conversationId}/messages/{msgId}'
     * Guarantees no duplicate messages on retry by reusing customMessageId.
     */
    suspend fun sendMessage(
        conversationId: String,
        currentUser: UserProfile,
        text: String,
        mediaUrl: String = "",
        mediaType: String = "text",
        voiceDurationSeconds: Int = 0,
        replyTo: ChatMessage? = null,
        customMessageId: String? = null,
        forwarded: Boolean = false
    ): Result<String> {
        val activeUid = currentUser.uid.ifBlank { FirebaseManager.currentUserId ?: "" }
        if (conversationId.isBlank() || conversationId == "none") {
            return Result.failure(IllegalArgumentException("Invalid conversation ID"))
        }
        if (activeUid.isBlank()) {
            return Result.failure(IllegalStateException("User is not authenticated"))
        }
        if (text.isBlank() && mediaUrl.isBlank()) {
            return Result.failure(IllegalArgumentException("Message cannot be empty"))
        }

        val msgId = customMessageId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        if (!inFlightMessageIds.add(msgId)) {
            return Result.success(msgId)
        }

        return try {
            val now = System.currentTimeMillis()
            val senderName = currentUser.displayName.ifBlank { currentUser.username.ifBlank { "User" } }

            val previewText = when (mediaType) {
                "image" -> "📷 Photo"
                "gif" -> "👾 GIF"
                "video" -> "🎥 Video"
                "voice" -> "🎤 Voice message (${voiceDurationSeconds}s)"
                "audio" -> "🎵 Audio"
                else -> text
            }

            val cleanConvId = conversationId.removePrefix("conv_")
            val participantsList = if (cleanConvId.contains("_") && !cleanConvId.startsWith("group_")) {
                val parts = cleanConvId.split("_")
                if (parts.size == 2) listOf(parts[0], parts[1]).distinct() else listOf(activeUid)
            } else {
                listOf(activeUid)
            }

            val parentPayload = mutableMapOf<String, Any>(
                "id" to conversationId,
                "lastMessageText" to previewText,
                "lastMessage" to previewText,
                "last_message" to previewText,
                "lastMessageTimestamp" to now,
                "timestamp" to now,
                "lastMessageSenderId" to activeUid,
                "typingUsers" to mapOf(activeUid to false)
            )
            if (participantsList.size >= 2) {
                parentPayload["participants"] = participantsList
                parentPayload["users"] = participantsList
            }

            val messageMap = mapOf(
                "id" to msgId,
                "conversationId" to conversationId,
                "senderId" to activeUid,
                "sender_id" to activeUid,
                "senderUid" to activeUid,
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
                "forwarded" to forwarded,
                "deletedForEveryone" to false,
                "deletedForUsers" to emptyList<String>()
            )

            // 1. Write parent conversation metadata
            conversationsRef.document(conversationId).set(parentPayload, SetOptions.merge()).await()

            // 2. Write canonical message document (await completion to verify real write success)
            conversationsRef.document(conversationId)
                .collection("messages")
                .document(msgId)
                .set(messageMap)
                .await()

            Result.success(msgId)
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error sending message [$msgId]: ${e.message}", e)
            Result.failure(e)
        } finally {
            inFlightMessageIds.remove(msgId)
        }
    }

    /**
     * Forward an existing message to another conversation
     */
    suspend fun forwardMessage(
        targetConversationId: String,
        currentUser: UserProfile,
        originalMessage: ChatMessage
    ): Result<String> {
        return sendMessage(
            conversationId = targetConversationId,
            currentUser = currentUser,
            text = originalMessage.text,
            mediaUrl = originalMessage.mediaUrl,
            mediaType = originalMessage.mediaType,
            voiceDurationSeconds = originalMessage.voiceDurationSeconds,
            forwarded = true
        )
    }

    /**
     * Marks unread messages as seen (or delivered)
     */
    suspend fun markMessagesAsSeen(conversationId: String, currentUserId: String, readReceiptsEnabled: Boolean = true) {
        if (conversationId.isBlank() || currentUserId.isBlank()) return
        try {
            val targetStatus = if (readReceiptsEnabled) "seen" else "delivered"
            val unread = conversationsRef.document(conversationId)
                .collection("messages")
                .whereNotEqualTo("senderId", currentUserId)
                .get()
                .await()

            val batch = db.batch()
            var hasUpdates = false
            for (doc in unread.documents) {
                val status = doc.getString("status")
                if (status != "seen" && status != targetStatus) {
                    batch.update(doc.reference, "status", targetStatus)
                    hasUpdates = true
                }
            }
            if (hasUpdates) {
                batch.commit().await()
            }
        } catch (e: Exception) {
            Log.w("ChatRepository", "Error marking seen: ${e.message}")
        }
    }

    suspend fun deleteMessageForEveryone(conversationId: String, messageId: String): Result<Unit> {
        return try {
            val updates = mapOf(
                "deletedForEveryone" to true,
                "text" to "🚫 This message was deleted",
                "message" to "🚫 This message was deleted",
                "mediaUrl" to "",
                "media_url" to "",
                "imageUrl" to ""
            )
            conversationsRef.document(conversationId).collection("messages").document(messageId).update(updates).await()
            try {
                chatsRef.document(conversationId).collection("messages").document(messageId).update(updates).await()
            } catch (_: Exception) {}
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
        private val inFlightMessageIds = ConcurrentHashMap.newKeySet<String>()
        private val activeListeners = ConcurrentHashMap.newKeySet<ListenerRegistration>()

        fun registerActiveListener(listener: ListenerRegistration) {
            activeListeners.add(listener)
        }

        fun unregisterActiveListener(listener: ListenerRegistration) {
            activeListeners.remove(listener)
        }

        /**
         * Cleans up all active Firestore listeners and in-flight state on account switch/logout
         */
        fun clearSessionCache() {
            activeListeners.forEach {
                try { it.remove() } catch (_: Exception) {}
            }
            activeListeners.clear()
            inFlightMessageIds.clear()
        }

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
            val cleanId = id.removePrefix("conv_")
            val participants = if (rawParticipants.isNotEmpty()) rawParticipants else {
                if (cleanId.contains("_") && !cleanId.startsWith("group_")) {
                    val parts = cleanId.split("_")
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

            @Suppress("UNCHECKED_CAST")
            val typingUsers = (data["typingUsers"] as? Map<String, Boolean>) ?: emptyMap()

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
                typingUsers = typingUsers,
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
                forwarded = data["forwarded"] as? Boolean ?: false,
                deletedForEveryone = data["deletedForEveryone"] as? Boolean ?: false,
                deletedForUsers = deletedForUsers
            )
        }
    }
}
