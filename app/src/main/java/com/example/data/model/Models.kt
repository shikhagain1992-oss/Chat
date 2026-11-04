package com.example.data.model

data class UserProfile(
    val uid: String = "",
    val username: String = "",
    val displayName: String = "",
    val email: String = "",
    val phoneNumber: String = "",
    val photoUrl: String = "",
    val about: String = "Hey there! I am using NexChat Pro.",
    val isOnline: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any> = mapOf(
        "uid" to uid,
        "username" to username,
        "displayName" to displayName,
        "email" to email,
        "phoneNumber" to phoneNumber,
        "photoUrl" to photoUrl,
        "about" to about,
        "isOnline" to isOnline,
        "lastSeen" to lastSeen,
        "createdAt" to createdAt
    )
}

data class ChatMessage(
    val id: String = "",
    val conversationId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderPhotoUrl: String = "",
    val text: String = "",
    val mediaUrl: String = "",
    val mediaType: String = "text", // "text", "image", "video", "audio", "voice"
    val voiceDurationSeconds: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "sent", // "sent", "delivered", "seen"
    val replyToMessageId: String = "",
    val replyToText: String = "",
    val replyToSender: String = "",
    val deletedForEveryone: Boolean = false,
    val deletedForUsers: List<String> = emptyList()
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "conversationId" to conversationId,
        "senderId" to senderId,
        "senderName" to senderName,
        "senderPhotoUrl" to senderPhotoUrl,
        "text" to text,
        "mediaUrl" to mediaUrl,
        "mediaType" to mediaType,
        "voiceDurationSeconds" to voiceDurationSeconds,
        "timestamp" to timestamp,
        "status" to status,
        "replyToMessageId" to replyToMessageId,
        "replyToText" to replyToText,
        "replyToSender" to replyToSender,
        "deletedForEveryone" to deletedForEveryone,
        "deletedForUsers" to deletedForUsers
    )
}

data class Conversation(
    val id: String = "",
    val isGroup: Boolean = false,
    val participants: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val participantPhotos: Map<String, String> = emptyMap(),
    val lastMessageText: String = "",
    val lastMessageTimestamp: Long = System.currentTimeMillis(),
    val lastMessageSenderId: String = "",
    val unreadCounts: Map<String, Long> = emptyMap(),
    val groupName: String = "",
    val groupPhotoUrl: String = "",
    val groupDescription: String = "",
    val createdBy: String = ""
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "isGroup" to isGroup,
        "participants" to participants,
        "participantNames" to participantNames,
        "participantPhotos" to participantPhotos,
        "lastMessageText" to lastMessageText,
        "lastMessageTimestamp" to lastMessageTimestamp,
        "lastMessageSenderId" to lastMessageSenderId,
        "unreadCounts" to unreadCounts,
        "groupName" to groupName,
        "groupPhotoUrl" to groupPhotoUrl,
        "groupDescription" to groupDescription,
        "createdBy" to createdBy
    )
}

data class StatusStory(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val userPhotoUrl: String = "",
    val mediaUrl: String = "",
    val mediaType: String = "image", // "image" or "video"
    val caption: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val viewers: List<String> = emptyList()
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "userId" to userId,
        "userName" to userName,
        "userPhotoUrl" to userPhotoUrl,
        "mediaUrl" to mediaUrl,
        "mediaType" to mediaType,
        "caption" to caption,
        "createdAt" to createdAt,
        "viewers" to viewers
    )
}

data class CallRecord(
    val id: String = "",
    val callerId: String = "",
    val callerName: String = "",
    val callerPhotoUrl: String = "",
    val receiverId: String = "",
    val receiverName: String = "",
    val receiverPhotoUrl: String = "",
    val isVideo: Boolean = false,
    val status: String = "ringing", // "ringing", "accepted", "rejected", "ended", "missed"
    val timestamp: Long = System.currentTimeMillis(),
    val durationSeconds: Int = 0,
    val channelName: String = ""
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "callerId" to callerId,
        "callerName" to callerName,
        "callerPhotoUrl" to callerPhotoUrl,
        "receiverId" to receiverId,
        "receiverName" to receiverName,
        "receiverPhotoUrl" to receiverPhotoUrl,
        "isVideo" to isVideo,
        "status" to status,
        "timestamp" to timestamp,
        "durationSeconds" to durationSeconds,
        "channelName" to channelName
    )
}
