package com.example.data.repository

import android.util.Log
import com.example.data.model.UserProfile
import com.example.data.remote.FirebaseManager
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class UserRepository {

    private val db = FirebaseManager.firestore
    private val usersRef = db.collection("users")

    suspend fun saveUserProfile(profile: UserProfile): Result<Unit> {
        return try {
            usersRef.document(profile.uid)
                .set(profile.toMap(), SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("UserRepository", "Error saving user profile: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun getUserProfile(uid: String): Flow<UserProfile?> = callbackFlow {
        if (uid.isEmpty()) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val listener = usersRef.document(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("UserRepository", "Listen failed: ${error.message}")
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val data = snapshot.data ?: emptyMap<String, Any>()
                val profile = UserProfile(
                    uid = snapshot.id,
                    username = data["username"] as? String ?: "",
                    displayName = data["displayName"] as? String ?: "",
                    email = data["email"] as? String ?: "",
                    phoneNumber = data["phoneNumber"] as? String ?: "",
                    photoUrl = data["photoUrl"] as? String ?: "",
                    about = data["about"] as? String ?: "Hey there! I am using NexChat Pro.",
                    isOnline = data["isOnline"] as? Boolean ?: false,
                    lastSeen = (data["lastSeen"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                    createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                )
                trySend(profile)
            } else {
                trySend(null)
            }
        }
        awaitClose { listener.remove() }
    }

    suspend fun setUserOnline(isOnline: Boolean) {
        val uid = FirebaseManager.currentUserId ?: return
        try {
            val updates = mapOf(
                "isOnline" to isOnline,
                "lastSeen" to System.currentTimeMillis()
            )
            usersRef.document(uid).update(updates).await()
        } catch (e: Exception) {
            Log.w("UserRepository", "Update presence error: ${e.message}")
        }
    }

    suspend fun searchUsers(query: String): List<UserProfile> {
        if (query.isBlank()) return emptyList()
        val currentUid = FirebaseManager.currentUserId
        return try {
            val normalized = query.trim().lowercase()
            val snapshot = usersRef.get().await()
            val results = mutableListOf<UserProfile>()
            for (doc in snapshot.documents) {
                if (doc.id == currentUid) continue
                val data = doc.data ?: continue
                val username = (data["username"] as? String ?: "").lowercase()
                val displayName = (data["displayName"] as? String ?: "").lowercase()
                val email = (data["email"] as? String ?: "").lowercase()
                if (username.contains(normalized) || displayName.contains(normalized) || email.contains(normalized)) {
                    results.add(
                        UserProfile(
                            uid = doc.id,
                            username = data["username"] as? String ?: "",
                            displayName = data["displayName"] as? String ?: "",
                            email = data["email"] as? String ?: "",
                            phoneNumber = data["phoneNumber"] as? String ?: "",
                            photoUrl = data["photoUrl"] as? String ?: "",
                            about = data["about"] as? String ?: "",
                            isOnline = data["isOnline"] as? Boolean ?: false,
                            lastSeen = (data["lastSeen"] as? Number)?.toLong() ?: 0L
                        )
                    )
                }
            }
            results
        } catch (e: Exception) {
            Log.e("UserRepository", "Error searching users: ${e.message}", e)
            emptyList()
        }
    }
}
