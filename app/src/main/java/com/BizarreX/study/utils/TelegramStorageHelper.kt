package com.BizarreX.study.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

object TelegramStorageHelper {
    private val botToken get() = com.BizarreX.study.BuildConfig.TG_BOT_TOKEN
    private const val CHAT_ID_CONST = "" // overridden at runtime
    private const val BASE_URL = "https://api.telegram.org/bot"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .build()

    class ProgressRequestBody(
        private val file: File,
        private val mediaType: MediaType?,
        private val onProgress: (Float) -> Unit
    ) : RequestBody() {
        override fun contentType() = mediaType
        override fun contentLength() = file.length()
        override fun writeTo(sink: BufferedSink) {
            val fileLength = file.length()
            val buffer = ByteArray(8192)
            FileInputStream(file).use { input ->
                var uploaded = 0L
                var read: Int
                var lastProgress = 0f
                while (input.read(buffer).also { read = it } != -1) {
                    uploaded += read
                    sink.write(buffer, 0, read)
                    val progress = uploaded.toFloat() / fileLength.toFloat()
                    if (progress - lastProgress >= 0.05f || progress >= 1f) {
                        onProgress(progress)
                        lastProgress = progress
                    }
                }
            }
        }
    }

    suspend fun uploadFileWithProgress(
        context: Context, 
        uri: Uri, 
        type: String, // "image", "video", or "document"
        onProgress: (Float) -> Unit
    ): Pair<String, String?>? = withContext(Dispatchers.IO) {
        try {
            val tempFile = copyUriToTempFile(context, uri) ?: return@withContext null
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            
            val fieldName = when(type) {
                "video" -> "video"
                "image" -> "photo"
                else -> "document"
            }
            
            var safeFilename = tempFile.name
            if (type == "video" && !safeFilename.endsWith(".mp4", ignoreCase = true)) {
                safeFilename += ".mp4"
            } else if (type == "image" && !safeFilename.endsWith(".jpg", ignoreCase = true) && !safeFilename.endsWith(".png", ignoreCase = true)) {
                safeFilename += ".jpg"
            }
            
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", com.BizarreX.study.BuildConfig.TG_CHAT_ID)
                .addFormDataPart(
                    fieldName, 
                    safeFilename, 
                    ProgressRequestBody(tempFile, mimeType.toMediaTypeOrNull(), onProgress)
                )
                .build()

            val endpoint = when(type) {
                "video" -> "sendVideo"
                "image" -> "sendPhoto"
                else -> "sendDocument"
            }
            
            val request = Request.Builder()
                .url("$BASE_URL$botToken/$endpoint")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                Log.d("TgStorage", "Response: $responseBody")
                if (response.isSuccessful && responseBody != null) {
                    val json = JSONObject(responseBody)
                    if (json.getBoolean("ok")) {
                        val result = json.getJSONObject("result")
                        if (result.has("document")) {
                            return@withContext Pair(result.getJSONObject("document").getString("file_id"), null)
                        } else if (result.has("video")) {
                            val vid = result.getJSONObject("video")
                            var thumbId: String? = null
                            if (vid.has("thumbnail")) {
                                thumbId = vid.getJSONObject("thumbnail").getString("file_id")
                            } else if (vid.has("thumb")) {
                                thumbId = vid.getJSONObject("thumb").getString("file_id")
                            }
                            return@withContext Pair(vid.getString("file_id"), thumbId)
                        } else if (result.has("photo")) {
                            val photos = result.getJSONArray("photo")
                            val bestPhoto = photos.getJSONObject(photos.length() - 1)
                            return@withContext Pair(bestPhoto.getString("file_id"), null)
                        }
                    } else {
                        Log.e("TgStorage", "Tg Api Error: ${json.optString("description")}")
                    }
                } else {
                    Log.e("TgStorage", "HTTP Error: ${response.code} $responseBody")
                }
            }
            tempFile.delete()
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("TgStorage", "Upload Exception", e)
        }
        null
    }

    private val urlCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    suspend fun getDirectMediaUrl(context: Context, fileId: String, extension: String = "jpg"): String? = withContext(Dispatchers.IO) {
        val cacheFolder = File(context.cacheDir, "bx_media").apply { mkdirs() }
        val localFile = File(cacheFolder, "${fileId}.$extension")
        if (localFile.exists() && localFile.length() > 0) {
            return@withContext Uri.fromFile(localFile).toString()
        }

        if (urlCache.containsKey(fileId)) return@withContext urlCache[fileId]
        
        try {
            val url = "$BASE_URL$botToken/getFile?file_id=$fileId"
            val request = Request.Builder().url(url).build()
            
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (response.isSuccessful && body != null) {
                    val json = JSONObject(body)
                    if (json.getBoolean("ok")) {
                        val filePath = json.getJSONObject("result").getString("file_path")
                        val finalUrl = "https://api.telegram.org/file/bot$botToken/$filePath"
                        urlCache[fileId] = finalUrl
                        
                        // Silently download the file to cache directory in background for WhatsApp-style 0ms-delays next time
                        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                            try {
                                val downloadReq = Request.Builder().url(finalUrl).build()
                                client.newCall(downloadReq).execute().use { dlResp ->
                                    if (dlResp.isSuccessful) {
                                        dlResp.body?.byteStream()?.use { input ->
                                            FileOutputStream(localFile).use { output ->
                                                input.copyTo(output)
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                localFile.delete()
                                e.printStackTrace() 
                            }
                        }
                        
                        return@withContext finalUrl
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    fun copyUriToTempFile(context: Context, uri: Uri): File? {
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            val nameIndex = cursor?.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor?.moveToFirst()
            val originalName = if (nameIndex != null && nameIndex >= 0) cursor.getString(nameIndex) else "temp_media_${System.currentTimeMillis()}"
            cursor?.close()

            val tempFile = File(context.cacheDir, originalName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            return tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
