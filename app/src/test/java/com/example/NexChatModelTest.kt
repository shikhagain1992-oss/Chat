package com.example

import com.example.data.model.CallRecord
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.StatusStory
import com.example.data.model.UserProfile
import com.example.data.repository.ChatRepository
import com.example.data.repository.UserRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NexChatModelTest {

    @Test
    fun testUserProfileToMap() {
        val user = UserProfile(
            uid = "user_123",
            username = "alex_pro",
            displayName = "Alex Rivera",
            email = "alex@nexchat.com",
            phoneNumber = "+1234567890",
            photoUrl = "https://res.cloudinary.com/demo/image/upload/sample.jpg",
            about = "Testing NexChat Pro",
            isOnline = true
        )

        val map = user.toMap()
        assertEquals("user_123", map["uid"])
        assertEquals("alex_pro", map["username"])
        assertEquals("Alex Rivera", map["displayName"])
        assertEquals(true, map["isOnline"])
    }

    @Test
    fun testChatMessageToMap() {
        val message = ChatMessage(
            id = "msg_001",
            conversationId = "conv_100",
            senderId = "user_123",
            senderName = "Alex",
            text = "Hello from NexChat!",
            status = "seen",
            mediaType = "text"
        )

        val map = message.toMap()
        assertEquals("msg_001", map["id"])
        assertEquals("Hello from NexChat!", map["text"])
        assertEquals("seen", map["status"])
    }

    @Test
    fun testStatusStoryToMap() {
        val story = StatusStory(
            id = "story_1",
            userId = "u1",
            userName = "Alex",
            mediaUrl = "https://example.com/status.jpg",
            mediaType = "image",
            caption = "Day 1 on NexChat!"
        )

        val map = story.toMap()
        assertEquals("story_1", map["id"])
        assertEquals("Day 1 on NexChat!", map["caption"])
    }

    @Test
    fun testCallRecordToMap() {
        val call = CallRecord(
            id = "call_99",
            callerId = "u1",
            callerName = "Alex",
            receiverId = "u2",
            receiverName = "Jordan",
            isVideo = true,
            status = "ringing"
        )

        val map = call.toMap()
        assertEquals("call_99", map["id"])
        assertEquals(true, map["isVideo"])
        assertEquals("ringing", map["status"])
    }

    @Test
    fun testExtractPhotoUrlAliases() {
        val mapWithPhotoUrl = mapOf("photoUrl" to "https://photo1.jpg")
        assertEquals("https://photo1.jpg", UserRepository.extractPhotoUrl(mapWithPhotoUrl))

        val mapWithSnakeCase = mapOf("photo_url" to "https://photo2.jpg")
        assertEquals("https://photo2.jpg", UserRepository.extractPhotoUrl(mapWithSnakeCase))

        val mapWithProfilePic = mapOf("profilePic" to "https://photo3.jpg")
        assertEquals("https://photo3.jpg", UserRepository.extractPhotoUrl(mapWithProfilePic))

        val mapWithImage = mapOf("image" to "https://photo4.jpg")
        assertEquals("https://photo4.jpg", UserRepository.extractPhotoUrl(mapWithImage))
    }

    @Test
    fun testExtractParticipantsAliases() {
        val withParticipants = mapOf("participants" to listOf("u1", "u2"))
        assertEquals(listOf("u1", "u2"), ChatRepository.extractParticipantsList(withParticipants))

        val withUsers = mapOf("users" to listOf("u3", "u4"))
        assertEquals(listOf("u3", "u4"), ChatRepository.extractParticipantsList(withUsers))

        val withMembers = mapOf("members" to listOf("u5", "u6"))
        assertEquals(listOf("u5", "u6"), ChatRepository.extractParticipantsList(withMembers))
    }

    @Test
    fun testChatExportFormatting() {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateStr = sdf.format(Date(1700000000000L))
        val output = "[$dateStr] Alex: Hello NexChat!"
        assertTrue(output.contains("Alex"))
        assertTrue(output.contains("Hello NexChat!"))
    }
}
