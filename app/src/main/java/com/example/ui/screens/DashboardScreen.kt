package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CallRecord
import com.example.data.model.Conversation
import com.example.data.model.StatusStory
import com.example.data.model.UserProfile
import com.example.data.remote.FirebaseManager
import com.example.data.repository.CallRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.StatusRepository
import com.example.data.repository.UserRepository
import com.example.service.NexChatCallService
import com.example.ui.components.UserAvatar
import com.example.ui.theme.DeliveredGrey
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavySurface
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueLight
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SeenDoubleBlue
import com.example.ui.theme.SoftBlueBorder
import com.example.ui.theme.TextDark
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    onNavigateToChatDetail: (String, String, Boolean) -> Unit,
    onNavigateToUserSearch: () -> Unit,
    onNavigateToGroupCreate: () -> Unit,
    onNavigateToStatusUpload: () -> Unit,
    onNavigateToStatusViewer: (String) -> Unit,
    onNavigateToCallScreen: (String, Boolean, Boolean) -> Unit,
    onNavigateToProfileEdit: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    val authUser by FirebaseManager.authStateFlow.collectAsState(initial = FirebaseManager.currentUser)
    val currentUserId = authUser?.uid ?: FirebaseManager.currentUserId ?: ""

    val userRepo = remember { UserRepository() }
    val chatRepo = remember { ChatRepository() }
    val statusRepo = remember { StatusRepository() }
    val callRepo = remember { CallRepository() }

    val currentUserProfile by userRepo.getUserProfile(currentUserId)
        .collectAsState(initial = null)

    // Presence management
    DisposableEffect(Unit) {
        scope.launch { userRepo.setUserOnline(true) }
        onDispose {
            scope.launch { userRepo.setUserOnline(false) }
        }
    }

    // Check battery optimization prompt once
    LaunchedEffect(Unit) {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && powerManager != null) {
                if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            }
        } catch (e: Exception) {
            // Ignore if restricted
        }
    }

    // Listen for incoming calls
    val incomingCall by callRepo.listenForIncomingCalls(currentUserId)
        .collectAsState(initial = null)

    LaunchedEffect(incomingCall) {
        val call = incomingCall
        if (call != null && call.status == "ringing") {
            // Start foreground call alert service
            val serviceIntent = Intent(context, NexChatCallService::class.java).apply {
                action = NexChatCallService.ACTION_INCOMING_CALL
                putExtra(NexChatCallService.EXTRA_CALL_ID, call.id)
                putExtra(NexChatCallService.EXTRA_CALLER_NAME, call.callerName)
                putExtra(NexChatCallService.EXTRA_IS_VIDEO, call.isVideo)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("dashboard_bottom_bar"),
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Chat, contentDescription = "Chats") },
                    label = { Text("Chats") },
                    modifier = Modifier.testTag("tab_chats")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.PhotoCamera, contentDescription = "Status") },
                    label = { Text("Status") },
                    modifier = Modifier.testTag("tab_status")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Call, contentDescription = "Calls") },
                    label = { Text("Calls") },
                    modifier = Modifier.testTag("tab_calls")
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    modifier = Modifier.testTag("tab_settings")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> ChatsTab(
                    currentUserId = currentUserId,
                    chatRepo = chatRepo,
                    onNavigateToChatDetail = onNavigateToChatDetail,
                    onNavigateToUserSearch = onNavigateToUserSearch,
                    onNavigateToGroupCreate = onNavigateToGroupCreate
                )
                1 -> StatusTab(
                    currentUser = currentUserProfile,
                    statusRepo = statusRepo,
                    onNavigateToStatusUpload = onNavigateToStatusUpload,
                    onNavigateToStatusViewer = onNavigateToStatusViewer
                )
                2 -> CallsTab(
                    currentUserId = currentUserId,
                    callRepo = callRepo,
                    onNavigateToUserSearch = onNavigateToUserSearch,
                    onNavigateToCallScreen = onNavigateToCallScreen
                )
                3 -> SettingsTab(
                    currentUser = currentUserProfile,
                    onNavigateToProfileEdit = onNavigateToProfileEdit,
                    onLogout = {
                        scope.launch {
                            userRepo.setUserOnline(false)
                            FirebaseManager.auth.signOut()
                            onLogout()
                        }
                    }
                )
            }

            // Incoming Call floating dialog if in foreground
            incomingCall?.let { call ->
                if (call.status == "ringing") {
                    IncomingCallBanner(
                        call = call,
                        onAccept = {
                            val stopIntent = Intent(context, NexChatCallService::class.java).apply {
                                action = NexChatCallService.ACTION_ACCEPT_CALL
                            }
                            context.startService(stopIntent)
                            scope.launch {
                                callRepo.updateCallStatus(call.id, "accepted")
                                onNavigateToCallScreen(call.id, false, call.isVideo)
                            }
                        },
                        onDecline = {
                            val stopIntent = Intent(context, NexChatCallService::class.java).apply {
                                action = NexChatCallService.ACTION_END_CALL
                            }
                            context.startService(stopIntent)
                            scope.launch {
                                callRepo.updateCallStatus(call.id, "rejected")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ChatsTab(
    currentUserId: String,
    chatRepo: ChatRepository,
    onNavigateToChatDetail: (String, String, Boolean) -> Unit,
    onNavigateToUserSearch: () -> Unit,
    onNavigateToGroupCreate: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val conversations by chatRepo.getConversationsFlow(currentUserId)
        .collectAsState(initial = emptyList())

    val filtered = remember(conversations, searchQuery) {
        if (searchQuery.isBlank()) conversations
        else {
            val q = searchQuery.lowercase()
            conversations.filter { conv ->
                val name = if (conv.isGroup) conv.groupName else {
                    val otherId = conv.participants.firstOrNull { it != currentUserId } ?: ""
                    conv.participantNames[otherId] ?: ""
                }
                name.lowercase().contains(q) || conv.lastMessageText.lowercase().contains(q)
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FloatingActionButton(
                    onClick = onNavigateToGroupCreate,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = PrimaryBlue,
                    modifier = Modifier.size(46.dp).testTag("fab_new_group")
                ) {
                    Icon(Icons.Default.GroupAdd, contentDescription = "New Group")
                }
                FloatingActionButton(
                    onClick = onNavigateToUserSearch,
                    containerColor = PrimaryBlue,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("fab_new_chat")
                ) {
                    Icon(Icons.Default.Chat, contentDescription = "New Chat")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NexChat Pro",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search conversations or contacts...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("chat_search_field"),
                shape = RoundedCornerShape(14.dp)
            )

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = null,
                            tint = PrimaryBlueLight,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = if (searchQuery.isBlank()) "No conversations yet" else "No matching chats found",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Tap the message button below to discover contacts and start chatting!",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 6.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filtered, key = { it.id }) { conv ->
                        ConversationItem(
                            conversation = conv,
                            currentUserId = currentUserId,
                            onClick = {
                                val otherId = conv.participants.firstOrNull { it != currentUserId && it.isNotBlank() }
                                    ?: if (conv.id.contains("_")) {
                                        val parts = conv.id.split("_")
                                        if (parts.size == 2) if (parts[0] == currentUserId) parts[1] else parts[0] else "none"
                                    } else "none"
                                onNavigateToChatDetail(conv.id, otherId, conv.isGroup)
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 76.dp, end = 16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConversationItem(
    conversation: Conversation,
    currentUserId: String,
    onClick: () -> Unit
) {
    val isGroup = conversation.isGroup
    val otherUserId = conversation.participants.firstOrNull { it != currentUserId && it.isNotBlank() }
        ?: if (conversation.id.contains("_")) {
            val parts = conversation.id.split("_")
            if (parts.size == 2) if (parts[0] == currentUserId) parts[1] else parts[0] else ""
        } else ""

    val userRepo = remember { UserRepository() }
    val dynamicOtherUser by userRepo.getUserProfile(otherUserId).collectAsState(initial = null)

    val title = if (isGroup) {
        conversation.groupName.ifBlank { "Group" }
    } else {
        dynamicOtherUser?.displayName?.ifBlank { dynamicOtherUser?.username }
            ?: conversation.participantNames[otherUserId]?.ifBlank { "Contact" }
            ?: "Contact"
    }
    val photo = if (isGroup) conversation.groupPhotoUrl else (dynamicOtherUser?.photoUrl ?: conversation.participantPhotos[otherUserId])
    val timeFormatted = remember(conversation.lastMessageTimestamp) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.format(Date(conversation.lastMessageTimestamp))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("conversation_item_${conversation.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(
            photoUrl = photo,
            displayName = title,
            size = 52.dp,
            showOnlineIndicator = !isGroup
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = timeFormatted,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = conversation.lastMessageText.ifBlank { "No messages yet" },
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun StatusTab(
    currentUser: UserProfile?,
    statusRepo: StatusRepository,
    onNavigateToStatusUpload: () -> Unit,
    onNavigateToStatusViewer: (String) -> Unit
) {
    val currentUserId = currentUser?.uid ?: ""
    val allStatuses by statusRepo.getActiveStatusesFlow().collectAsState(initial = emptyList())

    val myStatus = allStatuses.firstOrNull { it.userId == currentUserId }
    val othersStatuses = allStatuses.filter { it.userId != currentUserId }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToStatusUpload,
                containerColor = PrimaryBlue,
                contentColor = Color.White,
                modifier = Modifier.testTag("fab_add_status")
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = "Add Status")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            item {
                Text(
                    text = "Status Stories",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 14.dp)
                )
            }

            // My Status Section
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (myStatus != null) {
                                onNavigateToStatusViewer(myStatus.id)
                            } else {
                                onNavigateToStatusUpload()
                            }
                        }
                        .padding(vertical = 10.dp)
                        .testTag("my_status_item"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        UserAvatar(
                            photoUrl = currentUser?.photoUrl,
                            displayName = currentUser?.displayName ?: "Me",
                            size = 56.dp
                        )
                        if (myStatus == null) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(PrimaryBlue),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Add",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "My Status",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (myStatus != null) "Tap to view your 24h story" else "Tap to add story update",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            item {
                Text(
                    text = "Recent Updates (${othersStatuses.size})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            if (othersStatuses.isEmpty()) {
                item {
                    Text(
                        text = "No recent status updates from your contacts",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 18.dp)
                    )
                }
            } else {
                items(othersStatuses, key = { it.id }) { story ->
                    val isViewed = story.viewers.contains(currentUserId)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToStatusViewer(story.id) }
                            .padding(vertical = 10.dp)
                            .testTag("status_item_${story.id}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                                .border(
                                    width = 2.5.dp,
                                    color = if (isViewed) DeliveredGrey else PrimaryBlue,
                                    shape = CircleShape
                                )
                                .padding(2.5.dp)
                        ) {
                            UserAvatar(
                                photoUrl = story.userPhotoUrl,
                                displayName = story.userName,
                                size = 50.dp
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = story.userName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(story.createdAt))
                            Text(
                                text = "Today at $timeStr",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CallsTab(
    currentUserId: String,
    callRepo: CallRepository,
    onNavigateToUserSearch: () -> Unit,
    onNavigateToCallScreen: (String, Boolean, Boolean) -> Unit
) {
    val callLogs by callRepo.getCallHistoryFlow(currentUserId)
        .collectAsState(initial = emptyList())

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToUserSearch,
                containerColor = PrimaryBlue,
                contentColor = Color.White,
                modifier = Modifier.testTag("fab_new_call")
            ) {
                Icon(Icons.Default.Call, contentDescription = "New Call")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Call History",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 14.dp)
            )

            if (callLogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Call,
                            contentDescription = null,
                            tint = PrimaryBlueLight,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("No recent calls", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Start real-time voice and video calls with friends",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(callLogs, key = { it.id }) { call ->
                        val isOutgoing = call.callerId == currentUserId
                        val contactName = if (isOutgoing) call.receiverName else call.callerName
                        val contactPhoto = if (isOutgoing) call.receiverPhotoUrl else call.callerPhotoUrl
                        val isMissed = call.status == "missed" || call.status == "rejected"
                        val timeStr = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(call.timestamp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onNavigateToCallScreen(call.id, true, call.isVideo)
                                }
                                .padding(vertical = 10.dp)
                                .testTag("call_item_${call.id}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatar(photoUrl = contactPhoto, displayName = contactName, size = 50.dp)

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = contactName,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isMissed && !isOutgoing) ErrorRed else MaterialTheme.colorScheme.onBackground
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = when {
                                            isMissed && !isOutgoing -> Icons.Default.CallMissed
                                            isOutgoing -> Icons.Default.CallMade
                                            else -> Icons.Default.CallReceived
                                        },
                                        contentDescription = null,
                                        tint = if (isMissed && !isOutgoing) ErrorRed else OnlineGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "$timeStr • ${if (call.durationSeconds > 0) "${call.durationSeconds}s" else call.status}",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    onNavigateToCallScreen(call.id, true, call.isVideo)
                                }
                            ) {
                                Icon(
                                    imageVector = if (call.isVideo) Icons.Default.Videocam else Icons.Default.Call,
                                    contentDescription = "Call back",
                                    tint = PrimaryBlue
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsTab(
    currentUser: UserProfile?,
    onNavigateToProfileEdit: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    var notificationsEnabled by remember { mutableStateOf(true) }
    var readReceiptsEnabled by remember { mutableStateOf(true) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("settings_tab")
    ) {
        item {
            Text(
                text = "Settings",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }

        // Profile Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToProfileEdit() }
                    .padding(vertical = 8.dp)
                    .testTag("profile_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UserAvatar(
                        photoUrl = currentUser?.photoUrl,
                        displayName = currentUser?.displayName ?: "User",
                        size = 64.dp
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentUser?.displayName ?: "NexChat User",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "@${currentUser?.username ?: "user"}",
                            fontSize = 14.sp,
                            color = PrimaryBlue
                        )
                        Text(
                            text = currentUser?.about ?: "Hey there! I am using NexChat Pro.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(Icons.Default.Edit, contentDescription = "Edit Profile", tint = PrimaryBlue)
                }
            }
        }

        // Settings items
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Text("Preferences", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))

            // Notifications switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Notifications, contentDescription = null, tint = PrimaryBlue)
                    Spacer(modifier = Modifier.width(14.dp))
                    Text("Chat & Call Notifications", fontSize = 16.sp)
                }
                Switch(checked = notificationsEnabled, onCheckedChange = { notificationsEnabled = it })
            }

            // Read receipts switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = PrimaryBlue)
                    Spacer(modifier = Modifier.width(14.dp))
                    Text("Read Receipts (Double Blue Ticks)", fontSize = 16.sp)
                }
                Switch(checked = readReceiptsEnabled, onCheckedChange = { readReceiptsEnabled = it })
            }

            // Battery Optimization & Always Allow Permissions
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        try {
                            // First open direct app details settings so user can set "Allow all the time"
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                            Toast.makeText(context, "Select 'Permissions' and 'Battery' -> Choose 'Unrestricted' / 'Allow all the time'", Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    val bIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(bIntent)
                                }
                            } catch (e2: Exception) {
                                Toast.makeText(context, "Could not open settings", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = OnlineGreen)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Always Allow & Background Calls", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text("Tap to set Battery to 'Unrestricted' and allow 24/7 incoming calls even when phone is locked", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // About NexChat Pro
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = PrimaryBlue)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("About NexChat Pro", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Version 1.0.0 (Build 2026)\nPowered by Firebase Firestore real-time messaging, Cloudinary media storage up to 1GB, and high-performance WebRTC voice & video calling engine.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Logout Button
            TextButton(
                onClick = { showLogoutDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("logout_button")
            ) {
                Icon(Icons.Default.Logout, contentDescription = "Log out", tint = ErrorRed)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Log Out", color = ErrorRed, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log Out") },
            text = { Text("Are you sure you want to log out of NexChat Pro?") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    onLogout()
                }) {
                    Text("Log Out", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun IncomingCallBanner(
    call: CallRecord,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(16.dp)
            .border(1.5.dp, SoftBlueBorder, RoundedCornerShape(16.dp))
            .testTag("incoming_call_banner"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PureWhite)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatar(photoUrl = call.callerPhotoUrl, displayName = call.callerName, size = 48.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Incoming ${if (call.isVideo) "Video" else "Voice"} Call", color = PrimaryBlue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(call.callerName, color = TextDark, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            IconButton(
                onClick = onDecline,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(ErrorRed)
                    .testTag("banner_decline_btn")
            ) {
                Icon(Icons.Default.CallEnd, contentDescription = "Decline", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onAccept,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(OnlineGreen)
                    .testTag("banner_accept_btn")
            ) {
                Icon(Icons.Default.Call, contentDescription = "Answer", tint = Color.White)
            }
        }
    }
}
