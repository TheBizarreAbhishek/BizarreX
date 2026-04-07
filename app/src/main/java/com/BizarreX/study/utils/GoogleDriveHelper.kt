package com.BizarreX.study.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class DriveVideo(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val durationMs: Long = 0L
)

object GoogleDriveHelper {
    // 🔴 IMPORTANT: Paste the Apps Script Web App URL here!
    var APPS_SCRIPT_URL = "https://script.google.com/macros/s/AKfycbx0_EBwOv2rhULM4RxbLPZv4gA-OyQgJkmcQuLN5t77uFwO23GQylh3EtbH6_6IAcSwNA/exec"
    
    // Kept exclusively for ExoPlayer raw video streaming
    var API_KEY = "AIzaSyD2L1dmCu7f-DUgtTPWJHG3E4lgE18w5k8" 
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    var lastError: String? = null
    
    fun getCachedVideos(context: android.content.Context, subjectName: String): List<DriveVideo>? {
        val prefs = context.getSharedPreferences("bizarrex_study_cache", android.content.Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("subject_$subjectName", null) ?: return null
        return try {
            val arr = org.json.JSONArray(jsonStr)
            val list = mutableListOf<DriveVideo>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val dur = if (obj.has("dur")) obj.getLong("dur") else 0L
                list.add(DriveVideo(obj.getString("id"), obj.getString("title"), obj.getString("thumb"), dur))
            }
            list
        } catch (e: Exception) { null }
    }
    
    fun saveVideosToCache(context: android.content.Context, subjectName: String, videos: List<DriveVideo>) {
        val arr = org.json.JSONArray()
        for (v in videos) {
            val obj = org.json.JSONObject()
            obj.put("id", v.id)
            obj.put("title", v.title)
            obj.put("thumb", v.thumbnailUrl)
            obj.put("dur", v.durationMs)
            arr.put(obj)
        }
        val prefs = context.getSharedPreferences("bizarrex_study_cache", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("subject_$subjectName", arr.toString()).apply()
    }
    
    // Watch History Support Core
    fun saveWatchProgress(context: android.content.Context, videoId: String, currentPosMs: Long, durationMs: Long) {
        val prefs = context.getSharedPreferences("bizarrex_watch_history", android.content.Context.MODE_PRIVATE)
        // Only save if progression is realistic
        if (currentPosMs > 1000L) {
            prefs.edit()
                 .putLong("pos_$videoId", currentPosMs)
                 .putLong("dur_$videoId", durationMs)
                 .apply()
        }
    }
    
    fun getWatchProgress(context: android.content.Context, videoId: String): Pair<Long, Long> {
        val prefs = context.getSharedPreferences("bizarrex_watch_history", android.content.Context.MODE_PRIVATE)
        val pos = prefs.getLong("pos_$videoId", 0L)
        val dur = prefs.getLong("dur_$videoId", 0L)
        return Pair(pos, dur)
    }

    /**
     * Hits our custom Apps Script which bypasses Google API constraints.
     */
    suspend fun fetchVideosFromAppsScript(context: android.content.Context, subjectName: String): List<DriveVideo>? = withContext(Dispatchers.IO) {
        lastError = null
        
        if (APPS_SCRIPT_URL == "PASTE_YOUR_APPS_SCRIPT_URL_HERE") {
             lastError = "APPS_SCRIPT_URL is missing. Please paste it in GoogleDriveHelper.kt!"
             return@withContext null
        }
        
        try {
            val encodedSubject = URLEncoder.encode(subjectName, "UTF-8")
            val url = "$APPS_SCRIPT_URL?subject=$encodedSubject"
            
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                
                if (!response.isSuccessful) {
                    lastError = "Apps Script Error ${response.code}: $body"
                    return@withContext null
                }
                
                if (body.isNullOrEmpty()) {
                    lastError = "Apps Script returned empty body"
                    return@withContext null
                }
                
                val json = JSONObject(body)
                if (json.has("error")) {
                    lastError = "Script reported error: ${json.getString("error")}"
                    return@withContext null
                }
                
                if (json.has("success") && json.getBoolean("success")) {
                    val videosArray = json.getJSONArray("videos")
                    val videos = mutableListOf<DriveVideo>()
                    for (i in 0 until videosArray.length()) {
                        val vObj = videosArray.getJSONObject(i)
                        val id = vObj.getString("id")
                        val title = vObj.getString("title")
                        
                        // Use official drive thumbnail generator 
                        val thumbUrl = "https://drive.google.com/thumbnail?id=$id&sz=w1000-h1080"
                        
                        // Parse pre-emptive Drive API durations secretly into the database
                        var finalDur = 0L
                        if (vObj.has("duration")) {
                            val realDur = vObj.getLong("duration")
                            if (realDur > 0L) {
                                finalDur = realDur
                                val prefs = context.getSharedPreferences("bizarrex_watch_history", android.content.Context.MODE_PRIVATE)
                                val existingPos = prefs.getLong("pos_$id", 0L)
                                prefs.edit().putLong("pos_$id", existingPos).putLong("dur_$id", realDur).apply()
                            }
                        }
                        
                        videos.add(DriveVideo(id, title, thumbUrl, finalDur))
                    }
                    val sortedVideos = videos.sortedBy { it.title }
                    return@withContext sortedVideos
                }
                
                lastError = "Unknown Script Response: $body"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            lastError = "Exception: ${e.message}"
        }
        return@withContext null
    }
}
