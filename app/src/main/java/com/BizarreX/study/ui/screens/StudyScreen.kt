package com.BizarreX.study.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Data ─────────────────────────────────────────────────────────────────────

private val sem1Subjects = listOf(
    "Engg. Mathematics I",
    "Engg. Physics",
    "Electrical Engineering",
    "Programming for Problem Solving",
    "Environment & Ecology"
)

private val sem2Subjects = listOf(
    "Engg. Mathematics II",
    "Engg. Chemistry",
    "Electronics Engineering",
    "Fundamentals of Mech. Engg.",
    "Soft Skills"
)

enum class StudyNavLevel { HOME, SEMESTER, SUBJECT, VIDEO }

@Composable
fun StudyScreen(onFullscreenVisibilityChanged: (Boolean) -> Unit = {}) {
    var navLevel by rememberSaveable { mutableStateOf(StudyNavLevel.HOME) }
    var selectedSem by rememberSaveable { mutableIntStateOf(0) }
    var selectedSubject by rememberSaveable { mutableStateOf<String?>(null) }
    var activeVideoId by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(enabled = navLevel != StudyNavLevel.HOME) {
        when (navLevel) {
            StudyNavLevel.VIDEO -> navLevel = StudyNavLevel.SUBJECT
            StudyNavLevel.SUBJECT -> { navLevel = StudyNavLevel.SEMESTER; selectedSubject = null }
            StudyNavLevel.SEMESTER -> { navLevel = StudyNavLevel.HOME; selectedSem = 0 }
            StudyNavLevel.HOME -> {}
        }
    }

    AnimatedContent(
        targetState = navLevel,
        transitionSpec = {
            if (targetState > initialState) {
                // Going forward: slide in from right, slide out to left
                (slideInHorizontally(tween(300)) { it } + fadeIn(tween(300)))
                    .togetherWith(slideOutHorizontally(tween(300)) { -it } + fadeOut(tween(200)))
            } else {
                // Going back: slide in from left, slide out to right
                (slideInHorizontally(tween(300)) { -it } + fadeIn(tween(300)))
                    .togetherWith(slideOutHorizontally(tween(300)) { it } + fadeOut(tween(200)))
            }
        },
        label = "study_nav"
    ) { level ->
        when (level) {
            StudyNavLevel.HOME -> StudyHomeScreen(onSemClick = { selectedSem = it; navLevel = StudyNavLevel.SEMESTER })
            StudyNavLevel.SEMESTER -> {
                val (num, title, subs) = if (selectedSem == 1) Triple("I", "Semester I", sem1Subjects) else Triple("II", "Semester II", sem2Subjects)
                SemesterSubjectsScreen(
                    romanNum = num,
                    title = title,
                    subjects = subs,
                    onBack = { navLevel = StudyNavLevel.HOME; selectedSem = 0 },
                    onSubjectClick = { selectedSubject = it; navLevel = StudyNavLevel.SUBJECT }
                )
            }
            StudyNavLevel.SUBJECT -> {
                SubjectDetailScreen(
                    subjectName = selectedSubject ?: "",
                    onBack = { navLevel = StudyNavLevel.SEMESTER; selectedSubject = null },
                    onVideoClick = { activeVideoId = it; navLevel = StudyNavLevel.VIDEO }
                )
            }
            StudyNavLevel.VIDEO -> {
                VideoPlayerScreen(
                    videoId = activeVideoId ?: "",
                    onBack = { navLevel = StudyNavLevel.SUBJECT; activeVideoId = null },
                    onFullscreenToggled = onFullscreenVisibilityChanged
                )
            }
        }
    }
}

// ── Home Screen ───────────────────────────────────────────────────────────────

