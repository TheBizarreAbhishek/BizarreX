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

data class DriveFolder(
    val id: String,
    val name: String
)

data class FolderContents(
    val folderId: String,
    val folderName: String,
    val hasSubFolders: Boolean,
    val folders: List<DriveFolder>,
    val videos: List<DriveVideo>
)

object GoogleDriveHelper {
    // 🔴 IMPORTANT: Paste the Apps Script Web App URL here!
    var APPS_SCRIPT_URL = "https://script.google.com/macros/s/AKfycbx0_EBwOv2rhULM4RxbLPZv4gA-OyQgJkmcQuLN5t77uFwO23GQylh3EtbH6_6IAcSwNA/exec"
    
    // Kept exclusively for ExoPlayer raw video streaming
    var API_KEY = "AIzaSyD2L1dmCu7f-DUgtTPWJHG3E4lgE18w5k8"

    // Maps app display name → exact Google Drive folder name
    private val subjectFolderMap = mapOf(
        "Engg. Mathematics I"              to "Engg. Mathematics I",
        "Engg. Mathematics II"             to "Engg. Maths-II",
        "Engg. Physics"                    to "Engg. Physics",
        "Engg. Chemistry"                  to "Engg. Chemistry",
        "Electrical Engineering"           to "Electrical Engg.",
        "Electronics Engineering"          to "Electronics Engg. by Gulshan Sir",
        "Programming for Problem Solving"  to "Programming for Problem Solving(PPS)",
        "Fundamentals of Mech. Engg."      to "Fundamentals of Mech. Engg. (FME)",
        "Environment & Ecology"            to "Environment and Ecology",
        "Soft Skills"                      to "Soft Skills"
    )

    /** Converts app subject display name to actual Drive folder name */
    private fun driveFolderName(subjectName: String): String =
        subjectFolderMap[subjectName] ?: subjectName
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // Interceptor to bypass Google Drive's "File too large to scan for viruses" prompt silently
    class DriveVirusScanInterceptor : okhttp3.Interceptor {
        override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
            val request = chain.request()
            val response = chain.proceed(request)

            val hostStr = request.url.host
            if (hostStr.contains("google.com") || hostStr.contains("googleusercontent.com")) {
                val contentType = response.body?.contentType()
                if (contentType?.subtype == "html") {
                    val bodyString = response.peekBody(10 * 1024 * 1024).string()
                    val uuidRegex = Regex("""<input[^>]+name="uuid"[^>]+value="([^"]+)"""")
                    val confirmRegex = Regex("""<input[^>]+name="confirm"[^>]+value="([^"]+)"""")
                    
                    val matchUuid = uuidRegex.find(bodyString)
                    val matchConfirm = confirmRegex.find(bodyString)
                    
                    if (matchUuid != null && matchConfirm != null) {
                        val uuid = matchUuid.groupValues[1]
                        val confirmToken = matchConfirm.groupValues[1]

                        // Now use the final redirected URL (drive.usercontent.google.com) and append the bypassing parameters
                        val newUrl = response.request.url.newBuilder()
                            .addQueryParameter("confirm", confirmToken)
                            .addQueryParameter("uuid", uuid)
                            .build()

                        val newRequest = request.newBuilder()
                            .url(newUrl)
                            // Remove conflicting headers if any, OkHttp retains them normally
                            .build()

                        response.close() // Discard HTML Warning
                        return chain.proceed(newRequest) // Return Actual Video Stream
                    }
                }
            }
            return response
        }
    }
    
    val videoClient = client.newBuilder()
        .addInterceptor(DriveVirusScanInterceptor())
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
            val folderName = driveFolderName(subjectName)
            val encodedSubject = URLEncoder.encode(folderName, "UTF-8")
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

    fun getCachedFolderContents(context: android.content.Context, key: String): FolderContents? {
        val prefs = context.getSharedPreferences("bx_folder_cache", android.content.Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(key, null) ?: return null
        return try {
            parseFolderContents(org.json.JSONObject(jsonStr))
        } catch (_: Exception) { null }
    }

    private fun saveCachedFolderContents(context: android.content.Context, key: String, jsonStr: String) {
        val prefs = context.getSharedPreferences("bx_folder_cache", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString(key, jsonStr).apply()
    }

    private fun removeCachedFolder(context: android.content.Context, key: String) {
        context.getSharedPreferences("bx_folder_cache", android.content.Context.MODE_PRIVATE).edit().remove(key).apply()
    }

    /** Fetch contents of any Drive folder by its ID (returns sub-folders AND/OR videos) */
    suspend fun fetchFolderContents(context: android.content.Context, folderId: String): FolderContents? = withContext(Dispatchers.IO) {
        try {
            val url = "$APPS_SCRIPT_URL?folderId=${java.net.URLEncoder.encode(folderId, "UTF-8")}"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext null
                val json = org.json.JSONObject(body)
                if (json.has("error")) { 
                    val err = json.getString("error")
                    lastError = err
                    if (err.contains("not found", true)) removeCachedFolder(context, folderId)
                    return@withContext null 
                }
                saveCachedFolderContents(context, folderId, body)
                return@withContext parseFolderContents(json)
            }
        } catch (e: Exception) {
            lastError = "Exception: ${e.message}"
        }
        return@withContext null
    }

    /** Fetch top-level folders inside a subject folder by name */
    suspend fun fetchSubjectFolders(context: android.content.Context, subjectName: String): FolderContents? = withContext(Dispatchers.IO) {
        val folderName = driveFolderName(subjectName)
        try {
            val url = "$APPS_SCRIPT_URL?subject=${java.net.URLEncoder.encode(folderName, "UTF-8")}"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext null
                val json = org.json.JSONObject(body)
                if (json.has("error")) {
                    val err = json.getString("error")
                    lastError = err
                    if (err.contains("not found", true)) removeCachedFolder(context, subjectName)
                    return@withContext null 
                }
                saveCachedFolderContents(context, subjectName, body)
                return@withContext parseFolderContents(json)
            }
        } catch (e: Exception) {
            lastError = "Exception: ${e.message}"
        }
        return@withContext null
    }

    private fun parseFolderContents(json: org.json.JSONObject): FolderContents {
        val folderId   = json.optString("folderId", "")
        val folderName = json.optString("folderName", "")
        val hasSubs    = json.optBoolean("hasSubFolders", false)

        val folders = mutableListOf<DriveFolder>()
        val fArr = json.optJSONArray("folders")
        if (fArr != null) {
            for (i in 0 until fArr.length()) {
                val o = fArr.getJSONObject(i)
                folders.add(DriveFolder(o.getString("id"), o.getString("name")))
            }
        }

        val videos = mutableListOf<DriveVideo>()
        val vArr = json.optJSONArray("videos")
        if (vArr != null) {
            for (i in 0 until vArr.length()) {
                val o = vArr.getJSONObject(i)
                val id    = o.getString("id")
                val title = o.getString("title")
                val thumb = "https://drive.google.com/thumbnail?id=$id&sz=w1000-h1080"
                videos.add(DriveVideo(id, title, thumb, 0L))
            }
        }

        return FolderContents(folderId, folderName, hasSubs, folders, videos)
    }
}
