package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.remote.CloudinaryUploader
import com.example.data.remote.FirebaseManager
import com.example.data.repository.StatusRepository
import com.example.data.repository.UserRepository
import com.example.ui.components.UserAvatar
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.NavyDark
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueLight
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatusViewerScreen(
    statusId: String,
    onNavigateBack: () -> Unit
) {
    val statusRepo = remember { StatusRepository() }
    val currentUserId = FirebaseManager.currentUserId ?: ""
    val allStatuses by statusRepo.getActiveStatusesFlow().collectAsState(initial = emptyList())
    val story = allStatuses.firstOrNull { it.id == statusId }

    val progress = remember { Animatable(0f) }

    LaunchedEffect(statusId) {
        statusRepo.markStatusViewed(statusId, currentUserId)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 5000, easing = LinearEasing)
        )
        onNavigateBack()
    }

    if (story == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = PrimaryBlueLight)
        }
        return
    }

    val isOwner = story.userId == currentUserId

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("status_viewer_screen")
    ) {
        // Media Content
        AsyncImage(
            model = story.mediaUrl,
            contentDescription = "Status Story",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )

        // Overlay Gradient & Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            // Story Progress Bar
            LinearProgressIndicator(
                progress = { progress.value },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.3f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // User Info Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                UserAvatar(photoUrl = story.userPhotoUrl, displayName = story.userName, size = 42.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(story.userName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(story.createdAt))
                    Text("Today at $timeStr", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Caption overlay
            if (story.caption.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(14.dp)
                ) {
                    Text(
                        text = story.caption,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // View count for owner
            if (isOwner) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("${story.viewers.size} views", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusUploadScreen(
    onNavigateBack: () -> Unit,
    onStatusUploaded: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val statusRepo = remember { StatusRepository() }
    val userRepo = remember { UserRepository() }
    val currentUserId = FirebaseManager.currentUserId ?: ""
    val currentUser by userRepo.getUserProfile(currentUserId).collectAsState(initial = null)

    var selectedMediaUri by remember { mutableStateOf<Uri?>(null) }
    var captionText by remember { mutableStateOf("") }
    var isUploading by remember { mutableStateOf(false) }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        selectedMediaUri = uri
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("status_upload_screen"),
        topBar = {
            TopAppBar(
                title = { Text("Post Status", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Media Preview or Picker Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable {
                        mediaPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    }
                    .testTag("status_media_picker_box"),
                contentAlignment = Alignment.Center
            ) {
                if (selectedMediaUri != null) {
                    AsyncImage(
                        model = selectedMediaUri,
                        contentDescription = "Selected status image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Tap to choose photo or video",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = captionText,
                onValueChange = { captionText = it },
                label = { Text("Add a caption...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("status_caption_input"),
                shape = RoundedCornerShape(12.dp),
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    val uri = selectedMediaUri
                    if (uri == null) {
                        Toast.makeText(context, "Please select an image or video", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (currentUser == null) return@Button

                    isUploading = true
                    scope.launch {
                        val mimeType = context.contentResolver.getType(uri) ?: "image/*"
                        val mediaType = if (mimeType.startsWith("video")) "video" else "image"
                        val uploadResult = CloudinaryUploader.uploadUri(context, uri, mimeType)

                        if (uploadResult.isSuccess) {
                            val mediaUrl = uploadResult.getOrNull() ?: ""
                            statusRepo.uploadStatus(
                                user = currentUser!!,
                                mediaUrl = mediaUrl,
                                mediaType = mediaType,
                                caption = captionText.trim()
                            )
                            Toast.makeText(context, "Status uploaded!", Toast.LENGTH_SHORT).show()
                            onStatusUploaded()
                        } else {
                            Toast.makeText(context, "Failed to upload to Cloudinary", Toast.LENGTH_SHORT).show()
                        }
                        isUploading = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("submit_status_btn"),
                shape = RoundedCornerShape(12.dp),
                enabled = !isUploading && selectedMediaUri != null
            ) {
                if (isUploading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text("Share to Status", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}
