package com.BizarreX.study.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class UpdateInfo(
    val latestVersion: String,
    val currentVersion: String,
    val apkDownloadUrl: String,
    val releaseNotes: String
)

object UpdateChecker {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Checks GitHub Releases API for a newer version.
     * Returns UpdateInfo if update needed, null if already up-to-date or error.
     */
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val owner = com.BizarreX.study.BuildConfig.GITHUB_OWNER
            val repo = com.BizarreX.study.BuildConfig.GITHUB_REPO
            val url = "https://api.github.com/repos/$owner/$repo/releases/latest"

            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/vnd.github.v3+json")
                .addHeader("User-Agent", "BizarreX-Android")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null

                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)

                val latestTag = json.getString("tag_name").trimStart('v') // e.g. "1.2.0"
                val currentVersion = com.BizarreX.study.BuildConfig.VERSION_NAME // e.g. "1.0.0"
                val releaseNotes = json.optString("body", "Bug fixes and improvements.")

                // Find the APK asset download URL
                val assets = json.getJSONArray("assets")
                var apkUrl = json.optString("html_url", "")  // fallback to release page
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.getString("name")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.getString("browser_download_url")
                        break
                    }
                }

                // Compare versions — if latest > current, update needed
                if (isNewerVersion(latestTag, currentVersion)) {
                    return@withContext UpdateInfo(
                        latestVersion = latestTag,
                        currentVersion = currentVersion,
                        apkDownloadUrl = apkUrl,
                        releaseNotes = releaseNotes
                    )
                }

                return@withContext null
            }
        } catch (e: Exception) {
            Log.w("UpdateChecker", "Update check failed: ${e.message}")
            null
        }
    }

    /**
     * Returns true if `latest` version string is strictly newer than `current`.
     * Handles semantic versioning: "1.2.0" > "1.0.0"
     */
    private fun isNewerVersion(latest: String, current: String): Boolean {
        return try {
            val l = latest.split(".").map { it.trim().toInt() }
            val c = current.split(".").map { it.trim().toInt() }
            val size = maxOf(l.size, c.size)
            for (i in 0 until size) {
                val lv = l.getOrElse(i) { 0 }
                val cv = c.getOrElse(i) { 0 }
                if (lv > cv) return true
                if (lv < cv) return false
            }
            false
        } catch (e: NumberFormatException) {
            false
        }
    }
}
