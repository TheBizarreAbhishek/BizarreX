package com.BizarreX.study.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Float) : DownloadState()
    object Downloaded : DownloadState()
    data class Failed(val reason: String) : DownloadState()
}

object VideoDownloadManager {

    private val _states = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val states: StateFlow<Map<String, DownloadState>> = _states.asStateFlow()

    private val activeJobs = mutableMapOf<String, Job>()

    private val client = GoogleDriveHelper.videoClient

    // Sandboxed vault dir — never appears in Gallery, hidden from file managers
    private fun vaultDir(context: Context): File =
        File(context.filesDir, "bx_vault").apply { mkdirs() }

    private fun videoFile(context: Context, videoId: String): File =
        File(vaultDir(context), "$videoId.mp4")

    fun getState(videoId: String): DownloadState =
        _states.value[videoId] ?: if (false) DownloadState.Idle else DownloadState.Idle

    fun isDownloaded(context: Context, videoId: String): Boolean =
        videoFile(context, videoId).let { it.exists() && it.length() > 0 }

    fun getLocalFile(context: Context, videoId: String): File? =
        videoFile(context, videoId).takeIf { it.exists() && it.length() > 0 }

    /** Initialize state from disk on app boot */
    fun init(context: Context) {
        val downloaded = vaultDir(context).listFiles()
            ?.filter { it.name.endsWith(".mp4") && it.length() > 0 }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()

        val stateMap = downloaded.associate { id -> id to (DownloadState.Downloaded as DownloadState) }
        _states.value = stateMap
    }

    fun download(
        context: Context,
        videoId: String,
        streamUrl: String,
        scope: CoroutineScope
    ) {
        if (_states.value[videoId] is DownloadState.Downloading) return
        if (isDownloaded(context, videoId)) {
            setState(videoId, DownloadState.Downloaded)
            return
        }

        setState(videoId, DownloadState.Downloading(0f))

        val job = scope.launch(Dispatchers.IO) {
            val outputFile = videoFile(context, videoId)
            val tempFile = File(vaultDir(context), "$videoId.tmp")

            try {
                val request = Request.Builder()
                    .url(streamUrl)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        setState(videoId, DownloadState.Failed("Server error ${response.code}"))
                        return@launch
                    }

                    val body = response.body ?: run {
                        setState(videoId, DownloadState.Failed("Empty response"))
                        return@launch
                    }

                    val totalBytes = body.contentLength().takeIf { it > 0 } ?: -1L
                    var downloadedBytes = 0L

                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8192)
                        body.byteStream().use { input ->
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                if (!isActive) {
                                    // Cancelled
                                    tempFile.delete()
                                    setState(videoId, DownloadState.Idle)
                                    return@launch
                                }
                                output.write(buffer, 0, read)
                                downloadedBytes += read
                                if (totalBytes > 0) {
                                    val progress = downloadedBytes.toFloat() / totalBytes.toFloat()
                                    setState(videoId, DownloadState.Downloading(progress))
                                }
                            }
                        }
                    }

                    // Atomically move temp → final vault file
                    tempFile.renameTo(outputFile)
                    setState(videoId, DownloadState.Downloaded)
                    Log.d("VideoDownloadMgr", "✅ Downloaded: $videoId (${outputFile.length() / 1_000_000}MB)")
                }
            } catch (e: CancellationException) {
                tempFile.delete()
                setState(videoId, DownloadState.Idle)
            } catch (e: Exception) {
                tempFile.delete()
                Log.e("VideoDownloadMgr", "Download failed: ${e.message}")
                setState(videoId, DownloadState.Failed(e.message ?: "Unknown error"))
            }
        }

        activeJobs[videoId] = job
    }

    fun cancelDownload(videoId: String) {
        activeJobs[videoId]?.cancel()
        activeJobs.remove(videoId)
        setState(videoId, DownloadState.Idle)
    }

    fun delete(context: Context, videoId: String) {
        cancelDownload(videoId)
        videoFile(context, videoId).delete()
        setState(videoId, DownloadState.Idle)
        Log.d("VideoDownloadMgr", "🗑️ Deleted vault file: $videoId")
    }

    private fun setState(videoId: String, state: DownloadState) {
        _states.value = _states.value.toMutableMap().apply { put(videoId, state) }
    }
}
