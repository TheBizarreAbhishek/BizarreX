package com.BizarreX.study.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import android.net.Uri
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import com.BizarreX.study.utils.TelegramStorageHelper
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ── Firestore message model ───────────────────────────────────────────────────

data class FirestoreMessage(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderPhotoUrl: String? = null,
    val text: String = "",
    val replyToName: String? = null,
    val replyToText: String? = null,
    val timestamp: Date? = null,
    val isEdited: Boolean = false,
    val reactions: Map<String, String> = emptyMap(),
    val mediaFileId: String? = null,
    val mediaThumbId: String? = null,
    val mediaType: String? = null
) {
    val isMe: Boolean get() = false // resolved at render time
    val senderInitial: Char get() = senderName.firstOrNull()?.uppercaseChar() ?: '?'
    val formattedTime: String get() = timestamp?.let {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(it)
    } ?: ""
    val verboseTime: String get() = timestamp?.let {
        SimpleDateFormat("dd MMM yyyy, hh:mm:ss a", Locale.getDefault()).format(it)
    } ?: "Unknown time"
}

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun CommunityScreen(
    currentUser: FirebaseUser,
    onChatVisibilityChanged: (Boolean) -> Unit = {}
) {
    var showChat by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(showChat) {
        onChatVisibilityChanged(showChat)
    }

    BackHandler(enabled = showChat) {
        showChat = false
    }

    AnimatedContent(
        targetState = showChat,
        transitionSpec = {
            if (targetState) {
                (slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(350, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(350))) togetherWith (slideOutHorizontally(
                    targetOffsetX = { -it },
                    animationSpec = tween(350, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(350)))
            } else {
                (slideInHorizontally(
                    initialOffsetX = { -it },
                    animationSpec = tween(350, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(350))) togetherWith (slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(350, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(350)))
            }
        },
        label = "chat_transition"
    ) { isChatState ->
        if (isChatState) {
            CommunityChatContent(
                currentUser = currentUser,
                onBack = { showChat = false }
            )
        } else {
            CommunityHomeContent(
                onOpenChat = { showChat = true }
            )
        }
    }
}

@Composable
fun CommunityHomeContent(onOpenChat: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Card(
            onClick = onOpenChat,
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Groups,
                        contentDescription = "Community Chat",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(20.dp))
                Column {
                    Text(
                        text = "Community Chat",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Join the live conversation",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

data class LocalUpload(
    val id: String,
    val uri: Uri,
    val type: String, // "image", "video", "document"
    val progress: Float = 0f
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityChatContent(currentUser: FirebaseUser, onBack: () -> Unit) {
    val db = remember { FirebaseFirestore.getInstance() }
    val messages = remember { mutableStateListOf<FirestoreMessage>() }
    var inputText by remember { mutableStateOf("") }
    var replyToMessage by remember { mutableStateOf<FirestoreMessage?>(null) }
    var editingMessage by remember { mutableStateOf<FirestoreMessage?>(null) }
    
    val selectedMediaUris = remember { mutableStateListOf<Uri>() }
    var selectedMediaType by remember { mutableStateOf("image") }
    val localUploads = remember { mutableStateListOf<LocalUpload>() }
    
    var showAttachmentSheet by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val selectedMessageIds = remember { mutableStateListOf<String>() }

    // Auto-scroll to bottom on new messages
    LaunchedEffect(messages.size, localUploads.size) {
        val total = messages.size + localUploads.size
        if (total > 0) {
            listState.animateScrollToItem(total - 1)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(),
        onResult = { uris: List<Uri> ->
            if (uris.isNotEmpty()) {
                selectedMediaUris.addAll(uris)
                selectedMediaType = "image"
                showAttachmentSheet = false
            }
        }
    )
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = { uris: List<Uri> ->
            if (uris.isNotEmpty()) {
                selectedMediaUris.addAll(uris)
                selectedMediaType = "document"
                showAttachmentSheet = false
            }
        }
    )

    // Real-time Firestore listener
    DisposableEffect(Unit) {
        val listener = db.collection("community_chat")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    messages.clear()
                    snapshot.documents.forEach { doc ->
                        @Suppress("UNCHECKED_CAST")
                        val reactionsRaw = doc.get("reactions") as? Map<String, String> ?: emptyMap()
                        
                        val msg = FirestoreMessage(
                            id = doc.id,
                            senderId = doc.getString("senderId") ?: "",
                            senderName = doc.getString("senderName") ?: "Unknown",
                            text = doc.getString("text") ?: "",
                            replyToName = doc.getString("replyToName"),
                            replyToText = doc.getString("replyToText"),
                            timestamp = doc.getTimestamp("timestamp")?.toDate(),
                            isEdited = doc.getBoolean("isEdited") ?: false,
                            reactions = reactionsRaw,
                            mediaFileId = doc.getString("mediaFileId"),
                            mediaThumbId = doc.getString("mediaThumbId"),
                            mediaType = doc.getString("mediaType")
                        )
                        messages.add(msg)
                    }
                    scope.launch {
                        if (messages.isNotEmpty()) {
                            listState.animateScrollToItem(messages.lastIndex)
                        }
                    }
                }
            }
        onDispose { listener.remove() }
    }

    fun sendMessage() {
        val text = inputText.trim()
        val urisToUpload = selectedMediaUris.toList()
        val uploadType = selectedMediaType
        
        if (text.isEmpty() && urisToUpload.isEmpty()) return
        
        inputText = ""
        selectedMediaUris.clear()

        if (editingMessage != null) {
            db.collection("community_chat").document(editingMessage!!.id).update(
                mapOf(
                    "text" to text,
                    "isEdited" to true
                )
            )
            editingMessage = null
        } else {
            if (urisToUpload.isEmpty()) {
                val payload = mutableMapOf<String, Any>(
                    "senderId" to currentUser.uid,
                    "senderName" to (currentUser.displayName ?: "Student"),
                    "senderPhotoUrl" to (currentUser.photoUrl?.toString() ?: ""),
                    "text" to text,
                    "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "isEdited" to false,
                    "reactions" to emptyMap<String, String>()
                )
                if (replyToMessage != null) {
                    payload["replyToName"] = replyToMessage!!.senderName
                    payload["replyToText"] = replyToMessage!!.text
                }
                db.collection("community_chat").add(payload)
                replyToMessage = null
            } else {
                val rToName = replyToMessage?.senderName
                val rToText = replyToMessage?.text
                replyToMessage = null // immediately dismiss reply state
                
                urisToUpload.forEachIndexed { index, uri ->
                    val mime = context.contentResolver.getType(uri) ?: ""
                    val actualUploadType = when {
                        mime.startsWith("video/") -> "video"
                        mime.startsWith("image/") -> "image"
                        else -> "document"
                    }
                    val uploadId = java.util.UUID.randomUUID().toString()
                    val uploadItem = LocalUpload(id = uploadId, uri = uri, type = actualUploadType)
                    localUploads.add(uploadItem)
                    
                    val textToSend = if (index == 0) text else ""
                    
                    scope.launch {
                        val uploadResult = TelegramStorageHelper.uploadFileWithProgress(context, uri, actualUploadType) { progress ->
                            val idx = localUploads.indexOfFirst { it.id == uploadId }
                            if (idx != -1) {
                                localUploads[idx] = localUploads[idx].copy(progress = progress)
                            }
                        }
                        
                        // Wait for completion chunk to fully clear out
                        localUploads.removeAll { it.id == uploadId }
                        
                        if (uploadResult != null) {
                            val fileId = uploadResult.first
                            val thumbId = uploadResult.second
                            
                            val mime = context.contentResolver.getType(uri) ?: ""
                            val finalStoredType = when {
                                mime.startsWith("video/") -> "video"
                                mime.startsWith("image/") -> "image"
                                else -> "document"
                            }
                            
                            val payload = mutableMapOf<String, Any>(
                                "senderId" to currentUser.uid,
                                "senderName" to (currentUser.displayName ?: "Student"),
                                "senderPhotoUrl" to (currentUser.photoUrl?.toString() ?: ""),
                                "text" to textToSend,
                                "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                                "isEdited" to false,
                                "reactions" to emptyMap<String, String>(),
                                "mediaFileId" to fileId,
                                "mediaType" to finalStoredType
                            )
                            if (thumbId != null) payload["mediaThumbId"] = thumbId
                            
                            if (rToName != null && rToText != null && index == 0) {
                                payload["replyToName"] = rToName
                                payload["replyToText"] = rToText
                            }
                            db.collection("community_chat").add(payload)
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        // ── Top Bar / Action Bar ──────────────────────────────────────────────────────────
        if (selectedMessageIds.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(start = 8.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { selectedMessageIds.clear() }) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "${selectedMessageIds.size}",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.weight(1f))
                
                Icon(
                    Icons.Default.ContentCopy, 
                    contentDescription = "Copy", 
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp).clickable {
                        val texts = selectedMessageIds.mapNotNull { id -> messages.find { it.id == id }?.text }.filter { it.isNotBlank() }
                        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(texts.joinToString("\n")))
                        selectedMessageIds.clear()
                    }
                )
                
                Spacer(modifier = Modifier.width(20.dp))

                val canDeleteAll = selectedMessageIds.all { id -> 
                    val msg = messages.find { it.id == id }
                    msg != null && msg.senderId == currentUser.uid
                }
                if (canDeleteAll) {
                    Icon(
                        Icons.Default.Delete, 
                        contentDescription = "Delete Selected", 
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp).clickable {
                            val toDelete = selectedMessageIds.toList()
                            selectedMessageIds.clear()
                            toDelete.forEach { id ->
                                db.collection("community_chat").document(id).delete()
                            }
                        }
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .padding(start = 8.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
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
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Groups,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "BizarreX Community",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4CAF50))
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Live group chat",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.55f)
                        )
                    }
                }
            }
        }

        // ── Message List ─────────────────────────────────────────────────────
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("👋", fontSize = 40.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Be the first to say hi!",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "This is the BizarreX community chat.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    val isSelected = selectedMessageIds.contains(msg.id)
                    MessageBubble(
                        msg = msg,
                        isMe = msg.senderId == currentUser.uid,
                        isSelected = isSelected,
                        selectionMode = selectedMessageIds.isNotEmpty(),
                        onToggleSelect = {
                            if (isSelected) selectedMessageIds.remove(msg.id)
                            else selectedMessageIds.add(msg.id)
                        },
                        onReply = { replyToMessage = msg },
                        onEdit = {
                            editingMessage = msg
                            inputText = msg.text
                            replyToMessage = null
                        },
                        onDelete = {
                            db.collection("community_chat").document(msg.id).delete()
                        },
                        onReact = { emoji ->
                            val currentReactions = msg.reactions.toMutableMap()
                            if (currentReactions[currentUser.uid] == emoji) {
                                currentReactions.remove(currentUser.uid)
                            } else {
                                currentReactions[currentUser.uid] = emoji
                            }
                            db.collection("community_chat").document(msg.id).update("reactions", currentReactions)
                        }
                    )
                }
                
                items(localUploads, key = { it.id }) { upload ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(4.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (upload.type == "document") {
                                    Box(
                                        modifier = Modifier.width(200.dp).height(100.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Attachment, contentDescription = "File", modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                } else {
                                    AsyncImage(
                                        model = upload.uri,
                                        contentDescription = null,
                                        modifier = Modifier.width(200.dp).height(200.dp).clip(RoundedCornerShape(14.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.5f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        progress = { upload.progress }, // Requires material3 ^1.3
                                        color = Color.White,
                                        strokeWidth = 3.dp,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Text(
                                        "${(upload.progress * 100).toInt()}%",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Input Bar ────────────────────────────────────────────────────────
        Surface(
            tonalElevation = 3.dp,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
                if (editingMessage != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Editing message", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                            Text(text = editingMessage!!.text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { 
                            editingMessage = null
                            inputText = ""
                        }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", modifier = Modifier.size(16.dp))
                        }
                    }
                } else if (replyToMessage != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Reply, contentDescription = "Reply", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Replying to ${replyToMessage!!.senderName}", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                            Text(text = replyToMessage!!.text ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { replyToMessage = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", modifier = Modifier.size(16.dp))
                        }
                    }
                }
                
                if (selectedMediaUris.isNotEmpty()) {
                    androidx.compose.foundation.lazy.LazyRow(
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.3f)).padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(selectedMediaUris) { uri ->
                            Box(contentAlignment = Alignment.TopEnd) {
                                if (selectedMediaType == "document") {
                                    Box(modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Attachment, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                } else {
                                    AsyncImage(
                                        model = uri,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Box(modifier = Modifier.padding(4.dp).size(20.dp).clip(CircleShape).background(Color.Black.copy(alpha=0.6f)).clickable {
                                    selectedMediaUris.remove(uri)
                                }, contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showAttachmentSheet = true },
                        modifier = Modifier.padding(end = 4.dp).size(40.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Attach Media", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            Text(
                                "Message...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { sendMessage() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    FilledIconButton(
                        onClick = { sendMessage() },
                        enabled = inputText.isNotBlank() || selectedMediaUris.isNotEmpty(),
                        modifier = Modifier.size(48.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Send",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
        
        if (showAttachmentSheet) {
            val sheetState = rememberModalBottomSheetState()
            ModalBottomSheet(
                onDismissRequest = { showAttachmentSheet = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(modifier = Modifier.padding(16.dp).navigationBarsPadding()) {
                    Text("Attachment Type", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }.padding(vertical = 14.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Gallery (Photos)", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            filePickerLauncher.launch("*/*")
                        }.padding(vertical = 14.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Document / File", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

// ── Telegram Styled Menu Item ─────────────────────────────────────────────────
@Composable
private fun TgMenuItem(title: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(16.dp))
        Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

// ── Message Bubble ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageBubble(
    msg: FirestoreMessage, 
    isMe: Boolean, 
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReact: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }
    var showCustomReactDialog by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.StartToEnd) {
                onReply()
            }
            false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            val color by animateColorAsState(
                targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                label = "swipe_color"
            )
            Box(
                modifier = Modifier.fillMaxSize().background(color).padding(start = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd) {
                    Icon(Icons.Default.Reply, contentDescription = "Reply", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.background),
            horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
            if (!isMe) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    val photoUrl = msg.senderPhotoUrl
                    if (!photoUrl.isNullOrEmpty()) {
                        coil.compose.AsyncImage(
                            model = photoUrl,
                            contentDescription = "Profile",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = msg.senderInitial.toString(),
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(
                horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                if (!isMe) {
                    Text(
                        text = msg.senderName,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, bottom = 3.dp)
                    )
                }

                if (msg.replyToName != null && msg.replyToText != null) {
                    Box(
                        modifier = Modifier
                            .padding(bottom = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Column {
                            Text(text = "Replying to ${msg.replyToName}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                            Text(text = msg.replyToText, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(
                            RoundedCornerShape(
                                topStart = 18.dp,
                                topEnd = 18.dp,
                                bottomStart = if (isMe) 18.dp else 4.dp,
                                bottomEnd = if (isMe) 4.dp else 18.dp
                            )
                        )
                        .background(
                            if (isMe) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .pointerInput(selectionMode) {
                            detectTapGestures(
                                onTap = { 
                                    if (selectionMode) onToggleSelect() 
                                },
                                onLongPress = { 
                                    if (selectionMode) onToggleSelect() 
                                    else showMenu = true 
                                },
                                onDoubleTap = { if (!selectionMode) onReact("👍") }
                            )
                        }
                ) {
                    Column {
                        val hasMedia = msg.mediaFileId != null
                        val isImageVideo = hasMedia && (msg.mediaType == "image" || msg.mediaType == "video")
                        
                        if (hasMedia) {
                            var resolvedMediaUrl by remember { mutableStateOf<String?>(null) }
                            var resolvedThumbUrl by remember { mutableStateOf<String?>(null) }
                            var showFullScreenMedia by remember { mutableStateOf(false) }

                            LaunchedEffect(msg.mediaFileId, msg.mediaThumbId) {
                                launch {
                                    if (msg.mediaThumbId != null) {
                                        resolvedThumbUrl = TelegramStorageHelper.getDirectMediaUrl(context, msg.mediaThumbId, "jpg")
                                    }
                                }
                                launch {
                                    val ext = if (msg.mediaType == "video") "mp4" else if (msg.mediaType == "document") "pdf" else "jpg"
                                    resolvedMediaUrl = TelegramStorageHelper.getDirectMediaUrl(context, msg.mediaFileId!!, ext)
                                }
                            }
                            
                            if (showFullScreenMedia && resolvedMediaUrl != null) {
                                Dialog(
                                    onDismissRequest = { showFullScreenMedia = false },
                                    properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
                                        var scale by remember { mutableStateOf(1f) }
                                        var offset by remember { mutableStateOf(Offset.Zero) }
                                        val state = rememberTransformableState { zoomChange, panChange, _ ->
                                            scale = (scale * zoomChange).coerceIn(1f, 5f)
                                            offset = Offset(offset.x + panChange.x * scale, offset.y + panChange.y * scale)
                                        }
                                        
                                        Box(
                                            modifier = Modifier.fillMaxSize()
                                                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { scale = if (scale > 1f) 1f else 2f; offset = Offset.Zero }) }
                                                .transformable(state = state),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AsyncImage(
                                                model = resolvedMediaUrl,
                                                contentDescription = "Full Screen Media",
                                                modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
                                                contentScale = ContentScale.Fit
                                            )
                                            if (msg.mediaType == "video") {
                                                Icon(Icons.Default.PlayArrow, contentDescription = "Play Video", tint = androidx.compose.ui.graphics.Color.White.copy(alpha=0.7f), modifier = Modifier.size(80.dp))
                                            }
                                        }
                                        androidx.compose.material3.IconButton(onClick = { showFullScreenMedia = false }, modifier = Modifier.padding(top = 40.dp, start = 16.dp)) {
                                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Close", tint = androidx.compose.ui.graphics.Color.White)
                                        }
                                    }
                                }
                            }

                            if (resolvedMediaUrl != null || resolvedThumbUrl != null) {
                                if (isImageVideo) {
                                    val urlToDisplay = resolvedThumbUrl ?: resolvedMediaUrl 
                                    if (urlToDisplay != null) {
                                        Box(
                                            contentAlignment = Alignment.Center, 
                                            modifier = Modifier
                                                .padding(top = 4.dp, bottom = 4.dp)
                                                .fillMaxWidth()
                                                .wrapContentHeight()
                                                .heightIn(max = 400.dp)
                                                .clickable { showFullScreenMedia = true }
                                        ) {
                                            AsyncImage(
                                                model = urlToDisplay,
                                                contentDescription = "Shared Image",
                                                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                                                contentScale = ContentScale.FillWidth
                                            )
                                            if (msg.mediaType == "video") {
                                                Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color.Black.copy(alpha=0.4f)), contentAlignment = Alignment.Center) {
                                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play Video", tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(32.dp))
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Row(modifier = Modifier
                                        .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 0.dp)
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.InsertDriveFile, contentDescription = "Document", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text("Document Attached", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                            } else {
                                Box(modifier = Modifier
                                    .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 0.dp)
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f))
                                    .clip(RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(strokeWidth = 2.dp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }

                        val hasTextOrReactions = msg.text.isNotEmpty() || msg.reactions.isNotEmpty()
                        if (hasTextOrReactions) {
                            Column(
                                modifier = Modifier.padding(
                                    start = 14.dp,
                                    end = 14.dp,
                                    top = if (isImageVideo) 8.dp else (if (hasMedia && !isImageVideo) 0.dp else 10.dp),
                                    bottom = 10.dp
                                )
                            ) {
                                if (msg.text.isNotEmpty()) {
                                    Text(
                                        text = msg.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isMe) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (msg.reactions.isNotEmpty()) {
                                    val groupedReactions = msg.reactions.values.groupingBy { it }.eachCount()
                                    Row(
                                        modifier = Modifier
                                            .padding(top = 6.dp)
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        groupedReactions.toList().sortedByDescending { it.second }.forEach { (emoji, count) ->
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = if (isMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                                                modifier = Modifier.clickable { onReact(emoji) }
                                            ) {
                                                Text(
                                                    text = "$emoji $count",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (showMenu || showCustomReactDialog) {
                        Popup(
                            alignment = if (isMe) Alignment.TopEnd else Alignment.TopStart,
                            onDismissRequest = { 
                                showMenu = false
                                showCustomReactDialog = false 
                            },
                            properties = PopupProperties(focusable = true)
                        ) {
                            AnimatedContent(
                                targetState = showCustomReactDialog,
                                transitionSpec = {
                                    if (targetState) {
                                        // Animating Forward to Grid
                                        (slideInHorizontally { it } + fadeIn(tween(220))) togetherWith (slideOutHorizontally { -it } + fadeOut(tween(220)))
                                    } else {
                                        // Animating Backward to Menu
                                        (slideInHorizontally { -it } + fadeIn(tween(220))) togetherWith (slideOutHorizontally { it } + fadeOut(tween(220)))
                                    }
                                },
                                label = "TgMenuTransition"
                            ) { isGrid ->
                                if (!isGrid) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
                                    ) {
                                        // 1. Emojis Pill
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shadowElevation = 10.dp
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                listOf("❤️", "👍", "👎", "🔥", "🥰", "👏").forEach { emoji ->
                                                    Text(
                                                        text = emoji,
                                                        fontSize = 24.sp,
                                                        modifier = Modifier.clickable {
                                                            showMenu = false
                                                            onReact(emoji)
                                                        }
                                                    )
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                                                        .clickable {
                                                            showCustomReactDialog = true
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        Icons.Default.KeyboardArrowDown,
                                                        contentDescription = "Expand Emojis",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }

                                        // 2. Action Menu
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shadowElevation = 10.dp,
                                            modifier = Modifier.width(240.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(vertical = 6.dp)) {
                                                TgMenuItem("Reply", Icons.Default.Reply) {
                                                    showMenu = false
                                                    onReply()
                                                }
                                                TgMenuItem("Select", Icons.Default.CheckCircle) {
                                                    showMenu = false
                                                    onToggleSelect()
                                                }
                                                if (isMe) {
                                                    TgMenuItem("Edit", Icons.Default.Edit) {
                                                        showMenu = false
                                                        onEdit()
                                                    }
                                                }
                                                TgMenuItem("Details", Icons.Default.Info) {
                                                    showMenu = false
                                                    showDetailsDialog = true
                                                }
                                            }
                                        }

                                        // 3. Quick Action Pill (Bottom)
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            shadowElevation = 10.dp
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                                                horizontalArrangement = Arrangement.spacedBy(32.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Default.Reply, 
                                                    contentDescription = "Reply", 
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant, 
                                                    modifier = Modifier.size(22.dp).clickable { showMenu = false; onReply() }
                                                )
                                                if (isMe) {
                                                    Icon(
                                                        Icons.Default.Delete, 
                                                        contentDescription = "Delete", 
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant, 
                                                        modifier = Modifier.size(22.dp).clickable { showMenu = false; onDelete() }
                                                    )
                                                }
                                                Icon(
                                                    Icons.Default.ContentCopy, 
                                                    contentDescription = "Copy", 
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant, 
                                                    modifier = Modifier.size(22.dp).clickable { 
                                                        showMenu = false
                                                        clipboardManager.setText(AnnotatedString(msg.text))
                                                    }
                                                )
                                                Icon(
                                                    Icons.AutoMirrored.Rounded.Send, 
                                                    contentDescription = "Forward", 
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant, 
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shadowElevation = 10.dp,
                                        modifier = Modifier.width(300.dp).heightIn(max = 400.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically, 
                                                modifier = Modifier.padding(bottom = 12.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                                                        .clickable {
                                                            showCustomReactDialog = false
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        Icons.AutoMirrored.Rounded.ArrowBack,
                                                        contentDescription = "Back",
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text("Popular Emojis", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            
                                            val allEmojis = listOf(
                                                "🙏", "👌", "🕊️", "🤡", "🥱", "🥴", "😍", "🐳",
                                                "❤️‍🔥", "🌚", "🌭", "💯", "🤣", "⚡️", "🍌", "🏆",
                                                "💔", "😒", "😑", "🍓", "🍾", "💋", "🖕", "😈",
                                                "😴", "😭", "🤓", "👻", "👨‍💻", "👀", "🎃", "🙈",
                                                "😇", "😨", "🤝", "✍️", "🤗", "🫡", "🎅", "🎄",
                                                "⛄", "🤯", "🤪", "🗿", "🆒", "💘", "🐒", "🦄",
                                                "🌹", "☀️", "🎉", "💩", "🤮", "🤧", "🥵", "🥶",
                                                "🥳", "🥺", "💀", "👽", "👾", "🤖", "💪", "🧠"
                                            )
                                            LazyVerticalGrid(
                                                columns = GridCells.Adaptive(36.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                items(allEmojis) { emoji ->
                                                    Text(
                                                        text = emoji,
                                                        fontSize = 28.sp,
                                                        modifier = Modifier.clickable {
                                                            onReact(emoji)
                                                            showCustomReactDialog = false
                                                            showMenu = false
                                                        },
                                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (msg.formattedTime.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    val timeText = if (msg.isEdited) "${msg.formattedTime} (edited)" else msg.formattedTime
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontSize = 10.sp
                    )
                }
            }

            if (isMe) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = msg.senderInitial.toString(),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}
