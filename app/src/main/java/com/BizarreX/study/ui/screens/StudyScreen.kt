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
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
import androidx.compose.material3.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
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
            .background(MiuixTheme.colorScheme.background)
    ) {
        // Modern Floating Header Card / Pill
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            cornerRadius = 20.dp,
            colors = CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.surfaceContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "BizarreX",
                        style = MiuixTheme.textStyles.title2.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp,
                            color = MiuixTheme.colorScheme.primary
                        )
                    )
                    Text(
                        text = "Welcome, ${user?.displayName?.split(" ")?.firstOrNull() ?: "Student"}",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.primary)
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
                            style = MiuixTheme.textStyles.title3.copy(fontWeight = FontWeight.Bold),
                            color = MiuixTheme.colorScheme.onPrimary
                        )
                    }
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
                style = MiuixTheme.textStyles.title1.copy(fontWeight = FontWeight.ExtraBold),
                color = MiuixTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "B.Tech First Year",
                style = MiuixTheme.textStyles.title4.copy(fontWeight = FontWeight.SemiBold),
                color = MiuixTheme.colorScheme.onSurfaceSecondary
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
            .padding(bottom = 12.dp),
        onClick = onClick,
        pressFeedbackType = PressFeedbackType.Sink
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MiuixTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = romanNum,
                    style = MiuixTheme.textStyles.title2.copy(fontWeight = FontWeight.ExtraBold),
                    color = MiuixTheme.colorScheme.onPrimary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AKTU · $title",
                    style = MiuixTheme.textStyles.title4.copy(fontWeight = FontWeight.Bold),
                    color = MiuixTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.PlayCircle,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = description,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
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
            .background(MiuixTheme.colorScheme.background)
    ) {
        // Top bar - Modern Floating Card / Pill
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            cornerRadius = 20.dp,
            colors = CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.surfaceContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = MiuixTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = "AKTU · $title",
                        style = MiuixTheme.textStyles.title4.copy(fontWeight = FontWeight.Bold),
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${subjects.size} subjects",
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
        }

        // Body
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 16.dp,
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MiuixTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = romanNum,
                            style = MiuixTheme.textStyles.title2.copy(fontWeight = FontWeight.ExtraBold),
                            color = MiuixTheme.colorScheme.onPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "B.Tech First Year",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                        Text(
                            text = title,
                            style = MiuixTheme.textStyles.title3.copy(fontWeight = FontWeight.ExtraBold),
                            color = MiuixTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "SUBJECTS",
                style = MiuixTheme.textStyles.footnote2.copy(letterSpacing = 1.5.sp),
                color = MiuixTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
            )

            // Grouped Subjects Card (Compact & Filled)
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 16.dp,
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer)
            ) {
                Column {
                    subjects.forEachIndexed { index, subject ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSubjectClick(subject) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MiuixTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = (index + 1).toString(),
                                    style = MiuixTheme.textStyles.title4.copy(fontWeight = FontWeight.Bold),
                                    color = MiuixTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = subject,
                                style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Medium),
                                color = MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        if (index < subjects.lastIndex) {
                            androidx.compose.material3.HorizontalDivider(
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
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
            .fillMaxWidth(),
        onClick = onClick,
        pressFeedbackType = PressFeedbackType.Sink
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MiuixTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = number.toString(),
                    style = MiuixTheme.textStyles.title4.copy(fontWeight = FontWeight.Bold),
                    color = MiuixTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = subject,
                style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Medium),
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
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

    OverlayDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = "Edit Profile"
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Box(
                    modifier = Modifier.size(100.dp).clip(CircleShape).background(MiuixTheme.colorScheme.primary).clickable {
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
                        Text(user?.displayName?.take(1) ?: "A", color = MiuixTheme.colorScheme.onPrimary, fontSize = 40.sp)
                    }
                }
                
                Box(
                    modifier = Modifier
                        .offset(x = (-4).dp, y = (-4).dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp), tint = MiuixTheme.colorScheme.onPrimaryContainer)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            TextField(
                value = name,
                onValueChange = { name = it },
                label = "Display Name",
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                TextButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(16.dp))
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            val req = com.google.firebase.auth.UserProfileChangeRequest.Builder().setDisplayName(name).build()
                            auth.currentUser?.updateProfile(req)?.addOnCompleteListener {
                                user = auth.currentUser
                                onDismiss()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors()
                ) {
                    Text("Save")
                }
            }
        }
    }
}
