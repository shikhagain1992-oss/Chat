package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GifBox
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.ChatMessage
import com.example.data.model.Conversation
import com.example.data.model.UserProfile
import com.example.data.remote.CloudinaryUploader
import com.example.data.remote.FirebaseManager
import com.example.data.repository.CallRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.UserRepository
import com.example.ui.components.MessageStatusIcon
import com.example.ui.components.UserAvatar
import com.example.ui.theme.AliceBlue
import com.example.ui.theme.BubbleBorderColor
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.IceBlue
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PureWhite
import com.example.ui.theme.ReceiverBubbleColor
import com.example.ui.theme.SenderBubbleColor
import com.example.ui.theme.SoftBlueBorder
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextDarkMuted
import com.example.ui.theme.TextSender
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private val QUICK_GIFS = listOf(
    "https://media.giphy.com/media/v1.Y2lkPTc5MGI3NjExbXNydG56amJ5Y2N4eW56ZzR2c3B2Zzh6NHRlZ3R2Zzh6NHRlZyZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/3o7abKhOpu0NwenH3O/giphy.gif",
    "https://media.giphy.com/media/l0MYt5jPR6QX5pnqM/giphy.gif",
    "https://media.giphy.com/media/26u4cqiYI30juCOGY/giphy.gif",
    "https://media.giphy.com/media/3oz8xIsloV7zOmt81G/giphy.gif"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    conversationId: String,
    otherUserId: String,
    isGroup: Boolean,
    onNavigateBack: () -> Unit,
    onStartCall: (String, Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUserId = FirebaseManager.currentUserId ?: "user"

    val chatRepo = remember { ChatRepository() }
    val userRepo = remember { UserRepository() }
    val callRepo = remember { CallRepository() }

    val currentUserProfile by userRepo.getUserProfile(currentUserId).collectAsState(initial = null)
    val allUsers by userRepo.getAllUsersFlow().collectAsState(initial = emptyList())

    val effectiveOtherId = remember(otherUserId, conversationId, currentUserId) {
        val cleanConv = conversationId.removePrefix("conv_")
        if (otherUserId.isNotBlank() && otherUserId != "none" && otherUserId != "group") otherUserId
        else if (cleanConv.contains("_") && !cleanConv.startsWith("group_")) {
            val parts = cleanConv.split("_")
            if (parts.size == 2) {
                if (parts[0] == currentUserId) parts[1] else parts[0]
            } else ""
        } else ""
    }

    val otherUser by userRepo.getUserProfile(effectiveOtherId).collectAsState(initial = null)

    val canonicalFallbackId = remember(currentUserId, effectiveOtherId) {
        if (currentUserId.isNotBlank() && effectiveOtherId.isNotBlank() && effectiveOtherId != "none") {
            chatRepo.getCanonicalOneToOneId(currentUserId, effectiveOtherId)
        } else ""
    }

    var effectiveConvId by remember(conversationId, canonicalFallbackId) {
        val clean = conversationId.removePrefix("conv_")
        mutableStateOf(if (clean.isBlank() || clean == "none") canonicalFallbackId else clean)
    }

    val activeSender = remember(currentUserProfile, currentUserId) {
        currentUserProfile ?: UserProfile(
            uid = currentUserId,
            displayName = FirebaseManager.auth.currentUser?.displayName ?: "Me",
            username = FirebaseManager.auth.currentUser?.email?.substringBefore("@") ?: "user",
            email = FirebaseManager.auth.currentUser?.email ?: ""
        )
    }

    LaunchedEffect(conversationId, effectiveOtherId) {
        if (!isGroup && effectiveOtherId.isNotBlank() && effectiveOtherId != "none") {
            val targetUser = otherUser ?: UserProfile(uid = effectiveOtherId)
            val res = chatRepo.getOrCreateOneToOneConversation(activeSender, targetUser)
            if (res.isSuccess) {
                val resolved = res.getOrNull()
                if (!resolved.isNullOrBlank()) {
                    effectiveConvId = resolved
                }
            }
        }
    }

    val firestoreMessages by chatRepo.getMessagesFlow(effectiveConvId).collectAsState(initial = emptyList())
    val conversationMeta by chatRepo.getConversationFlow(effectiveConvId).collectAsState(initial = null)

    // Track local pending / failed messages so UI accurately reflects sending -> sent / failed -> retrying -> sent
    val localStagedMessages = remember { mutableStateMapOf<String, ChatMessage>() }

    // Remove staged message once Firestore listener confirms it is saved
    LaunchedEffect(firestoreMessages) {
        for (remoteMsg in firestoreMessages) {
            localStagedMessages.remove(remoteMsg.id)
        }
    }

    val mergedMessages = remember(firestoreMessages, localStagedMessages.values.toList()) {
        val map = LinkedHashMap<String, ChatMessage>()
        firestoreMessages.forEach { map[it.id] = it }
        localStagedMessages.values.forEach { staged ->
            if (!map.containsKey(staged.id) || staged.status == "failed" || staged.status == "retrying") {
                map[staged.id] = staged
            }
        }
        map.values.sortedBy { it.timestamp }
    }

    var inputText by remember { mutableStateOf("") }
    var replyingToMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var selectedMessageForAction by remember { mutableStateOf<ChatMessage?>(null) }
    var messageToForward by remember { mutableStateOf<ChatMessage?>(null) }
    var showGifPicker by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var searchKeyword by remember { mutableStateOf("") }
    var isUploadingMedia by remember { mutableStateOf(false) }

    // Voice recording state
    var isRecordingVoice by remember { mutableStateOf(false) }
    var voiceRecordDuration by remember { mutableIntStateOf(0) }
    var voiceOutputFile by remember { mutableStateOf<File?>(null) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }

    val listState = rememberLazyListState()

    // Update typing indicator when inputText changes
    LaunchedEffect(inputText, effectiveConvId) {
        if (effectiveConvId.isNotBlank() && currentUserId.isNotBlank()) {
            val typing = inputText.trim().isNotEmpty()
            chatRepo.setTypingStatus(effectiveConvId, currentUserId, typing)
            if (typing) {
                delay(4000L)
                chatRepo.setTypingStatus(effectiveConvId, currentUserId, false)
            }
        }
    }

    DisposableEffect(effectiveConvId) {
        onDispose {
            try {
                mediaRecorder?.release()
            } catch (_: Exception) {}
        }
    }

    // Voice recording timer
    LaunchedEffect(isRecordingVoice) {
        if (isRecordingVoice) {
            voiceRecordDuration = 0
            while (isRecordingVoice) {
                delay(1000L)
                voiceRecordDuration++
            }
        }
    }

    val sendMessageAction: (customId: String?, text: String, mediaUrl: String, mediaType: String) -> Unit = { customId, text, mediaUrl, mediaType ->
        val textToSend = text.trim()
        if (textToSend.isNotEmpty() || mediaUrl.isNotEmpty()) {
            val msgId = customId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
            val isRetry = customId != null
            val reply = replyingToMessage
            if (!isRetry) replyingToMessage = null

            val staged = ChatMessage(
                id = msgId,
                conversationId = effectiveConvId,
                senderId = currentUserId,
                senderName = activeSender.displayName.ifBlank { activeSender.username },
                senderPhotoUrl = activeSender.photoUrl,
                text = textToSend,
                mediaUrl = mediaUrl,
                mediaType = mediaType,
                timestamp = localStagedMessages[msgId]?.timestamp ?: System.currentTimeMillis(),
                status = if (isRetry) "retrying" else "sending",
                replyToMessageId = reply?.id ?: "",
                replyToText = reply?.text ?: "",
                replyToSender = reply?.senderName ?: ""
            )
            localStagedMessages[msgId] = staged

            scope.launch {
                val res = chatRepo.sendMessage(
                    conversationId = effectiveConvId,
                    currentUser = activeSender,
                    text = textToSend,
                    mediaUrl = mediaUrl,
                    mediaType = mediaType,
                    replyTo = reply,
                    customMessageId = msgId
                )
                if (res.isSuccess) {
                    localStagedMessages.remove(msgId)
                } else {
                    localStagedMessages[msgId] = staged.copy(status = "failed")
                    Toast.makeText(
                        context,
                        "Could not send message. Tap Retry on the message.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    // Auto-mark seen and scroll to bottom
    LaunchedEffect(mergedMessages.size) {
        if (mergedMessages.isNotEmpty()) {
            val prefs = context.getSharedPreferences("nexchat_prefs", Context.MODE_PRIVATE)
            val readReceipts = prefs.getBoolean("read_receipts", true)
            chatRepo.markMessagesAsSeen(effectiveConvId, currentUserId, readReceipts)
            listState.animateScrollToItem(mergedMessages.size - 1)
        }
    }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploadingMedia = true
            scope.launch {
                try {
                    val mimeType = try { context.contentResolver.getType(uri) ?: "image/jpeg" } catch (_: Exception) { "image/jpeg" }
                    val mediaType = when {
                        mimeType.startsWith("video") -> "video"
                        mimeType.contains("gif") -> "gif"
                        else -> "image"
                    }
                    val uploadResult = CloudinaryUploader.uploadUri(context, uri, mimeType)
                    if (uploadResult.isSuccess) {
                        val url = uploadResult.getOrNull() ?: ""
                        val label = when (mediaType) {
                            "video" -> "🎥 Video"
                            "gif" -> "👾 GIF"
                            else -> "📷 Photo"
                        }
                        sendMessageAction(null, label, url, mediaType)
                    } else {
                        Toast.makeText(context, "Media upload failed. Please try again.", Toast.LENGTH_SHORT).show()
                    }
                } catch (t: Throwable) {
                    android.util.Log.e("ChatDetailScreen", "Media pick/upload error: ${t.message}", t)
                    Toast.makeText(context, "Could not load selected media", Toast.LENGTH_SHORT).show()
                } finally {
                    isUploadingMedia = false
                }
            }
        }
    }

    val isOtherTyping = remember(conversationMeta, effectiveOtherId, currentUserId) {
        val typingMap = conversationMeta?.typingUsers ?: emptyMap()
        if (isGroup) {
            typingMap.any { (uid, typing) -> uid != currentUserId && typing }
        } else {
            typingMap[effectiveOtherId] == true
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .testTag("chat_detail_screen"),
        containerColor = AliceBlue,
        topBar = {
            TopAppBar(
                title = {
                    if (isSearching) {
                        OutlinedTextField(
                            value = searchKeyword,
                            onValueChange = { searchKeyword = it },
                            placeholder = { Text("Search messages...") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 8.dp)
                                .testTag("message_search_field"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = PureWhite,
                                unfocusedContainerColor = PureWhite
                            )
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UserAvatar(
                                photoUrl = if (isGroup) conversationMeta?.groupPhotoUrl else otherUser?.photoUrl,
                                displayName = if (isGroup) (conversationMeta?.groupName?.ifBlank { "Group" } ?: "Group") else (otherUser?.displayName ?: "User"),
                                size = 42.dp,
                                isOnline = otherUser?.isOnline == true,
                                showOnlineIndicator = !isGroup
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (isGroup) (conversationMeta?.groupName?.ifBlank { "Group Chat" } ?: "Group Chat") else (otherUser?.displayName ?: "Contact"),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextDark,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = when {
                                        isOtherTyping -> "typing..."
                                        isGroup -> "${conversationMeta?.participants?.size ?: 2} members"
                                        otherUser?.isOnline == true -> "Online"
                                        else -> "Offline"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = if (isOtherTyping) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isOtherTyping || otherUser?.isOnline == true) OnlineGreen else TextDarkMuted
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isSearching) isSearching = false else onNavigateBack()
                        },
                        modifier = Modifier.testTag("chat_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PrimaryBlue)
                    }
                },
                actions = {
                    if (isSearching) {
                        IconButton(onClick = { isSearching = false; searchKeyword = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Close search", tint = TextDarkMuted)
                        }
                    } else {
                        if (!isGroup && effectiveOtherId.isNotBlank() && effectiveOtherId != "none") {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        val target = otherUser ?: UserProfile(uid = effectiveOtherId)
                                        if (target.uid.isNotBlank() && target.uid != activeSender.uid) {
                                            val res = callRepo.startCall(activeSender, target, false)
                                            if (res.isSuccess) {
                                                onStartCall(res.getOrNull()!!.id, false)
                                            } else {
                                                Toast.makeText(context, "Call failed: please check network", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "User unavailable for calls", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.testTag("voice_call_btn")
                            ) {
                                Icon(Icons.Default.Call, contentDescription = "Voice Call", tint = PrimaryBlue)
                            }
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        val target = otherUser ?: UserProfile(uid = effectiveOtherId)
                                        if (target.uid.isNotBlank() && target.uid != activeSender.uid) {
                                            val res = callRepo.startCall(activeSender, target, true)
                                            if (res.isSuccess) {
                                                onStartCall(res.getOrNull()!!.id, true)
                                            } else {
                                                Toast.makeText(context, "Call failed: please check network", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "User unavailable for calls", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                modifier = Modifier.testTag("video_call_btn")
                            ) {
                                Icon(Icons.Default.Videocam, contentDescription = "Video Call", tint = PrimaryBlue)
                            }
                        }
                        IconButton(onClick = { isSearching = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search messages", tint = TextDarkMuted)
                        }
                        Box {
                            IconButton(onClick = { showMenu = !showMenu }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = TextDarkMuted)
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Send Animated GIF") },
                                    leadingIcon = { Icon(Icons.Default.GifBox, contentDescription = null, tint = PrimaryBlue) },
                                    onClick = {
                                        showMenu = false
                                        showGifPicker = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export Chat History") },
                                    leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = PrimaryBlue) },
                                    onClick = {
                                        showMenu = false
                                        val conv = Conversation(id = effectiveConvId, isGroup = isGroup)
                                        val exported = chatRepo.exportChatHistory(conv, mergedMessages)
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, "NexChat Pro Export")
                                            putExtra(Intent.EXTRA_TEXT, exported)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Export Chat"))
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PureWhite)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val displayMessages = remember(mergedMessages, searchKeyword) {
                if (searchKeyword.isBlank()) mergedMessages
                else mergedMessages.filter { it.text.lowercase().contains(searchKeyword.lowercase()) }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Spacer(modifier = Modifier.height(6.dp)) }
                items(displayMessages, key = { it.id }) { msg ->
                    if (!msg.deletedForUsers.contains(currentUserId)) {
                        WhiteLightBlueMessageBubble(
                            message = msg,
                            isCurrentUser = msg.senderId == currentUserId,
                            onLongPress = { selectedMessageForAction = msg },
                            onRetry = {
                                sendMessageAction(msg.id, msg.text, msg.mediaUrl, msg.mediaType)
                            }
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(6.dp)) }
            }

            if (isUploadingMedia) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(IceBlue)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = PrimaryBlue)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Uploading media...", fontSize = 13.sp, color = PrimaryBlue)
                }
            }

            replyingToMessage?.let { replyMsg ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(IceBlue)
                        .border(1.dp, SoftBlueBorder)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Replying to ${replyMsg.senderName}", fontWeight = FontWeight.Bold, color = PrimaryBlue, fontSize = 12.sp)
                        Text(replyMsg.text, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = TextDark)
                    }
                    IconButton(onClick = { replyingToMessage = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel reply", modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Input Row
            Surface(
                color = PureWhite,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                if (isRecordingVoice) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(ErrorRed)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Recording Voice: ${voiceRecordDuration}s", color = ErrorRed, fontWeight = FontWeight.Bold)
                        }

                        Row {
                            IconButton(
                                onClick = {
                                    try {
                                        mediaRecorder?.stop()
                                        mediaRecorder?.release()
                                    } catch (_: Exception) {}
                                    mediaRecorder = null
                                    isRecordingVoice = false
                                    voiceOutputFile?.delete()
                                }
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel Voice", tint = ErrorRed)
                            }

                            IconButton(
                                onClick = {
                                    try {
                                        mediaRecorder?.stop()
                                        mediaRecorder?.release()
                                        mediaRecorder = null
                                        isRecordingVoice = false

                                        val file = voiceOutputFile
                                        if (file != null && file.exists()) {
                                            val dur = voiceRecordDuration.coerceAtLeast(1)
                                            scope.launch {
                                                isUploadingMedia = true
                                                val uploadResult = CloudinaryUploader.uploadFile(file, "audio/mp4")
                                                val url = uploadResult.getOrNull() ?: ""
                                                if (url.isNotEmpty()) {
                                                    chatRepo.sendMessage(
                                                        conversationId = effectiveConvId,
                                                        currentUser = activeSender,
                                                        text = "🎤 Voice message",
                                                        mediaUrl = url,
                                                        mediaType = "voice",
                                                        voiceDurationSeconds = dur,
                                                        replyTo = replyingToMessage
                                                    )
                                                    replyingToMessage = null
                                                } else {
                                                    Toast.makeText(context, "Voice upload failed", Toast.LENGTH_SHORT).show()
                                                }
                                                isUploadingMedia = false
                                                file.delete()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        isRecordingVoice = false
                                    }
                                },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(PrimaryBlue)
                                    .testTag("stop_voice_send_btn")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Voice", tint = Color.White)
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                mediaPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                )
                            },
                            modifier = Modifier.testTag("attach_media_button")
                        ) {
                            Icon(Icons.Default.AttachFile, contentDescription = "Attach media", tint = PrimaryBlue)
                        }

                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Type a message...", color = TextDarkMuted) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("message_input_field"),
                            shape = RoundedCornerShape(24.dp),
                            maxLines = 4,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Send
                            ),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (inputText.isNotBlank()) {
                                        val t = inputText
                                        inputText = ""
                                        sendMessageAction(null, t, "", "text")
                                    }
                                }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = AliceBlue,
                                unfocusedContainerColor = AliceBlue,
                                focusedBorderColor = PrimaryBlue,
                                unfocusedBorderColor = SoftBlueBorder
                            )
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        if (inputText.isBlank()) {
                            // Voice Record Button when input is empty
                            IconButton(
                                onClick = {
                                    try {
                                        val outFile = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
                                        voiceOutputFile = outFile
                                        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            MediaRecorder(context)
                                        } else {
                                            @Suppress("DEPRECATION")
                                            MediaRecorder()
                                        }
                                        recorder.apply {
                                            setAudioSource(MediaRecorder.AudioSource.MIC)
                                            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                                            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                                            setOutputFile(outFile.absolutePath)
                                            prepare()
                                            start()
                                        }
                                        mediaRecorder = recorder
                                        isRecordingVoice = true
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Grant microphone permission to record voice notes", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(IceBlue)
                                    .testTag("record_voice_button")
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = "Record Voice", tint = PrimaryBlue)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        IconButton(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    val t = inputText
                                    inputText = ""
                                    sendMessageAction(null, t, "", "text")
                                }
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(PrimaryBlue)
                                .testTag("send_message_button")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }

    // Message Action Dialog (Reply, Forward, Copy, Delete for Me, Delete for Everyone)
    selectedMessageForAction?.let { msg ->
        val isSender = msg.senderId == currentUserId
        AlertDialog(
            onDismissRequest = { selectedMessageForAction = null },
            title = { Text("Message Actions", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            replyingToMessage = msg
                            selectedMessageForAction = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Reply, contentDescription = null, tint = PrimaryBlue)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Reply", color = PrimaryBlue)
                    }

                    TextButton(
                        onClick = {
                            messageToForward = msg
                            selectedMessageForAction = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Forward, contentDescription = null, tint = PrimaryBlue)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Forward Message", color = PrimaryBlue)
                    }

                    if (msg.text.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("NexChat Message", msg.text))
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                selectedMessageForAction = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Copy Text")
                        }
                    }

                    TextButton(
                        onClick = {
                            scope.launch {
                                chatRepo.deleteMessageForMe(effectiveConvId, msg.id, currentUserId)
                                selectedMessageForAction = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Delete for Me", color = ErrorRed)
                    }

                    if (isSender) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    chatRepo.deleteMessageForEveryone(effectiveConvId, msg.id)
                                    selectedMessageForAction = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Delete for Everyone", color = ErrorRed, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedMessageForAction = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Forward Message Target Contact Picker Dialog
    messageToForward?.let { forwardMsg ->
        val contacts = allUsers.filter { it.uid != currentUserId && it.uid.isNotBlank() }
        AlertDialog(
            onDismissRequest = { messageToForward = null },
            title = { Text("Forward Message To", fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(contacts, key = { it.uid }) { user ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch {
                                        val convRes = chatRepo.getOrCreateOneToOneConversation(activeSender, user)
                                        val targetConvId = convRes.getOrNull() ?: chatRepo.getCanonicalOneToOneId(currentUserId, user.uid)
                                        val res = chatRepo.forwardMessage(targetConvId, activeSender, forwardMsg)
                                        if (res.isSuccess) {
                                            Toast.makeText(context, "Forwarded to ${user.displayName}", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Forward failed", Toast.LENGTH_SHORT).show()
                                        }
                                        messageToForward = null
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatar(photoUrl = user.photoUrl, displayName = user.displayName, size = 40.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(user.displayName.ifBlank { user.username }, fontWeight = FontWeight.SemiBold)
                                Text("@${user.username}", fontSize = 12.sp, color = TextDarkMuted)
                            }
                        }
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { messageToForward = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Quick GIF Picker Dialog
    if (showGifPicker) {
        AlertDialog(
            onDismissRequest = { showGifPicker = false },
            title = { Text("Send a GIF", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    QUICK_GIFS.forEachIndexed { idx, gifUrl ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showGifPicker = false
                                    sendMessageAction(null, "👾 GIF", gifUrl, "gif")
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = IceBlue
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = gifUrl,
                                    contentDescription = "GIF ${idx + 1}",
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Tap to send Reaction GIF #${idx + 1}", fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showGifPicker = false }) {
                    Text("Close")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WhiteLightBlueMessageBubble(
    message: ChatMessage,
    isCurrentUser: Boolean,
    onLongPress: () -> Unit,
    onRetry: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var isPlayingAudio by remember { mutableStateOf(false) }
    var audioPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(message.id) {
        onDispose {
            audioPlayer?.release()
            audioPlayer = null
        }
    }

    val bubbleBg = if (isCurrentUser) SenderBubbleColor else ReceiverBubbleColor
    val textColor = if (isCurrentUser) TextSender else TextDark

    val timeFormatted = remember(message.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isCurrentUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 290.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongPress
                )
                .testTag("message_bubble_${message.id}"),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isCurrentUser) 16.dp else 4.dp,
                bottomEnd = if (isCurrentUser) 4.dp else 16.dp
            ),
            color = bubbleBg,
            border = if (!isCurrentUser) androidx.compose.foundation.BorderStroke(1.dp, BubbleBorderColor) else null,
            shadowElevation = 1.dp
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                if (message.forwarded) {
                    Text(
                        text = "↪ Forwarded",
                        fontSize = 11.sp,
                        fontStyle = FontStyle.Italic,
                        color = PrimaryBlue,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }

                // Quoted Reply Preview
                if (message.replyToText.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isCurrentUser) IceBlue else AliceBlue)
                            .border(1.dp, SoftBlueBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Column {
                            Text(message.replyToSender, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = PrimaryBlue)
                            Text(message.replyToText, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = TextDark)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Media: Image or GIF
                if ((message.mediaType == "image" || message.mediaType == "gif") && message.mediaUrl.isNotEmpty()) {
                    AsyncImage(
                        model = message.mediaUrl,
                        contentDescription = "Shared image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Media: Video
                if (message.mediaType == "video" && message.mediaUrl.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0A192F))
                            .clickable {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(message.mediaUrl))
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play Video", tint = Color.White, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Tap to Play Video", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Media: Audio / Voice
                if ((message.mediaType == "voice" || message.mediaType == "audio") && message.mediaUrl.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (isPlayingAudio) {
                                    audioPlayer?.pause()
                                    isPlayingAudio = false
                                } else {
                                    try {
                                        if (audioPlayer == null) {
                                            audioPlayer = MediaPlayer().apply {
                                                setDataSource(message.mediaUrl)
                                                prepareAsync()
                                                setOnPreparedListener {
                                                    start()
                                                    isPlayingAudio = true
                                                }
                                                setOnCompletionListener {
                                                    isPlayingAudio = false
                                                }
                                            }
                                        } else {
                                            audioPlayer?.start()
                                            isPlayingAudio = true
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Cannot play audio", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(PrimaryBlue)
                        ) {
                            Icon(
                                imageVector = if (isPlayingAudio) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = if (isPlayingAudio) "Stop" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column {
                            Text("Voice Message", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = textColor)
                            Text(
                                if (message.voiceDurationSeconds > 0) "${message.voiceDurationSeconds}s" else "Audio",
                                fontSize = 11.sp,
                                color = TextDarkMuted
                            )
                        }
                    }
                }

                // Text Content
                if (message.text.isNotEmpty()) {
                    Text(
                        text = message.text,
                        fontSize = 15.sp,
                        fontStyle = if (message.deletedForEveryone) FontStyle.Italic else FontStyle.Normal,
                        color = if (message.deletedForEveryone) TextDarkMuted else textColor
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Time and Status Checkmarks
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeFormatted,
                        fontSize = 11.sp,
                        color = TextDarkMuted
                    )
                    if (isCurrentUser) {
                        Spacer(modifier = Modifier.width(4.dp))
                        if (message.status == "failed") {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { onRetry?.invoke() }
                                    .padding(horizontal = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                MessageStatusIcon(status = message.status)
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("Retry", fontSize = 10.sp, color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                            }
                        } else {
                            MessageStatusIcon(status = message.status)
                        }
                    }
                }
            }
        }
    }
}