@Composable
private fun StudyHomeScreen(onSemClick: (Int) -> Unit) {
    var showProfile by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
    var user by remember { mutableStateOf(auth.currentUser) }
    
    // Auto-refresh hook to ensure updates reflect globally
    LaunchedEffect(showProfile) {
        if (!showProfile) user = auth.currentUser
    }
    
    val photoUrl = remember(user, user?.photoUrl) {
        val raw = user?.photoUrl?.toString()
        if (raw?.startsWith("tg://") == true) raw.removePrefix("tg://") else raw
    }
    
    var activeRemoteUrl by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(photoUrl) {
        if (photoUrl != null) {
            if (user?.photoUrl?.toString()?.startsWith("tg://") == true) {
                val dl = com.BizarreX.study.utils.TelegramStorageHelper.getDirectMediaUrl(context, photoUrl)
                if (dl != null) activeRemoteUrl = dl
            } else {
                activeRemoteUrl = photoUrl
            }
        }
    }

    if (showProfile) {
        ProfileSettingsDialog(onDismiss = { showProfile = false })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top App Bar — Black
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "BizarreX",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = Color.White
                )
                Text(
                    text = "Welcome, ${user?.displayName?.split(" ")?.firstOrNull() ?: "Student"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.55f)
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { showProfile = true },
                contentAlignment = Alignment.Center
            ) {
                if (activeRemoteUrl != null) {
                    coil.compose.AsyncImage(
                        model = activeRemoteUrl,
                        contentDescription = "Profile",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Text(
                        text = user?.displayName?.take(1) ?: "A",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        // Body
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Let's Study",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "B.Tech First Year",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            SemesterCard(
                romanNum = "I",
                title = "Semester I",
                description = "Video lectures, notes & PYQs · 5 subjects",
                onClick = { onSemClick(1) }
            )

            Spacer(modifier = Modifier.height(14.dp))

            SemesterCard(
                romanNum = "II",
                title = "Semester II",
                description = "Video lectures, formula sheets & PYQs · 5 subjects",
                onClick = { onSemClick(2) }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ── Semester Card ─────────────────────────────────────────────────────────────

@Composable
private fun SemesterCard(
    romanNum: String,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = romanNum,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AKTU · $title",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.PlayCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

// ── Semester Subjects Screen ──────────────────────────────────────────────────

@Composable
private fun SemesterSubjectsScreen(
    romanNum: String,
    title: String,
    subjects: List<String>,
    onBack: () -> Unit,
    onSubjectClick: (String) -> Unit
) {
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
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(
                    text = "AKTU · $title",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Text(
                    text = "${subjects.size} subjects",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.55f)
                )
            }
        }

        // Body
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = romanNum,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "B.Tech First Year",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "SUBJECTS",
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.5.sp),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Pills — each takes equal share of remaining height
            subjects.forEachIndexed { index, subject ->
                SubjectPillCard(
                    modifier = Modifier.weight(1f),
                    number = index + 1,
                    subject = subject,
                    onClick = { onSubjectClick(subject) }
                )
                if (index < subjects.lastIndex) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

// ── Subject Pill Card ─────────────────────────────────────────────────────────

@Composable
private fun SubjectPillCard(
    modifier: Modifier = Modifier,
    number: Int,
    subject: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = subject,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun ProfileSettingsDialog(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
    var user by remember { mutableStateOf(auth.currentUser) }
    
    var name by remember { mutableStateOf(user?.displayName ?: "") }
    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableFloatStateOf(0f) }
    
    val photoUrl = remember(user, user?.photoUrl) {
        val raw = user?.photoUrl?.toString()
        if (raw?.startsWith("tg://") == true) {
            raw.removePrefix("tg://")
        } else {
            raw
        }
    }
    
    var activeRemoteUrl by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(photoUrl) {
        if (photoUrl != null) {
            if (user?.photoUrl?.toString()?.startsWith("tg://") == true) {
                val dl = com.BizarreX.study.utils.TelegramStorageHelper.getDirectMediaUrl(context, photoUrl)
                if (dl != null) activeRemoteUrl = dl
            } else {
                activeRemoteUrl = photoUrl
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()
    
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            isUploading = true
            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val result = com.BizarreX.study.utils.TelegramStorageHelper.uploadFileWithProgress(
                    context, uri, "image"
                ) { p -> uploadProgress = p }
                
                if (result != null) {
                    val fileId = result.first
                    val newUri = android.net.Uri.parse("tg://$fileId")
                    val req = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                        .setPhotoUri(newUri)
                        .build()
                    auth.currentUser?.updateProfile(req)?.addOnCompleteListener {
                        user = auth.currentUser
                        isUploading = false
                    }
                } else {
                    isUploading = false
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        title = { Text("Edit Profile", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier.size(100.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).clickable {
                            if (!isUploading) {
                                launcher.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        if (activeRemoteUrl != null && !isUploading) {
                            coil.compose.AsyncImage(
                                model = activeRemoteUrl,
                                contentDescription = "Profile",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else if (isUploading) {
                            CircularProgressIndicator(progress = { uploadProgress }, color = Color.White)
                        } else {
                            Text(user?.displayName?.take(1) ?: "A", color = MaterialTheme.colorScheme.onPrimary, fontSize = 40.sp)
                        }
                    }
                    
                    Box(
                        modifier = Modifier
                            .offset(x = (-4).dp, y = (-4).dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank()) {
                    val req = com.google.firebase.auth.UserProfileChangeRequest.Builder().setDisplayName(name).build()
                    auth.currentUser?.updateProfile(req)?.addOnCompleteListener {
                        user = auth.currentUser
                        onDismiss()
                    }
                }
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
