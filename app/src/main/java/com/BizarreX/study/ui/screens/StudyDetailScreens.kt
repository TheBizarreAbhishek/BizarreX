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
import com.BizarreX.study.utils.DriveFolder
import com.BizarreX.study.utils.DriveVideo
import com.BizarreX.study.utils.FolderContents
import com.BizarreX.study.utils.GoogleDriveHelper

// ── Subject Detail Screen ───────────────────────────────────────────────────────

/** Stack entry: either a subject name (root) or a folder ID (nested) */
private data class FolderNavEntry(val folderId: String?, val displayName: String)

@Composable
fun SubjectDetailScreen(
    subjectName: String,
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit
) {
    val context = LocalContext.current

    // Navigation stack — root entry has folderId=null (use subjectName API)
    val navStack = remember { mutableStateListOf(FolderNavEntry(null, subjectName)) }
    var contents  by remember { mutableStateOf<FolderContents?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error     by remember { mutableStateOf<String?>(null) }

    val current = navStack.last()

    // Fetch whenever nav changes (Stale-while-revalidate caching)
    LaunchedEffect(current) {
        val cacheKey = current.folderId ?: subjectName
        val cached = GoogleDriveHelper.getCachedFolderContents(context, cacheKey)
        if (cached != null) {
            contents = cached
            isLoading = false
        } else {
            isLoading = true
            contents = null
        }
        error = null

        val result = if (current.folderId == null) {
            GoogleDriveHelper.fetchSubjectFolders(context, subjectName)
        } else {
            GoogleDriveHelper.fetchFolderContents(context, current.folderId)
        }

        if (result == null) {
            // Only show error if we have no cache
            if (contents == null) {
                error = if (navStack.size == 1) "No lectures available yet.\n${GoogleDriveHelper.lastError ?: ""}"
                        else "Could not load folder."
            } else if (GoogleDriveHelper.lastError?.contains("not found", ignoreCase = true) == true) {
                // Folder was deleted on Drive, reflect deletion in UI immediately
                contents = null
                error = "Folder no longer exists on Drive."
            }
        } else {
            contents = result
        }
        isLoading = false
    }

    BackHandler(enabled = navStack.size > 1) { navStack.removeLast() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── Top bar ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { if (navStack.size > 1) navStack.removeLast() else onBack() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(
                    text = current.displayName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                val subtitle = when {
                    isLoading -> "Loading…"
                    contents?.hasSubFolders == true -> "${contents!!.folders.size} sections"
                    else -> "${contents?.videos?.size ?: 0} lectures"
                }
                Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.55f))
            }
        }

        // ── Breadcrumb (only when deeper than 2 levels) ───────────────────
        if (navStack.size > 2) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                navStack.dropLast(1).forEachIndexed { index, entry ->
                    if (index > 0) Text(" › ", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
                    Text(
                        text = entry.displayName,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        modifier = Modifier.clickable {
                            repeat(navStack.size - 1 - index) { navStack.removeLast() }
                        }
                    )
                }
            }
        }

        // ── Body ──────────────────────────────────────────────────────────
        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            error != null -> Box(
                Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center
            ) {
                Text(text = error!!, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            }

            contents != null -> {
                val c = contents!!

                if (c.hasSubFolders && c.folders.isNotEmpty()) {
                    // Show folder cards
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 32.dp)
                    ) {
                        items(c.folders) { folder ->
                            FolderCard(
                                name   = folder.name,
                                onClick = { navStack.add(FolderNavEntry(folder.id, folder.name)) }
                            )
                        }
                    }
                } else {
                    // Show video list
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp)
                    ) {
                        itemsIndexed(c.videos) { index, video ->
                            VideoPill(video = video, number = index + 1, onClick = { onVideoClick(video.id) })
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        if (c.videos.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.Center) {
                                    Text("No videos in this folder yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderCard(name: String, onClick: () -> Unit) {
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.replace(Regex("[^0-9]"), "").takeIf { it.isNotEmpty() } ?: "📂",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Spacer(modifier = Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
                Text(text = "Tap to view lectures", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
            val streamUrl = "https://docs.google.com/uc?export=download&id=${video.id}"

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

    val streamUrl = "https://docs.google.com/uc?export=download&id=$videoId"
    
    // 🔒 Vault-first: use local file if exists, else stream
    val localFile = VideoDownloadManager.getLocalFile(context, videoId)
    val mediaUri = if (localFile != null) {
        android.net.Uri.fromFile(localFile)
    } else {
        android.net.Uri.parse(streamUrl)
    }
    val exoPlayer = remember {
        val dataSourceFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(GoogleDriveHelper.videoClient)
        val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)
            
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
            val mediaItem = MediaItem.fromUri(mediaUri)
            setMediaItem(mediaItem)
            
            addListener(object : androidx.media3.common.Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    val msg = "ExoPlayer Error: ${error.errorCodeName} - ${error.cause?.message}"
                    android.util.Log.e("BizarreX", msg, error)
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                }
            })
            
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
