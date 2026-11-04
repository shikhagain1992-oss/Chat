package com.example.data.remote

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
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
     * Uploads a file to Cloudinary using the unsigned preset.
     * Supports images, videos, audio recordings, and documents up to 1 GB.
     */
    suspend fun uploadFile(
        file: File,
        mimeType: String = "auto"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val mediaType = mimeType.toMediaTypeOrNull()
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
                    Log.d(TAG, "Uploaded successfully: $secureUrl")
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
     * Uploads media from content URI (e.g. from photo picker).
     */
    suspend fun uploadUri(
        context: Context,
        uri: Uri,
        mimeType: String = "image/*"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val tempFile = File.createTempFile("upload_", ".tmp", context.cacheDir)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            val result = uploadFile(tempFile, mimeType)
            tempFile.delete()
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error reading URI: ${e.message}", e)
            Result.failure(e)
        }
    }
}
