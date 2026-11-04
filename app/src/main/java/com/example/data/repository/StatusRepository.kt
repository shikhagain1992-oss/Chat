package com.example.data.repository

import android.util.Log
import com.example.data.model.StatusStory
import com.example.data.model.UserProfile
import com.example.data.remote.FirebaseManager
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class StatusRepository {

    private val db = FirebaseManager.firestore
    private val statusRef = db.collection("statuses")

    suspend fun uploadStatus(
        user: UserProfile,
        mediaUrl: String,
        mediaType: String,
        caption: String
    ): Result<String> {
        return try {
            val statusId = UUID.randomUUID().toString()
            val status = StatusStory(
                id = statusId,
                userId = user.uid,
                userName = user.displayName.ifBlank { user.username },
                userPhotoUrl = user.photoUrl,
                mediaUrl = mediaUrl,
                mediaType = mediaType,
                caption = caption,
                createdAt = System.currentTimeMillis(),
                viewers = emptyList()
            )
            statusRef.document(statusId).set(status.toMap()).await()
            Result.success(statusId)
        } catch (e: Exception) {
            Log.e("StatusRepository", "Upload status error: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Real-time stream of non-expired statuses (within the last 24 hours)
     */
    fun getActiveStatusesFlow(): Flow<List<StatusStory>> = callbackFlow {
        val twentyFourHoursAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
        val listener = statusRef
            .whereGreaterThan("createdAt", twentyFourHoursAgo)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("StatusRepository", "Listen status failed: ${error.message}")
                    return@addSnapshotListener
                }
                val list = mutableListOf<StatusStory>()
                snapshot?.documents?.forEach { doc ->
                    val data = doc.data ?: return@forEach
                    @Suppress("UNCHECKED_CAST")
                    val viewers = data["viewers"] as? List<String> ?: emptyList()
                    list.add(
                        StatusStory(
                            id = doc.id,
                            userId = data["userId"] as? String ?: "",
                            userName = data["userName"] as? String ?: "",
                            userPhotoUrl = data["userPhotoUrl"] as? String ?: "",
                            mediaUrl = data["mediaUrl"] as? String ?: "",
                            mediaType = data["mediaType"] as? String ?: "image",
                            caption = data["caption"] as? String ?: "",
                            createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
                            viewers = viewers
                        )
                    )
                }
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun markStatusViewed(statusId: String, currentUserId: String) {
        if (currentUserId.isBlank()) return
        try {
            statusRef.document(statusId)
                .update("viewers", FieldValue.arrayUnion(currentUserId))
                .await()
        } catch (e: Exception) {
            Log.w("StatusRepository", "Error marking status viewed: ${e.message}")
        }
    }

    suspend fun deleteStatus(statusId: String): Result<Unit> {
        return try {
            statusRef.document(statusId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
