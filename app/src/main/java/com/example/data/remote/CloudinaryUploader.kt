package com.example.data.remote

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object CloudinaryUploader {

    private const val TAG = "CloudinaryUploader"
    private const val CLOUD_NAME = "emoxfdx6"
    private const val UPLOAD_PRESET = "ml_default"
    private const val UPLOAD_URL = "https://api.cloudinary.com/v1_1/$CLOUD_NAME/auto/upload"

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /**
     * Uploads a file to Cloudinary using unsigned preset 'ml_default'.
     * Supports files up to 1 GB.
     */
    suspend fun uploadFile(
        file: File,
        mimeType: String = "auto"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val resolvedMime = if (mimeType.isBlank() || mimeType == "auto") {
                when (file.extension.lowercase()) {
                    "jpg", "jpeg" -> "image/jpeg"
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    "gif" -> "image/gif"
                    "mp4" -> "video/mp4"
                    "m4a", "aac", "mp3" -> "audio/mp4"
                    else -> "application/octet-stream"
                }
            } else {
                mimeType
            }

            val mediaType = resolvedMime.toMediaTypeOrNull()
            val requestFile = file.asRequestBody(mediaType)

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("upload_preset", UPLOAD_PRESET)
                .addFormDataPart("file", file.name, requestFile)
                .build()

            val request = Request.Builder()
                .url(UPLOAD_URL)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (response.isSuccessful && body != null) {
                    val json = JSONObject(body)
                    val secureUrl = json.getString("secure_url")
                    Log.d(TAG, "Uploaded to Cloudinary successfully: $secureUrl")
                    Result.success(secureUrl)
                } else {
                    val err = "Upload failed code=${response.code} body=$body"
                    Log.e(TAG, err)
                    Result.failure(Exception(err))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during upload: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Uploads media from content URI with proper file extension preservation.
     */
    suspend fun uploadUri(
        context: Context,
        uri: Uri,
        mimeType: String = "image/*"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val resolvedType = contentResolver.getType(uri) ?: mimeType
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(resolvedType)
                ?: if (resolvedType.startsWith("image")) "jpg"
                   else if (resolvedType.startsWith("video")) "mp4"
                   else if (resolvedType.startsWith("audio")) "m4a"
                   else "bin"

            val tempFile = File.createTempFile("upload_${System.currentTimeMillis()}_", ".$extension", context.cacheDir)
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            val result = uploadFile(tempFile, resolvedType)
            tempFile.delete()
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error reading URI: ${e.message}", e)
            Result.failure(e)
        }
    }
}
