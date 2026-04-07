package com.BizarreX.study.ui.screens

import androidx.compose.animation.AnimatedContent
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import com.BizarreX.study.utils.DownloadState
import com.BizarreX.study.utils.VideoDownloadManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.isActive
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.BizarreX.study.utils.DriveVideo
import com.BizarreX.study.utils.GoogleDriveHelper

// ── Subject Detail Screen ───────────────────────────────────────────────────────

@Composable
fun SubjectDetailScreen(
    subjectName: String,
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var groupedVideos by remember { mutableStateOf<Map<String, List<DriveVideo>>>(emptyMap()) }
    var allVideosCount by remember { mutableIntStateOf(0) }
    var activeUnitName by remember { mutableStateOf<String?>(null) }
    
    BackHandler(enabled = activeUnitName != null) {
        activeUnitName = null
    }

    LaunchedEffect(subjectName) {
        // 1. SILENT CACHE LOAD (0 seconds wait)
        val cachedVideos = GoogleDriveHelper.getCachedVideos(context, subjectName)
        if (cachedVideos != null && cachedVideos.isNotEmpty()) {
            val unitRegex = Regex("(?i)(?:unit|ch|module)\\s*\\.?\\s*(\\d+)")
            val map = mutableMapOf<String, MutableList<DriveVideo>>()
            for (v in cachedVideos) {
                val match = unitRegex.find(v.title)
                val unitName = if (match != null) "Unit ${match.groupValues[1]}" else "Other Lectures"
                if (!map.containsKey(unitName)) map[unitName] = mutableListOf()
                map[unitName]!!.add(v)
            }
            val sortedKeys = map.keys.sortedWith(compareBy { k ->
                if (k == "Other Lectures") 9999 else k.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 1000
            })
            val sortedMap = mutableMapOf<String, List<DriveVideo>>()
            for (k in sortedKeys) sortedMap[k] = map[k]!!
            
            groupedVideos = sortedMap
            allVideosCount = cachedVideos.size
            isLoading = false
        } else {
            isLoading = true
        }
        
        // 2. BACKGROUND SYNC (Network Check)
        val networkVideos = GoogleDriveHelper.fetchVideosFromAppsScript(context, subjectName)
        
        if (networkVideos != null) {
            // Save to Disk for next time
            GoogleDriveHelper.saveVideosToCache(context, subjectName, networkVideos)
            
            // Overwrite UI silently with live data
            if (networkVideos.isNotEmpty()) {
                val unitRegex = Regex("(?i)(?:unit|ch|module)\\s*\\.?\\s*(\\d+)")
                val map = mutableMapOf<String, MutableList<DriveVideo>>()
                for (v in networkVideos) {
                    val match = unitRegex.find(v.title)
                    val unitName = if (match != null) "Unit ${match.groupValues[1]}" else "Other Lectures"
                    if (!map.containsKey(unitName)) map[unitName] = mutableListOf()
                    map[unitName]!!.add(v)
                }
                val sortedKeys = map.keys.sortedWith(compareBy { k ->
                    if (k == "Other Lectures") 9999 else k.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 1000
                })
                val sortedMap = mutableMapOf<String, List<DriveVideo>>()
                for (k in sortedKeys) sortedMap[k] = map[k]!!
                
                groupedVideos = sortedMap
                allVideosCount = networkVideos.size
            } else if (cachedVideos.isNullOrEmpty()) {
                errorMessage = "No lectures available yet."
            }
        } else if (cachedVideos.isNullOrEmpty()) {
            // Only show error if we have NO cache at all
            val dbg = GoogleDriveHelper.lastError ?: "Unknown Error"
            errorMessage = "Sync Error. Diagnostics:\n$dbg"
        }
        
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { if (activeUnitName != null) activeUnitName = null else onBack() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(
                    text = activeUnitName ?: subjectName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isLoading) "Loading modules..." else if (activeUnitName != null) "${groupedVideos[activeUnitName]?.size ?: 0} Lectures" else "$allVideosCount Lectures",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.55f)
                )
            }
        }

        // Body
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (errorMessage == "COMING_SOON") {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Rounded.PlayCircle, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Coming Soon!", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Lectures for $subjectName are currently being recorded & will be available shortly.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        } else if (errorMessage != null) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            AnimatedContent(targetState = activeUnitName, label = "unit_transition") { currentUnit ->
                if (currentUnit == null) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        groupedVideos.forEach { (unitName, videos) ->
                            item {
                                UnitCard(unitName = unitName, videos = videos, onClick = { activeUnitName = unitName })
                            }
                        }
                    }
                } else {
                    val unitVideos = groupedVideos[currentUnit] ?: emptyList()
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)
                    ) {
                        itemsIndexed(unitVideos) { index, video ->
                            VideoPill(video = video, number = index + 1, onClick = { onVideoClick(video.id) })
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UnitCard(unitName: String, videos: List<DriveVideo>, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .defaultMinSize(minHeight = 80.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = unitName.replace(Regex("[^0-9]"), "").takeIf { it.isNotEmpty() } ?: "#",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = unitName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${videos.size} Lectures",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VideoPill(video: DriveVideo, number: Int, onClick: () -> Unit) {
    val context = LocalContext.current
    var progress by remember { mutableFloatStateOf(0f) }
    var timeStr by remember { mutableStateOf("") }
    
    LaunchedEffect(video.id, video.durationMs) {
        val (pos, localDur) = GoogleDriveHelper.getWatchProgress(context, video.id)
        val finalDur = if (video.durationMs > 0L) video.durationMs else localDur
        
        if (finalDur > 0L) {
            progress = (pos.toFloat() / finalDur.toFloat()).coerceIn(0f, 1f)
            val secTotal = finalDur / 1000
            val min = secTotal / 60
            val sec = secTotal % 60
            timeStr = String.format("%02d:%02d", min, sec)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 86.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(100.dp).height(60.dp).clip(RoundedCornerShape(10.dp))) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                if (timeStr.isNotEmpty()) {
                    Box(modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(0.7f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                        Text(text = timeStr, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                if (progress > 0f) {
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
                        color = Color.Red,
                        trackColor = Color.Transparent
                    )
                }
                
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.PlayCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Lecture $number",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // ── Download / Delete button ──────────────────────────────────
            val downloadStates by VideoDownloadManager.states.collectAsState()
            val dlState = downloadStates[video.id] ?: if (VideoDownloadManager.isDownloaded(context, video.id)) DownloadState.Downloaded else DownloadState.Idle
            val scope = rememberCoroutineScope()
            val streamUrl = "https://www.googleapis.com/drive/v3/files/${video.id}?alt=media&key=${com.BizarreX.study.utils.GoogleDriveHelper.API_KEY}"

            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                when (dlState) {
                    is DownloadState.Idle -> IconButton(onClick = {
                        VideoDownloadManager.download(context, video.id, streamUrl, scope)
                    }) {
                        Icon(Icons.Rounded.Download, contentDescription = "Download",
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    }
                    is DownloadState.Downloading -> {
                        CircularProgressIndicator(
                            progress = { dlState.progress },
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    is DownloadState.Downloaded -> IconButton(onClick = {
                        VideoDownloadManager.delete(context, video.id)
                    }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete offline",
                            tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                    }
                    is DownloadState.Failed -> IconButton(onClick = {
                        VideoDownloadManager.download(context, video.id, streamUrl, scope)
                    }) {
                        Icon(Icons.Rounded.Download, contentDescription = "Retry",
                            tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

// ── Inside-App Video Player Screen ────────────────────────────────────────────

@Composable
fun VideoPlayerScreen(videoId: String, onBack: () -> Unit, onFullscreenToggled: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    var isFullScreen by remember { mutableStateOf(false) }

    val streamUrl = "https://www.googleapis.com/drive/v3/files/$videoId?alt=media&key=${GoogleDriveHelper.API_KEY}"
    
    // 🔒 Vault-first: use local file if exists, else stream
    val localFile = VideoDownloadManager.getLocalFile(context, videoId)
    val mediaUri = if (localFile != null) {
        android.net.Uri.fromFile(localFile)
    } else {
        android.net.Uri.parse(streamUrl)
    }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(mediaUri)
            setMediaItem(mediaItem)
            
            // Auto-Resume YouTube style
            val (savedPos, _) = GoogleDriveHelper.getWatchProgress(context, videoId)
            if (savedPos > 0L) {
                seekTo(savedPos)
            }
            
            prepare()
            playWhenReady = true
        }
    }

    // Steathy Background Progress Saver
    LaunchedEffect(exoPlayer) {
        while(isActive) {
            kotlinx.coroutines.delay(5000L)
            if (exoPlayer.isPlaying) {
                val dur = exoPlayer.duration.takeIf { it > 0 } ?: 0L
                GoogleDriveHelper.saveWatchProgress(context, videoId, exoPlayer.currentPosition, dur)
            }
        }
    }

    val activity = context as? Activity

    // Immersive Mode Logic
    DisposableEffect(isFullScreen) {
        val window = activity?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (isFullScreen) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {}
    }

    BackHandler(enabled = isFullScreen) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        isFullScreen = false
        onFullscreenToggled(false)
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            val window = activity?.window
            if (window != null) {
                 WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
            onFullscreenToggled(false)
            exoPlayer.release()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (!isFullScreen) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, bottom = 10.dp, start = 8.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            }
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                modifier = if (isFullScreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f/9f),
                factory = { ctx ->
                    val inflater = android.view.LayoutInflater.from(ctx)
                    val playerView = inflater.inflate(com.BizarreX.study.R.layout.bizarrex_player_view, null) as PlayerView
                    playerView.apply {
                        player = exoPlayer
                        
                        setFullscreenButtonClickListener { isFs ->
                            isFullScreen = isFs
                            onFullscreenToggled(isFs)
                            if (isFs) {
                                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            } else {
                                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            }
                        }
                    }
                }
            )
        }
    }
}
