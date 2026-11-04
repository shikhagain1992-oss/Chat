package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
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
import com.example.ui.theme.DeliveredGrey
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.IceBlue
import com.example.ui.theme.LightBlueTint
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
    val otherUser by userRepo.getUserProfile(otherUserId).collectAsState(initial = null)

    val canonicalFallbackId = remember(currentUserId, otherUserId) {
        if (currentUserId.isNotBlank() && otherUserId.isNotBlank() && otherUserId != "none") {
            if (currentUserId < otherUserId) "${currentUserId}_${otherUserId}" else "${otherUserId}_${currentUserId}"
        } else ""
    }

    var effectiveConvId by remember(conversationId, canonicalFallbackId) {
        mutableStateOf(if (conversationId.isBlank() || conversationId == "none") canonicalFallbackId else conversationId)
    }

    // Fallback user profile so message sending is NEVER blocked by slow profile fetch
    val activeSender = remember(currentUserProfile, currentUserId) {
        currentUserProfile ?: UserProfile(
            uid = currentUserId,
            displayName = FirebaseManager.auth.currentUser?.displayName ?: "Me",
            username = FirebaseManager.auth.currentUser?.email?.substringBefore("@") ?: "user",
            email = FirebaseManager.auth.currentUser?.email ?: ""
        )
    }

    // Auto-resolve / verify existing conversation ID in background
    LaunchedEffect(conversationId, otherUserId) {
        if (otherUserId.isNotBlank() && otherUserId != "none") {
            val targetUser = otherUser ?: UserProfile(uid = otherUserId)
            val res = chatRepo.getOrCreateOneToOneConversation(activeSender, targetUser)
            if (res.isSuccess) {
                val resolved = res.getOrNull()
                if (!resolved.isNullOrBlank()) {
                    effectiveConvId = resolved
                }
            }
        }
    }

    val messages by chatRepo.getMessagesFlow(effectiveConvId).collectAsState(initial = emptyList())

    var inputText by remember { mutableStateOf("") }
    var replyingToMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var selectedMessageForAction by remember { mutableStateOf<ChatMessage?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var searchKeyword by remember { mutableStateOf("") }
    var isUploadingMedia by remember { mutableStateOf(false) }

    // Hold-to-record voice state
    var isRecordingVoice by remember { mutableStateOf(false) }
    var voiceRecordDuration by remember { mutableIntStateOf(0) }
    var voiceOutputFile by remember { mutableStateOf<File?>(null) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }

    val listState = rememberLazyListState()

    // Auto-mark seen and scroll to bottom
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            chatRepo.markMessagesAsSeen(effectiveConvId, currentUserId)
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Media Picker launcher
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploadingMedia = true
            scope.launch {
                val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                val mediaType = if (mimeType.startsWith("video")) "video" else "image"
                val uploadResult = CloudinaryUploader.uploadUri(context, uri, mimeType)
                if (uploadResult.isSuccess) {
                    val url = uploadResult.getOrNull() ?: ""
                    chatRepo.sendMessage(
                        conversationId = effectiveConvId,
                        currentUser = activeSender,
                        text = if (mediaType == "video") "🎥 Video" else "📷 Photo",
                        mediaUrl = url,
                        mediaType = mediaType,
                        replyTo = replyingToMessage
                    )
                    replyingToMessage = null
                } else {
                    Toast.makeText(context, "Media upload failed. Check connection.", Toast.LENGTH_SHORT).show()
                }
                isUploadingMedia = false
            }
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
                                photoUrl = if (isGroup) "" else otherUser?.photoUrl,
                                displayName = if (isGroup) "Group" else (otherUser?.displayName ?: "User"),
                                size = 42.dp,
                                isOnline = otherUser?.isOnline == true,
                                showOnlineIndicator = !isGroup
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (isGroup) "Group Chat" else (otherUser?.displayName ?: "Contact"),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextDark,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (isGroup) "Members" else if (otherUser?.isOnline == true) "Online" else "Offline",
                                    fontSize = 12.sp,
                                    color = if (otherUser?.isOnline == true) OnlineGreen else TextDarkMuted
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
                        if (!isGroup && otherUserId.isNotBlank() && otherUserId != "none") {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        val target = otherUser ?: UserProfile(uid = otherUserId)
                                        val res = callRepo.startCall(activeSender, target, false)
                                        if (res.isSuccess) {
                                            onStartCall(res.getOrNull()!!.id, false)
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
                                        val target = otherUser ?: UserProfile(uid = otherUserId)
                                        val res = callRepo.startCall(activeSender, target, true)
                                        if (res.isSuccess) {
                                            onStartCall(res.getOrNull()!!.id, true)
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
                                    text = { Text("Export Chat History") },
                                    leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = PrimaryBlue) },
                                    onClick = {
                                        showMenu = false
                                        val conv = Conversation(id = effectiveConvId, isGroup = isGroup)
                                        val exported = chatRepo.exportChatHistory(conv, messages)
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
            val displayMessages = remember(messages, searchKeyword) {
                if (searchKeyword.isBlank()) messages
                else messages.filter { it.text.lowercase().contains(searchKeyword.lowercase()) }
            }

            // Message Bubble List
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
                            onLongPress = { selectedMessageForAction = msg }
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(6.dp)) }
            }

            // Uploading Media Progress Bar
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

            // Reply Preview Banner
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

            // Input Row (White & Light Blue Mix)
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

                        IconButton(
                            onClick = {
                                try {
                                    mediaRecorder?.stop()
                                    mediaRecorder?.release()
                                    mediaRecorder = null
                                    isRecordingVoice = false

                                    val file = voiceOutputFile
                                    if (file != null && file.exists()) {
                                        scope.launch {
                                            isUploadingMedia = true
                                            val uploadResult = CloudinaryUploader.uploadFile(file, "audio/mp4")
                                            val url = uploadResult.getOrNull() ?: ""
                                            chatRepo.sendMessage(
                                                conversationId = effectiveConvId,
                                                currentUser = activeSender,
                                                text = "🎤 Voice message",
                                                mediaUrl = url,
                                                mediaType = "voice",
                                                voiceDurationSeconds = voiceRecordDuration,
                                                replyTo = replyingToMessage
                                            )
                                            replyingToMessage = null
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
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = AliceBlue,
                                unfocusedContainerColor = AliceBlue,
                                focusedBorderColor = PrimaryBlue,
                                unfocusedBorderColor = SoftBlueBorder
                            )
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        // Send Button (Guaranteed delivery - fallback activeSender)
                        IconButton(
                            onClick = {
                                val textToSend = inputText.trim()
                                if (textToSend.isNotEmpty()) {
                                    inputText = ""
                                    scope.launch {
                                        val res = chatRepo.sendMessage(
                                            conversationId = effectiveConvId,
                                            currentUser = activeSender,
                                            text = textToSend,
                                            replyTo = replyingToMessage
                                        )
                                        if (res.isFailure) {
                                            Toast.makeText(
                                                context,
                                                "Failed to send: ${res.exceptionOrNull()?.localizedMessage ?: "Network error"}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        replyingToMessage = null
                                    }
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

    // Message action context dialog
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WhiteLightBlueMessageBubble(
    message: ChatMessage,
    isCurrentUser: Boolean,
    onLongPress: () -> Unit
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

                // Media: Image
                if (message.mediaType == "image" && message.mediaUrl.isNotEmpty()) {
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
                        color = textColor
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
                        MessageStatusIcon(status = message.status)
                    }
                }
            }
        }
    }
}
