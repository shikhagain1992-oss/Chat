package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.data.remote.FirebaseManager
import com.example.data.repository.CallRepository
import com.example.service.NexChatCallService
import com.example.ui.components.UserAvatar
import com.example.ui.theme.AliceBlue
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.IceBlue
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftBlueBorder
import com.example.ui.theme.TextDark
import com.example.ui.theme.TextDarkMuted
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
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
    val callState by callRepo.listenToCall(callId).collectAsState(initial = null)

    var isMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(initialIsVideo) }
    var isVideoEnabled by remember { mutableStateOf(initialIsVideo) }
    var callDurationSeconds by remember { mutableIntStateOf(0) }
    var webRtcConnectionState by remember { mutableStateOf("initializing") }
    var hasRemoteMedia by remember { mutableStateOf(false) }
    var permissionsGranted by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var webRtcStarted by remember { mutableStateOf(false) }
    var remoteDescSet by remember { mutableStateOf(false) }

    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    // Request RECORD_AUDIO and CAMERA permissions before starting media stream
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val audioOk = result[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val cameraOk = !initialIsVideo || result[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        permissionsGranted = audioOk && cameraOk
        if (!audioOk) {
            Toast.makeText(context, "Microphone permission is required for calls", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        val hasAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val hasCam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (hasAudio && (!initialIsVideo || hasCam)) {
            permissionsGranted = true
        } else {
            val perms = if (initialIsVideo) {
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
            } else {
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
            }
            permissionLauncher.launch(perms)
        }
    }

    // Configure hardware audio routing for voice/video call
    fun updateAudioRouting(speaker: Boolean) {
        try {
            audioManager?.let { am ->
                am.mode = AudioManager.MODE_IN_COMMUNICATION
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = speaker
            }
        } catch (e: Exception) {
            Log.w("CallScreen", "Audio routing update failed: ${e.message}")
        }
    }

    LaunchedEffect(isSpeakerOn) {
        updateAudioRouting(isSpeakerOn)
    }

    // Cleanup WebRTC and AudioManager when CallScreen leaves composition
    DisposableEffect(callId) {
        updateAudioRouting(initialIsVideo)
        var candidateListener: ListenerRegistration? = null

        val remoteSubcollection = if (isCaller) "calleeCandidates" else "callerCandidates"
        if (callId.isNotBlank()) {
            candidateListener = FirebaseManager.firestore
                .collection("calls")
                .document(callId)
                .collection(remoteSubcollection)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener
                    for (change in snapshot.documentChanges) {
                        if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                            val data = change.document.data
                            val candidateStr = data["candidate"] as? String ?: continue
                            val sdpMid = data["sdpMid"] as? String ?: ""
                            val sdpMLineIndex = (data["sdpMLineIndex"] as? Number)?.toInt() ?: 0
                            val json = JSONObject().apply {
                                put("candidate", candidateStr)
                                put("sdpMid", sdpMid)
                                put("sdpMLineIndex", sdpMLineIndex)
                            }.toString()
                            webViewRef?.post {
                                webViewRef?.evaluateJavascript("window.addRemoteCandidate($json);", null)
                            }
                        }
                    }
                }
        }

        onDispose {
            candidateListener?.remove()
            try {
                webViewRef?.evaluateJavascript("window.cleanupCall && window.cleanupCall();", null)
                webViewRef?.destroy()
            } catch (_: Exception) {}
            try {
                audioManager?.let { am ->
                    @Suppress("DEPRECATION")
                    am.isSpeakerphoneOn = false
                    am.mode = AudioManager.MODE_NORMAL
                }
            } catch (_: Exception) {}
        }
    }

    // 45-second ringing timeout if receiver does not answer
    LaunchedEffect(callState?.status, isCaller) {
        if (isCaller && (callState?.status == "ringing" || callState?.status == "initiating")) {
            delay(45_000L)
            val currentStatus = callState?.status
            if (currentStatus == "ringing" || currentStatus == "initiating") {
                callRepo.updateCallStatus(callId, "timeout", 0, "No answer within 45 seconds")
            }
        }
    }

    // Call duration timer when call is accepted or connected
    LaunchedEffect(callState?.status) {
        val status = callState?.status ?: ""
        if (status == "accepted" || status == "connecting" || status == "connected") {
            while (true) {
                delay(1000L)
                callDurationSeconds++
            }
        } else if (status in listOf("ended", "rejected", "cancelled", "missed", "timeout", "failed")) {
            val stopIntent = Intent(context, NexChatCallService::class.java).apply {
                action = NexChatCallService.ACTION_END_CALL
                putExtra(NexChatCallService.EXTRA_CALL_ID, callId)
            }
            context.startService(stopIntent)
            delay(600L)
            onCallEnded()
        }
    }

    // Start WebRTC offer (Caller) or answer (Callee) once permissions & WebView are ready
    LaunchedEffect(permissionsGranted, webViewRef, callState?.status, callState?.offer, callState?.answer) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (!permissionsGranted) return@LaunchedEffect
        val call = callState ?: return@LaunchedEffect

        if (isCaller) {
            if (!webRtcStarted) {
                webRtcStarted = true
                wv.evaluateJavascript("window.startCallerWebRtc(${initialIsVideo});", null)
            }
            val answerMap = call.answer
            if (answerMap != null && !remoteDescSet) {
                val type = answerMap["type"] as? String ?: "answer"
                val sdp = answerMap["sdp"] as? String ?: ""
                if (sdp.isNotBlank()) {
                    remoteDescSet = true
                    val json = JSONObject().apply {
                        put("type", type)
                        put("sdp", sdp)
                    }.toString()
                    wv.evaluateJavascript("window.onRemoteAnswer($json);", null)
                }
            }
        } else {
            // Receiver: once call status is accepted (or user tapped Answer) and offer is present
            val offerMap = call.offer
            if ((call.status == "accepted" || call.status == "connecting" || call.status == "connected") && offerMap != null && !webRtcStarted) {
                val type = offerMap["type"] as? String ?: "offer"
                val sdp = offerMap["sdp"] as? String ?: ""
                if (sdp.isNotBlank()) {
                    webRtcStarted = true
                    remoteDescSet = true
                    val json = JSONObject().apply {
                        put("type", type)
                        put("sdp", sdp)
                    }.toString()
                    wv.evaluateJavascript("window.startCalleeWebRtc(${initialIsVideo}, $json);", null)
                }
            }
        }
    }

    val contactName = if (isCaller) (callState?.receiverName ?: "Calling...") else (callState?.callerName ?: "Caller")
    val contactPhoto = if (isCaller) callState?.receiverPhotoUrl else callState?.callerPhotoUrl
    val status = callState?.status ?: "ringing"

    val formattedDuration = remember(callDurationSeconds) {
        val mins = callDurationSeconds / 60
        val secs = callDurationSeconds % 60
        String.format("%02d:%02d", mins, secs)
    }

    val statusSubtitle = when (status) {
        "initiating" -> "Initiating call..."
        "ringing" -> if (isCaller) "Ringing..." else "Incoming ${if (initialIsVideo) "Video" else "Audio"} Call"
        "accepted", "connecting" -> if (hasRemoteMedia || webRtcConnectionState == "connected") formattedDuration else "Connecting media... ($formattedDuration)"
        "connected" -> formattedDuration
        "rejected" -> "Call Declined"
        "cancelled" -> "Call Cancelled"
        "timeout", "missed" -> "No Answer"
        "failed" -> "Call Failed"
        else -> "Call Ended"
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("call_screen"),
        color = if (initialIsVideo) Color(0xFF0A192F) else AliceBlue
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Real WebRTC Audio/Video Engine View (renders remote video full screen & local PIP video)
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("webrtc_media_view"),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            allowContentAccess = true
                            cacheMode = WebSettings.LOAD_NO_CACHE
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onPermissionRequest(request: PermissionRequest?) {
                                request?.grant(request.resources)
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                webViewRef = this@apply
                            }
                        }

                        addJavascriptInterface(
                            object {
                                @JavascriptInterface
                                fun onLocalOffer(type: String, sdp: String) {
                                    if (callId.isBlank()) return
                                    FirebaseManager.firestore.collection("calls").document(callId).set(
                                        mapOf(
                                            "offer" to mapOf("type" to type, "sdp" to sdp),
                                            "status" to "ringing"
                                        ),
                                        SetOptions.merge()
                                    )
                                }

                                @JavascriptInterface
                                fun onLocalAnswer(type: String, sdp: String) {
                                    if (callId.isBlank()) return
                                    FirebaseManager.firestore.collection("calls").document(callId).set(
                                        mapOf(
                                            "answer" to mapOf("type" to type, "sdp" to sdp),
                                            "status" to "connected"
                                        ),
                                        SetOptions.merge()
                                    )
                                }

                                @JavascriptInterface
                                fun onLocalIceCandidate(candidateJson: String) {
                                    if (callId.isBlank()) return
                                    try {
                                        val obj = JSONObject(candidateJson)
                                        val subcollection = if (isCaller) "callerCandidates" else "calleeCandidates"
                                        val candMap = mapOf(
                                            "candidate" to obj.optString("candidate", ""),
                                            "sdpMid" to obj.optString("sdpMid", ""),
                                            "sdpMLineIndex" to obj.optInt("sdpMLineIndex", 0)
                                        )
                                        FirebaseManager.firestore.collection("calls")
                                            .document(callId)
                                            .collection(subcollection)
                                            .add(candMap)
                                    } catch (e: Exception) {
                                        Log.w("CallScreen", "ICE candidate parse error: ${e.message}")
                                    }
                                }

                                @JavascriptInterface
                                fun onConnectionStateChange(state: String) {
                                    webRtcConnectionState = state
                                    if (state == "connected") {
                                        hasRemoteMedia = true
                                        scope.launch {
                                            callRepo.updateCallStatus(callId, "connected", callDurationSeconds)
                                        }
                                    } else if (state == "failed") {
                                        scope.launch {
                                            callRepo.updateCallStatus(callId, "failed", callDurationSeconds, "WebRTC ICE connection failed")
                                        }
                                    }
                                }

                                @JavascriptInterface
                                fun onRemoteTrackReceived() {
                                    hasRemoteMedia = true
                                }
                            },
                            "AndroidBridge"
                        )

                        loadDataWithBaseURL(
                            "https://nexchat-pro-8e480.web.app",
                            buildWebRtcHtml(initialIsVideo),
                            "text/html",
                            "UTF-8",
                            null
                        )
                    }
                }
            )

            // Foreground Call Overlay UI
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Caller/Receiver Info Card
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (initialIsVideo && hasRemoteMedia) Color.Black.copy(alpha = 0.45f)
                            else Color.Transparent
                        )
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = if (initialIsVideo) "NexChat Pro Video Call" else "NexChat Pro Voice Call",
                        color = if (initialIsVideo) Color(0xFF93C5FD) else PrimaryBlue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = contactName,
                        color = if (initialIsVideo) Color.White else TextDark,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = statusSubtitle,
                        color = if (status == "connected" || status == "accepted") OnlineGreen
                        else if (initialIsVideo) Color.White.copy(alpha = 0.8f)
                        else TextDarkMuted,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag("call_status_text")
                    )
                }

                // Center Avatar when audio call or while waiting for remote video stream
                if (!initialIsVideo || !hasRemoteMedia) {
                    Card(
                        modifier = Modifier
                            .size(210.dp)
                            .border(2.dp, SoftBlueBorder, RoundedCornerShape(105.dp))
                            .testTag("call_video_preview"),
                        shape = RoundedCornerShape(105.dp),
                        colors = CardDefaults.cardColors(containerColor = PureWhite),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            UserAvatar(
                                photoUrl = contactPhoto,
                                displayName = contactName,
                                size = 150.dp
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Bottom Call Action Controls
                if (!isCaller && status == "ringing") {
                    // Incoming Call Answer / Decline Controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = {
                                    val stopIntent = Intent(context, NexChatCallService::class.java).apply {
                                        action = NexChatCallService.ACTION_END_CALL
                                        putExtra(NexChatCallService.EXTRA_CALL_ID, callId)
                                    }
                                    context.startService(stopIntent)
                                    scope.launch {
                                        callRepo.updateCallStatus(callId, "rejected")
                                        onCallEnded()
                                    }
                                },
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(ErrorRed)
                                    .testTag("decline_call_button")
                            ) {
                                Icon(
                                    Icons.Default.CallEnd,
                                    contentDescription = "Decline Call",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Decline",
                                color = if (initialIsVideo) Color.White else TextDark,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = {
                                    val stopIntent = Intent(context, NexChatCallService::class.java).apply {
                                        action = NexChatCallService.ACTION_ACCEPT_CALL
                                        putExtra(NexChatCallService.EXTRA_CALL_ID, callId)
                                    }
                                    context.startService(stopIntent)
                                    scope.launch {
                                        val res = callRepo.acceptCallSafely(callId)
                                        if (res.isFailure) {
                                            Toast.makeText(context, "Call is no longer active", Toast.LENGTH_SHORT).show()
                                            onCallEnded()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(OnlineGreen)
                                    .testTag("accept_call_button")
                            ) {
                                Icon(
                                    Icons.Default.Call,
                                    contentDescription = "Accept Call",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Answer",
                                color = if (initialIsVideo) Color.White else TextDark,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    // Active / Outgoing Call Controls: Mute, Speaker, Video Toggle, Camera Flip, End Call
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .border(1.dp, SoftBlueBorder, RoundedCornerShape(28.dp)),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (initialIsVideo) Color(0xCC112240) else PureWhite
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Mute Microphone
                            IconButton(
                                onClick = {
                                    isMuted = !isMuted
                                    webViewRef?.evaluateJavascript("window.setMicMuted($isMuted);", null)
                                },
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                                    .background(if (isMuted) PrimaryBlue else IceBlue)
                                    .testTag("mute_button")
                            ) {
                                Icon(
                                    imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                    contentDescription = "Toggle Mute",
                                    tint = if (isMuted) Color.White else PrimaryBlue
                                )
                            }

                            // 2. Speakerphone Toggle
                            IconButton(
                                onClick = {
                                    isSpeakerOn = !isSpeakerOn
                                    updateAudioRouting(isSpeakerOn)
                                },
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                                    .background(if (isSpeakerOn) PrimaryBlue else IceBlue)
                                    .testTag("speaker_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = "Toggle Speaker",
                                    tint = if (isSpeakerOn) Color.White else PrimaryBlue
                                )
                            }

                            // 3. Video Stream Toggle & Camera Flip (if video call)
                            if (initialIsVideo) {
                                IconButton(
                                    onClick = {
                                        isVideoEnabled = !isVideoEnabled
                                        webViewRef?.evaluateJavascript("window.setVideoEnabled($isVideoEnabled);", null)
                                    },
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(CircleShape)
                                        .background(if (!isVideoEnabled) PrimaryBlue else IceBlue)
                                        .testTag("video_toggle_button")
                                ) {
                                    Icon(
                                        imageVector = if (isVideoEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                        contentDescription = "Toggle Video",
                                        tint = if (!isVideoEnabled) Color.White else PrimaryBlue
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        webViewRef?.evaluateJavascript("window.switchCamera();", null)
                                    },
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(CircleShape)
                                        .background(IceBlue)
                                        .testTag("switch_camera_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Cameraswitch,
                                        contentDescription = "Switch Camera",
                                        tint = PrimaryBlue
                                    )
                                }
                            }

                            // 4. End Call
                            IconButton(
                                onClick = {
                                    val stopIntent = Intent(context, NexChatCallService::class.java).apply {
                                        action = NexChatCallService.ACTION_END_CALL
                                        putExtra(NexChatCallService.EXTRA_CALL_ID, callId)
                                    }
                                    context.startService(stopIntent)
                                    scope.launch {
                                        val finalStatus = if (isCaller && status == "ringing") "cancelled" else "ended"
                                        callRepo.updateCallStatus(callId, finalStatus, callDurationSeconds)
                                        onCallEnded()
                                    }
                                },
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(ErrorRed)
                                    .testTag("end_call_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CallEnd,
                                    contentDescription = "End Call",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun buildWebRtcHtml(isVideo: Boolean): String = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  html, body { width: 100%; height: 100%; background: transparent; overflow: hidden; }
  #remoteVideo {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: ${if (isVideo) "block" else "none"};
    background: #0A192F;
  }
  #localVideo {
    position: fixed;
    right: 16px;
    bottom: 120px;
    width: 110px;
    height: 155px;
    object-fit: cover;
    border-radius: 14px;
    border: 2px solid #29B6F6;
    background: #112240;
    z-index: 10;
    transform: scaleX(-1);
    display: ${if (isVideo) "block" else "none"};
  }
</style>
</head>
<body>
  <video id="remoteVideo" autoplay playsinline></video>
  <video id="localVideo" autoplay playsinline muted></video>
  <audio id="remoteAudio" autoplay></audio>
<script>
  const rtcConfig = {
    iceServers: [
      { urls: ['stun:stun.l.google.com:19302', 'stun:stun1.l.google.com:19302', 'stun:stun2.l.google.com:19302'] }
    ]
  };
  let pc = null;
  let localStream = null;
  let remoteStream = null;
  let currentFacingMode = 'user';
  let pendingCandidates = [];
  let remoteDescReady = false;

  async function initMedia(isVid) {
    if (localStream) return localStream;
    try {
      localStream = await navigator.mediaDevices.getUserMedia({
        audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true },
        video: isVid ? { facingMode: currentFacingMode, width: { ideal: 640 }, height: { ideal: 480 } } : false
      });
      const lv = document.getElementById('localVideo');
      if (lv && isVid) lv.srcObject = localStream;
    } catch (err) {
      try {
        localStream = await navigator.mediaDevices.getUserMedia({ audio: true, video: false });
      } catch (e2) {}
    }
    return localStream;
  }

  function createPeer() {
    if (pc) return pc;
    pc = new RTCPeerConnection(rtcConfig);
    remoteStream = new MediaStream();
    const rv = document.getElementById('remoteVideo');
    const ra = document.getElementById('remoteAudio');
    if (rv) rv.srcObject = remoteStream;
    if (ra) ra.srcObject = remoteStream;

    pc.ontrack = (event) => {
      if (event.streams && event.streams[0]) {
        event.streams[0].getTracks().forEach(t => remoteStream.addTrack(t));
      } else if (event.track) {
        remoteStream.addTrack(event.track);
      }
      if (window.AndroidBridge && window.AndroidBridge.onRemoteTrackReceived) {
        window.AndroidBridge.onRemoteTrackReceived();
      }
    };

    pc.onicecandidate = (event) => {
      if (event.candidate && window.AndroidBridge) {
        window.AndroidBridge.onLocalIceCandidate(JSON.stringify(event.candidate.toJSON()));
      }
    };

    pc.onconnectionstatechange = () => {
      if (window.AndroidBridge && pc) {
        window.AndroidBridge.onConnectionStateChange(pc.connectionState);
      }
    };
    return pc;
  }

  window.startCallerWebRtc = async function(isVid) {
    try {
      const stream = await initMedia(isVid);
      const peer = createPeer();
      if (stream) {
        stream.getTracks().forEach(track => peer.addTrack(track, stream));
      }
      const offer = await peer.createOffer({ offerToReceiveAudio: true, offerToReceiveVideo: isVid });
      await peer.setLocalDescription(offer);
      if (window.AndroidBridge) {
        window.AndroidBridge.onLocalOffer(offer.type, offer.sdp);
      }
    } catch (e) {
      console.error('startCallerWebRtc error', e);
    }
  };

  window.startCalleeWebRtc = async function(isVid, offerObj) {
    try {
      const stream = await initMedia(isVid);
      const peer = createPeer();
      if (stream) {
        stream.getTracks().forEach(track => peer.addTrack(track, stream));
      }
      await peer.setRemoteDescription(new RTCSessionDescription(offerObj));
      remoteDescReady = true;
      while (pendingCandidates.length > 0) {
        const c = pendingCandidates.shift();
        try { await peer.addIceCandidate(new RTCIceCandidate(c)); } catch (e) {}
      }
      const answer = await peer.createAnswer();
      await peer.setLocalDescription(answer);
      if (window.AndroidBridge) {
        window.AndroidBridge.onLocalAnswer(answer.type, answer.sdp);
      }
    } catch (e) {
      console.error('startCalleeWebRtc error', e);
    }
  };

  window.onRemoteAnswer = async function(answerObj) {
    try {
      if (!pc) return;
      await pc.setRemoteDescription(new RTCSessionDescription(answerObj));
      remoteDescReady = true;
      while (pendingCandidates.length > 0) {
        const c = pendingCandidates.shift();
        try { await pc.addIceCandidate(new RTCIceCandidate(c)); } catch (e) {}
      }
    } catch (e) {
      console.error('onRemoteAnswer error', e);
    }
  };

  window.addRemoteCandidate = async function(candObj) {
    try {
      if (!pc || !remoteDescReady) {
        pendingCandidates.push(candObj);
        return;
      }
      await pc.addIceCandidate(new RTCIceCandidate(candObj));
    } catch (e) {
      console.error('addRemoteCandidate error', e);
    }
  };

  window.setMicMuted = function(muted) {
    if (!localStream) return;
    localStream.getAudioTracks().forEach(t => { t.enabled = !muted; });
  };

  window.setVideoEnabled = function(enabled) {
    if (!localStream) return;
    localStream.getVideoTracks().forEach(t => { t.enabled = enabled; });
    const lv = document.getElementById('localVideo');
    if (lv) lv.style.display = enabled ? 'block' : 'none';
  };

  window.switchCamera = async function() {
    try {
      currentFacingMode = (currentFacingMode === 'user') ? 'environment' : 'user';
      const newStream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: currentFacingMode, width: { ideal: 640 }, height: { ideal: 480 } },
        audio: false
      });
      const newVideoTrack = newStream.getVideoTracks()[0];
      if (newVideoTrack && pc) {
        const sender = pc.getSenders().find(s => s.track && s.track.kind === 'video');
        if (sender) await sender.replaceTrack(newVideoTrack);
      }
      if (localStream) {
        localStream.getVideoTracks().forEach(t => {
          t.stop();
          localStream.removeTrack(t);
        });
        if (newVideoTrack) localStream.addTrack(newVideoTrack);
      }
      const lv = document.getElementById('localVideo');
      if (lv) {
        lv.srcObject = localStream;
        lv.style.transform = (currentFacingMode === 'user') ? 'scaleX(-1)' : 'none';
      }
    } catch (e) {
      console.error('switchCamera error', e);
    }
  };

  window.cleanupCall = function() {
    try {
      if (localStream) {
        localStream.getTracks().forEach(t => t.stop());
        localStream = null;
      }
      if (pc) {
        pc.close();
        pc = null;
      }
    } catch (e) {}
  };
</script>
</body>
</html>
""".trimIndent()
