package com.example.data.repository

import android.util.Log
import com.example.data.model.CallRecord
import com.example.data.model.UserProfile
import com.example.data.remote.FirebaseManager
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class CallRepository {

    private val db = FirebaseManager.firestore
    private val callsRef = db.collection("calls")

    /**
     * Initiates a new voice or video call with complete cross-platform signaling fields
     */
    suspend fun startCall(
        caller: UserProfile,
        receiver: UserProfile,
        isVideo: Boolean,
        platform: String = "android"
    ): Result<CallRecord> {
        val callerUid = caller.uid.ifBlank { FirebaseManager.currentUserId ?: "" }
        if (callerUid.isBlank() || receiver.uid.isBlank()) {
            return Result.failure(IllegalArgumentException("Caller or receiver UID is missing"))
        }
        return try {
            val callId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val call = CallRecord(
                id = callId,
                callerId = callerUid,
                callerName = caller.displayName.ifBlank { caller.username.ifBlank { "Caller" } },
                callerPhotoUrl = caller.photoUrl,
                receiverId = receiver.uid,
                receiverName = receiver.displayName.ifBlank { receiver.username.ifBlank { "Contact" } },
                receiverPhotoUrl = receiver.photoUrl,
                isVideo = isVideo,
                callType = if (isVideo) "video" else "audio",
                platform = platform,
                status = "ringing",
                timestamp = now,
                createdAt = now,
                durationSeconds = 0,
                channelName = "channel_$callId"
            )
            callsRef.document(callId).set(call.toMap()).await()
            Result.success(call)
        } catch (e: Exception) {
            Log.e("CallRepository", "Error starting call: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Listens to a specific call's state changes (initiating, ringing, accepted, connecting, connected, ended, rejected, cancelled, missed, timeout, failed)
     */
    fun listenToCall(callId: String): Flow<CallRecord?> = callbackFlow {
        if (callId.isBlank()) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val listener = callsRef.document(callId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("CallRepository", "Listen call failed: ${error.message}")
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val data = snapshot.data ?: emptyMap<String, Any>()
                trySend(parseCallRecord(snapshot.id, data))
            } else {
                trySend(null)
            }
        }
        ChatRepository.registerActiveListener(listener)
        awaitClose {
            listener.remove()
            ChatRepository.unregisterActiveListener(listener)
        }
    }

    /**
     * Atomically accept an incoming call only if it is currently 'ringing' or 'initiating'
     * Prevents accepting the same call twice.
     */
    suspend fun acceptCallSafely(callId: String): Result<Boolean> {
        if (callId.isBlank()) return Result.failure(IllegalArgumentException("Empty callId"))
        return try {
            val docRef = callsRef.document(callId)
            val snap = docRef.get().await()
            val currentStatus = snap.getString("status") ?: ""
            if (currentStatus != "ringing" && currentStatus != "initiating" && currentStatus != "accepted") {
                return Result.failure(IllegalStateException("Call is no longer ringing (status: $currentStatus)"))
            }
            val now = System.currentTimeMillis()
            docRef.update(
                mapOf(
                    "status" to "accepted",
                    "acceptedAt" to now
                )
            ).await()
            Result.success(true)
        } catch (e: Exception) {
            Log.w("CallRepository", "Accept call error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Update call status (accepted, connecting, connected, rejected, cancelled, missed, timeout, ended, failed)
     */
    suspend fun updateCallStatus(
        callId: String,
        newStatus: String,
        durationSeconds: Int = 0,
        failureReason: String = ""
    ) {
        if (callId.isBlank()) return
        try {
            val now = System.currentTimeMillis()
            val updates = mutableMapOf<String, Any>("status" to newStatus)
            if (newStatus == "accepted") {
                updates["acceptedAt"] = now
            }
            if (newStatus in listOf("ended", "rejected", "cancelled", "missed", "timeout", "failed")) {
                updates["endedAt"] = now
            }
            if (durationSeconds > 0) {
                updates["durationSeconds"] = durationSeconds
                updates["duration"] = durationSeconds
            }
            if (failureReason.isNotBlank()) {
                updates["failureReason"] = failureReason
            }
            callsRef.document(callId).set(updates, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w("CallRepository", "Error updating call status: ${e.message}")
        }
    }

    /**
     * Stream of call history for current user
     */
    fun getCallHistoryFlow(currentUserId: String): Flow<List<CallRecord>> = callbackFlow {
        if (currentUserId.isEmpty()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = callsRef
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("CallRepository", "Listen history failed: ${error.message}")
                    return@addSnapshotListener
                }
                val calls = mutableListOf<CallRecord>()
                snapshot?.documents?.forEach { doc ->
                    val data = doc.data ?: return@forEach
                    val callerId = (data["callerId"] as? String) ?: (data["callerUid"] as? String) ?: ""
                    val receiverId = (data["receiverId"] as? String) ?: (data["receiverUid"] as? String) ?: ""
                    if (callerId == currentUserId || receiverId == currentUserId) {
                        calls.add(parseCallRecord(doc.id, data))
                    }
                }
                trySend(calls)
            }

        ChatRepository.registerActiveListener(listener)
        awaitClose {
            listener.remove()
            ChatRepository.unregisterActiveListener(listener)
        }
    }

    /**
     * Listen for incoming calls for this user (filters out stale calls older than 60 seconds)
     */
    fun listenForIncomingCalls(currentUserId: String): Flow<CallRecord?> = callbackFlow {
        if (currentUserId.isEmpty()) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val listener = callsRef
            .whereEqualTo("receiverId", currentUserId)
            .whereEqualTo("status", "ringing")
            .limit(1)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val firstDoc = snapshot?.documents?.firstOrNull()
                if (firstDoc != null) {
                    val data = firstDoc.data ?: return@addSnapshotListener
                    val record = parseCallRecord(firstDoc.id, data)
                    val ageMs = System.currentTimeMillis() - record.timestamp
                    if (record.timestamp <= 0L || ageMs < 60_000L) {
                        trySend(record)
                    } else {
                        trySend(null)
                    }
                } else {
                    trySend(null)
                }
            }
        ChatRepository.registerActiveListener(listener)
        awaitClose {
            listener.remove()
            ChatRepository.unregisterActiveListener(listener)
        }
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun parseCallRecord(id: String, data: Map<String, Any?>): CallRecord {
            val isVid = (data["isVideo"] as? Boolean) ?: ((data["callType"] as? String) == "video")
            val ts = (data["timestamp"] as? Number)?.toLong()
                ?: (data["createdAt"] as? Number)?.toLong()
                ?: 0L
            val dur = (data["durationSeconds"] as? Number)?.toInt()
                ?: (data["duration"] as? Number)?.toInt()
                ?: 0
            return CallRecord(
                id = id,
                callerId = (data["callerId"] as? String) ?: (data["callerUid"] as? String) ?: "",
                callerName = (data["callerName"] as? String) ?: "Caller",
                callerPhotoUrl = (data["callerPhotoUrl"] as? String) ?: "",
                receiverId = (data["receiverId"] as? String) ?: (data["receiverUid"] as? String) ?: "",
                receiverName = (data["receiverName"] as? String) ?: "Contact",
                receiverPhotoUrl = (data["receiverPhotoUrl"] as? String) ?: "",
                isVideo = isVid,
                callType = (data["callType"] as? String) ?: if (isVid) "video" else "audio",
                platform = (data["platform"] as? String) ?: "android",
                status = (data["status"] as? String) ?: "ringing",
                timestamp = ts,
                createdAt = (data["createdAt"] as? Number)?.toLong() ?: ts,
                acceptedAt = (data["acceptedAt"] as? Number)?.toLong() ?: 0L,
                endedAt = (data["endedAt"] as? Number)?.toLong() ?: 0L,
                durationSeconds = dur,
                channelName = (data["channelName"] as? String) ?: (data["channelId"] as? String) ?: "channel_$id",
                failureReason = (data["failureReason"] as? String) ?: "",
                offer = data["offer"] as? Map<String, Any>,
                answer = data["answer"] as? Map<String, Any>
            )
        }
    }
}
