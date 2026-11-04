package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.FirebaseManager
import com.example.data.repository.CallRepository
import com.example.service.NexChatCallService
import com.example.ui.components.UserAvatar
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavySurface
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueLight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CallScreen(
    callId: String,
    isCaller: Boolean,
    initialIsVideo: Boolean,
    onCallEnded: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val callRepo = remember { CallRepository() }
    val currentUserId = FirebaseManager.currentUserId ?: ""

    val callRecord by callRepo.listenToCall(callId).collectAsState(initial = null)

    var isMuted by remember { mutableStateOf(false) }
    var isVideoEnabled by remember { mutableStateOf(initialIsVideo) }
    var isSpeakerOn by remember { mutableStateOf(initialIsVideo) }
    var callDurationSeconds by remember { mutableIntStateOf(0) }
    var isConnected by remember { mutableStateOf(false) }

    // Audio manager for speakerphone
    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    LaunchedEffect(isSpeakerOn) {
        audioManager?.isSpeakerphoneOn = isSpeakerOn
    }

    // Call state monitor
    LaunchedEffect(callRecord?.status) {
        val status = callRecord?.status
        if (status == "accepted") {
            isConnected = true
        } else if (status == "ended" || status == "rejected") {
            // Stop service and navigate back
            val stopIntent = Intent(context, NexChatCallService::class.java).apply {
                action = NexChatCallService.ACTION_END_CALL
            }
            context.startService(stopIntent)
            onCallEnded()
        }
    }

    // Call duration timer once connected
    LaunchedEffect(isConnected) {
        if (isConnected) {
            while (true) {
                delay(1000)
                callDurationSeconds++
            }
        }
    }

    // Clean up service on dispose
    DisposableEffect(Unit) {
        onDispose {
            val stopIntent = Intent(context, NexChatCallService::class.java).apply {
                action = NexChatCallService.ACTION_END_CALL
            }
            context.startService(stopIntent)
        }
    }

    val contactName = if (callRecord?.callerId == currentUserId) {
        callRecord?.receiverName ?: "Contact"
    } else {
        callRecord?.callerName ?: "Contact"
    }
    val contactPhoto = if (callRecord?.callerId == currentUserId) {
        callRecord?.receiverPhotoUrl
    } else {
        callRecord?.callerPhotoUrl
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(NavyDark, NavySurface, DarkBackground)
                )
            )
            .testTag("call_screen")
    ) {
        // Video Preview Mock or Full Avatar
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(1f))

            if (isVideoEnabled) {
                Card(
                    modifier = Modifier
                        .size(240.dp)
                        .testTag("call_video_preview"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = NavySurface)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        UserAvatar(
                            photoUrl = contactPhoto,
                            displayName = contactName,
                            size = 120.dp
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.6f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("HD 1080p", color = PrimaryBlueLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                UserAvatar(
                    photoUrl = contactPhoto,
                    displayName = contactName,
                    size = 140.dp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = contactName,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isConnected) {
                    val mins = callDurationSeconds / 60
                    val secs = callDurationSeconds % 60
                    String.format("%02d:%02d", mins, secs)
                } else {
                    if (isCaller) "Ringing..." else "Connecting..."
                },
                color = if (isConnected) OnlineGreen else PrimaryBlueLight,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.weight(1f))

            // Control Actions Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mute Mic
                IconButton(
                    onClick = { isMuted = !isMuted },
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(if (isMuted) Color.White else NavySurface)
                        .testTag("call_mute_btn")
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Mute",
                        tint = if (isMuted) NavyDark else Color.White
                    )
                }

                // Speakerphone
                IconButton(
                    onClick = { isSpeakerOn = !isSpeakerOn },
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(if (isSpeakerOn) PrimaryBlue else NavySurface)
                        .testTag("call_speaker_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Speaker",
                        tint = Color.White
                    )
                }

                // Video toggle
                IconButton(
                    onClick = { isVideoEnabled = !isVideoEnabled },
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(if (isVideoEnabled) PrimaryBlue else NavySurface)
                        .testTag("call_video_toggle_btn")
                ) {
                    Icon(
                        imageVector = if (isVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                        contentDescription = "Video",
                        tint = Color.White
                    )
                }

                // End Call Button
                IconButton(
                    onClick = {
                        scope.launch {
                            callRepo.updateCallStatus(callId, "ended", callDurationSeconds)
                            val stopIntent = Intent(context, NexChatCallService::class.java).apply {
                                action = NexChatCallService.ACTION_END_CALL
                            }
                            context.startService(stopIntent)
                            onCallEnded()
                        }
                    },
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(ErrorRed)
                        .testTag("call_end_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "End Call",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}
