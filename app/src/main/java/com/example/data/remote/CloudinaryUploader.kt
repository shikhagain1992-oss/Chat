package com.example.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object CloudinaryUploader {

    private const val TAG = "CloudinaryUploader"
    private const val CLOUD_NAME = "emoxfdx6"
    private const val UPLOAD_PRESET = "ml_default"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Uploads a file to Cloudinary using specific resource type endpoint
     * ('image/upload', 'video/upload', or 'auto/upload')
     */
    suspend fun uploadFile(
        file: File,
        mimeType: String = "image/jpeg"
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
                    else -> "image/jpeg"
                }
            } else {
                mimeType
            }

            // Cloudinary requires correct resource type in unsigned upload URL
            val resourceType = when {
                resolvedMime.startsWith("video") -> "video"
                resolvedMime.startsWith("audio") -> "video" // Cloudinary stores audio under video
                else -> "image"
            }

            val uploadUrl = "https://api.cloudinary.com/v1_1/$CLOUD_NAME/$resourceType/upload"

            val mediaType = resolvedMime.toMediaTypeOrNull()
            val requestFile = file.asRequestBody(mediaType)

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("upload_preset", UPLOAD_PRESET)
                .addFormDataPart("file", file.name, requestFile)
                .build()

            val request = Request.Builder()
                .url(uploadUrl)
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
            Log.e(TAG, "Exception during Cloudinary upload: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Uploads media from content URI with safe downsampling and fallback.
     */
    suspend fun uploadUri(
        context: Context,
        uri: Uri,
        mimeType: String = "image/*"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val resolvedType = try { contentResolver.getType(uri) ?: mimeType } catch (e: Exception) { mimeType }
            val isImage = resolvedType.startsWith("image") || mimeType.startsWith("image")

            val extension = if (isImage) "jpg"
                else if (resolvedType.startsWith("video")) "mp4"
                else if (resolvedType.startsWith("audio")) "m4a"
                else "jpg"

            val tempFile = File.createTempFile("nexchat_${System.currentTimeMillis()}_", ".$extension", context.cacheDir)

            if (isImage) {
                // Downsample image safely to prevent OutOfMemory and keep upload size under 600KB
                try {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    contentResolver.openInputStream(uri)?.use { input ->
                        BitmapFactory.decodeStream(input, null, options)
                    }

                    val maxDim = 1280
                    var sampleSize = 1
                    while ((options.outWidth / sampleSize) > maxDim || (options.outHeight / sampleSize) > maxDim) {
                        sampleSize *= 2
                    }

                    val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                    val bitmap = contentResolver.openInputStream(uri)?.use { input ->
                        BitmapFactory.decodeStream(input, null, decodeOptions)
                    }

                    if (bitmap != null) {
                        FileOutputStream(tempFile).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
                        }
                        bitmap.recycle()
                    } else {
                        // Fallback: direct stream copy
                        contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Image downsampling warning: ${e.message}")
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                    }
                }
            } else {
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output -> input.copyTo(output) }
                }
            }

            val result = uploadFile(tempFile, if (isImage) "image/jpeg" else resolvedType)
            tempFile.delete()

            if (result.isSuccess) {
                result
            } else if (isImage) {
                // Ultimate fallback for images: instant Base64 data URL
                val dataUrl = uriToCompressedDataUrl(context, uri)
                if (dataUrl != null) {
                    Log.d(TAG, "Cloudinary upload failed; used compressed data URL fallback")
                    Result.success(dataUrl)
                } else {
                    result
                }
            } else {
                result
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in uploadUri: ${e.message}", e)
            if (mimeType.startsWith("image")) {
                val dataUrl = uriToCompressedDataUrl(context, uri)
                if (dataUrl != null) Result.success(dataUrl) else Result.failure(e)
            } else {
                Result.failure(e)
            }
        }
    }

    /**
     * Converts an image URI into a compact compressed Base64 Data URL
     * for instant, 100% reliable local/Firestore profile photo storage.
     */
    suspend fun uriToCompressedDataUrl(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val original = BitmapFactory.decodeStream(input) ?: return@withContext null
                val maxDim = 320
                val ratio = minOf(maxDim.toFloat() / original.width, maxDim.toFloat() / original.height, 1f)
                val scaled = if (ratio < 1f) {
                    Bitmap.createScaledBitmap(
                        original,
                        (original.width * ratio).toInt(),
                        (original.height * ratio).toInt(),
                        true
                    )
                } else original

                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 75, out)
                val bytes = out.toByteArray()
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                "data:image/jpeg;base64,$base64"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing image to Data URL: ${e.message}", e)
            null
        }
    }
}
