package com.example.data.repository

import android.util.Log
import com.example.data.model.CallRecord
import com.example.data.model.UserProfile
import com.example.data.remote.FirebaseManager
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class CallRepository {

    private val db = FirebaseManager.firestore
    private val callsRef = db.collection("calls")

    /**
     * Initiates a new voice or video call
     */
    suspend fun startCall(
        caller: UserProfile,
        receiver: UserProfile,
        isVideo: Boolean
    ): Result<CallRecord> {
        return try {
            val callId = UUID.randomUUID().toString()
            val call = CallRecord(
                id = callId,
                callerId = caller.uid,
                callerName = caller.displayName.ifBlank { caller.username },
                callerPhotoUrl = caller.photoUrl,
                receiverId = receiver.uid,
                receiverName = receiver.displayName.ifBlank { receiver.username },
                receiverPhotoUrl = receiver.photoUrl,
                isVideo = isVideo,
                status = "ringing",
                timestamp = System.currentTimeMillis(),
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
     * Listens to a specific call's state changes (ringing, accepted, ended, rejected)
     */
    fun listenToCall(callId: String): Flow<CallRecord?> = callbackFlow {
        if (callId.isEmpty()) {
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
                val record = CallRecord(
                    id = snapshot.id,
                    callerId = data["callerId"] as? String ?: "",
                    callerName = data["callerName"] as? String ?: "",
                    callerPhotoUrl = data["callerPhotoUrl"] as? String ?: "",
                    receiverId = data["receiverId"] as? String ?: "",
                    receiverName = data["receiverName"] as? String ?: "",
                    receiverPhotoUrl = data["receiverPhotoUrl"] as? String ?: "",
                    isVideo = data["isVideo"] as? Boolean ?: false,
                    status = data["status"] as? String ?: "ringing",
                    timestamp = (data["timestamp"] as? Number)?.toLong() ?: 0L,
                    durationSeconds = (data["durationSeconds"] as? Number)?.toInt() ?: 0,
                    channelName = data["channelName"] as? String ?: ""
                )
                trySend(record)
            } else {
                trySend(null)
            }
        }
        awaitClose { listener.remove() }
    }

    /**
     * Update call status (accepted, rejected, ended)
     */
    suspend fun updateCallStatus(callId: String, newStatus: String, durationSeconds: Int = 0) {
        try {
            val updates = mutableMapOf<String, Any>("status" to newStatus)
            if (durationSeconds > 0) {
                updates["durationSeconds"] = durationSeconds
            }
            callsRef.document(callId).update(updates).await()
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
                    val callerId = data["callerId"] as? String ?: ""
                    val receiverId = data["receiverId"] as? String ?: ""
                    if (callerId == currentUserId || receiverId == currentUserId) {
                        calls.add(
                            CallRecord(
                                id = doc.id,
                                callerId = callerId,
                                callerName = data["callerName"] as? String ?: "",
                                callerPhotoUrl = data["callerPhotoUrl"] as? String ?: "",
                                receiverId = receiverId,
                                receiverName = data["receiverName"] as? String ?: "",
                                receiverPhotoUrl = data["receiverPhotoUrl"] as? String ?: "",
                                isVideo = data["isVideo"] as? Boolean ?: false,
                                status = data["status"] as? String ?: "ended",
                                timestamp = (data["timestamp"] as? Number)?.toLong() ?: 0L,
                                durationSeconds = (data["durationSeconds"] as? Number)?.toInt() ?: 0,
                                channelName = data["channelName"] as? String ?: ""
                            )
                        )
                    }
                }
                trySend(calls)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Listen for incoming calls for this user
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
                    trySend(
                        CallRecord(
                            id = firstDoc.id,
                            callerId = data["callerId"] as? String ?: "",
                            callerName = data["callerName"] as? String ?: "",
                            callerPhotoUrl = data["callerPhotoUrl"] as? String ?: "",
                            receiverId = data["receiverId"] as? String ?: "",
                            receiverName = data["receiverName"] as? String ?: "",
                            receiverPhotoUrl = data["receiverPhotoUrl"] as? String ?: "",
                            isVideo = data["isVideo"] as? Boolean ?: false,
                            status = "ringing",
                            timestamp = (data["timestamp"] as? Number)?.toLong() ?: 0L
                        )
                    )
                } else {
                    trySend(null)
                }
            }
        awaitClose { listener.remove() }
    }
}
