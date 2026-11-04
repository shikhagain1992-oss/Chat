package com.example.data.repository

import android.util.Log
import com.example.data.model.UserProfile
import com.example.data.remote.FirebaseManager
import com.google.firebase.Timestamp
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
            val map = mutableMapOf<String, Any>(
                "uid" to profile.uid,
                "username" to profile.username,
                "displayName" to profile.displayName,
                "display_name" to profile.displayName,
                "name" to profile.displayName,
                "email" to profile.email,
                "phoneNumber" to profile.phoneNumber,
                "phone_number" to profile.phoneNumber,
                "photoUrl" to profile.photoUrl,
                "photo_url" to profile.photoUrl,
                "profilePic" to profile.photoUrl,
                "profile_picture" to profile.photoUrl,
                "image" to profile.photoUrl,
                "avatarUrl" to profile.photoUrl,
                "about" to profile.about,
                "bio" to profile.about,
                "isOnline" to profile.isOnline,
                "lastSeen" to profile.lastSeen,
                "createdAt" to profile.createdAt
            )
            usersRef.document(profile.uid)
                .set(map, SetOptions.merge())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("UserRepository", "Error saving user profile: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun updatePhotoUrl(uid: String, photoUrl: String): Result<Unit> {
        return try {
            val updates = mapOf(
                "photoUrl" to photoUrl,
                "photo_url" to photoUrl,
                "profilePic" to photoUrl,
                "profile_picture" to photoUrl,
                "image" to photoUrl,
                "avatarUrl" to photoUrl
            )
            usersRef.document(uid).set(updates, SetOptions.merge()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("UserRepository", "Error updating photo URL: ${e.message}", e)
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
                val photo = extractPhotoUrl(data)
                val displayName = (data["displayName"] as? String)
                    ?: (data["display_name"] as? String)
                    ?: (data["name"] as? String)
                    ?: (data["username"] as? String)
                    ?: ""

                val profile = UserProfile(
                    uid = snapshot.id,
                    username = data["username"] as? String ?: "",
                    displayName = displayName,
                    email = data["email"] as? String ?: "",
                    phoneNumber = (data["phoneNumber"] as? String) ?: (data["phone_number"] as? String) ?: "",
                    photoUrl = photo,
                    about = (data["about"] as? String) ?: (data["bio"] as? String) ?: "Hey there! I am using NexChat Pro.",
                    isOnline = data["isOnline"] as? Boolean ?: false,
                    lastSeen = parseTimestamp(data["lastSeen"]),
                    createdAt = parseTimestamp(data["createdAt"])
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
            usersRef.document(uid).set(updates, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w("UserRepository", "Update presence error: ${e.message}")
        }
    }

    suspend fun searchUsers(query: String): List<UserProfile> {
        val currentUid = FirebaseManager.currentUserId
        return try {
            val normalized = query.trim().lowercase()
            val snapshot = usersRef.get().await()
            val results = mutableListOf<UserProfile>()
            for (doc in snapshot.documents) {
                if (doc.id == currentUid) continue
                val data = doc.data ?: continue
                val username = (data["username"] as? String ?: "").lowercase()
                val displayName = ((data["displayName"] as? String) ?: (data["name"] as? String) ?: "").lowercase()
                val email = (data["email"] as? String ?: "").lowercase()
                val photo = extractPhotoUrl(data)

                if (normalized.isBlank() || username.contains(normalized) || displayName.contains(normalized) || email.contains(normalized)) {
                    results.add(
                        UserProfile(
                            uid = doc.id,
                            username = data["username"] as? String ?: "",
                            displayName = (data["displayName"] as? String) ?: (data["name"] as? String) ?: username,
                            email = data["email"] as? String ?: "",
                            phoneNumber = (data["phoneNumber"] as? String) ?: "",
                            photoUrl = photo,
                            about = (data["about"] as? String) ?: "",
                            isOnline = data["isOnline"] as? Boolean ?: false,
                            lastSeen = parseTimestamp(data["lastSeen"])
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

    companion object {
        fun extractPhotoUrl(data: Map<String, Any?>): String {
            return (data["photoUrl"] as? String)
                ?: (data["photo_url"] as? String)
                ?: (data["profilePic"] as? String)
                ?: (data["profile_picture"] as? String)
                ?: (data["profilePhoto"] as? String)
                ?: (data["avatarUrl"] as? String)
                ?: (data["avatar"] as? String)
                ?: (data["image"] as? String)
                ?: ""
        }

        fun parseTimestamp(value: Any?): Long {
            return when (value) {
                is Number -> value.toLong()
                is Timestamp -> value.toDate().time
                is java.util.Date -> value.time
                is String -> value.toLongOrNull() ?: System.currentTimeMillis()
                else -> System.currentTimeMillis()
            }
        }
    }
}
