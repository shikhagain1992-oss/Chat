package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.remote.CloudinaryUploader
import com.example.data.remote.FirebaseManager
import com.example.data.repository.UserRepository
import com.example.ui.components.UserAvatar
import com.example.ui.theme.LightBlueTint
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueDark
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftBlueBorder
import com.example.ui.theme.TextDark
import kotlinx.coroutines.launch

// Preset ready-to-use avatar CDN links
private val PRESET_AVATARS = listOf(
    "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=200&auto=format&fit=crop&q=80",
    "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=200&auto=format&fit=crop&q=80",
    "https://images.unsplash.com/photo-1570295999919-56ceb5ecca61?w=200&auto=format&fit=crop&q=80",
    "https://images.unsplash.com/photo-1580489944761-15a19d654956?w=200&auto=format&fit=crop&q=80",
    "https://images.unsplash.com/photo-1628157582853-a796fa650a6a?w=200&auto=format&fit=crop&q=80",
    "https://images.unsplash.com/photo-1633332755192-727a05c4013d?w=200&auto=format&fit=crop&q=80"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userRepo = remember { UserRepository() }
    val currentUserId = FirebaseManager.currentUserId ?: ""

    val currentUser by userRepo.getUserProfile(currentUserId).collectAsState(initial = null)

    var displayName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var about by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var photoUrl by remember { mutableStateOf("") }
    var isUploadingPhoto by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var inputUrl by remember { mutableStateOf("") }

    LaunchedEffect(currentUser) {
        currentUser?.let {
            if (displayName.isEmpty()) displayName = it.displayName
            if (username.isEmpty()) username = it.username
            if (about.isEmpty()) about = it.about
            if (phoneNumber.isEmpty()) phoneNumber = it.phoneNumber
            if (photoUrl.isEmpty()) photoUrl = it.photoUrl
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null && currentUserId.isNotEmpty()) {
            isUploadingPhoto = true
            scope.launch {
                // 1. Immediately create compressed data URI for instant local & remote availability
                val immediateDataUrl = CloudinaryUploader.uriToCompressedDataUrl(context, uri)
                if (!immediateDataUrl.isNullOrEmpty()) {
                    photoUrl = immediateDataUrl
                    userRepo.updatePhotoUrl(currentUserId, immediateDataUrl)
                    Toast.makeText(context, "Profile photo updated!", Toast.LENGTH_SHORT).show()
                }

                // 2. Also upload to Cloudinary for CDN link
                val uploadRes = CloudinaryUploader.uploadUri(context, uri, "image/jpeg")
                if (uploadRes.isSuccess) {
                    val cdnUrl = uploadRes.getOrNull()
                    if (!cdnUrl.isNullOrEmpty()) {
                        photoUrl = cdnUrl
                        userRepo.updatePhotoUrl(currentUserId, cdnUrl)
                    }
                }
                isUploadingPhoto = false
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .testTag("profile_edit_screen"),
        topBar = {
            TopAppBar(
                title = { Text("Edit Profile", fontWeight = FontWeight.Bold, color = TextDark) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PrimaryBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PureWhite)
            )
        },
        containerColor = PureWhite
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Main Avatar with border
            Box(
                modifier = Modifier
                    .size(116.dp)
                    .clip(CircleShape)
                    .border(3.dp, SoftBlueBorder, CircleShape)
                    .clickable {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                    .testTag("edit_avatar_btn"),
                contentAlignment = Alignment.Center
            ) {
                UserAvatar(
                    photoUrl = photoUrl,
                    displayName = displayName.ifBlank { "User" },
                    size = 116.dp
                )

                if (isUploadingPhoto) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .clip(RoundedCornerShape(bottomStart = 58.dp, bottomEnd = 58.dp))
                                .background(PrimaryBlue.copy(alpha = 0.85f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = "Change photo",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons for Photo
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp), tint = PrimaryBlue)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Gallery / Camera", color = PrimaryBlue, fontWeight = FontWeight.SemiBold)
                }

                Text("•", color = SoftBlueBorder, modifier = Modifier.padding(horizontal = 4.dp))

                TextButton(onClick = { showUrlDialog = true }) {
                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp), tint = PrimaryBlue)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Paste URL", color = PrimaryBlue, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Preset Avatars Row (Instant 1-tap select)
            Text(
                text = "Or choose a preset avatar:",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(PRESET_AVATARS) { presetUrl ->
                    val isSelected = photoUrl == presetUrl
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .border(
                                width = if (isSelected) 3.dp else 1.5.dp,
                                color = if (isSelected) PrimaryBlue else SoftBlueBorder,
                                shape = CircleShape
                            )
                            .clickable {
                                photoUrl = presetUrl
                                scope.launch {
                                    userRepo.updatePhotoUrl(currentUserId, presetUrl)
                                    Toast.makeText(context, "Avatar selected & saved!", Toast.LENGTH_SHORT).show()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = presetUrl,
                            contentDescription = "Preset avatar",
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Display Name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_display_name"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it.filter { c -> c.isLetterOrDigit() || c == '_' } },
                label = { Text("Username (@handle)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_username"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                label = { Text("Phone Number") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_phone"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = about,
                onValueChange = { about = it },
                label = { Text("About / Bio") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("edit_about"),
                shape = RoundedCornerShape(12.dp),
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(26.dp))

            Button(
                onClick = {
                    if (currentUserId.isEmpty()) return@Button
                    isSaving = true
                    scope.launch {
                        val baseProfile = currentUser ?: com.example.data.model.UserProfile(uid = currentUserId)
                        val updated = baseProfile.copy(
                            displayName = displayName.trim(),
                            username = username.trim().lowercase(),
                            about = about.trim(),
                            phoneNumber = phoneNumber.trim(),
                            photoUrl = photoUrl
                        )
                        val res = userRepo.saveUserProfile(updated)
                        if (res.isSuccess) {
                            Toast.makeText(context, "Profile saved successfully!", Toast.LENGTH_SHORT).show()
                            onNavigateBack()
                        } else {
                            Toast.makeText(context, "Error updating profile", Toast.LENGTH_SHORT).show()
                        }
                        isSaving = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_profile_btn"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text("Save Changes", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }

    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("Set Profile Picture URL", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Paste an image URL directly:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = { inputUrl = it },
                        label = { Text("Image URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val url = inputUrl.trim()
                        if (url.isNotEmpty()) {
                            photoUrl = url
                            scope.launch {
                                userRepo.updatePhotoUrl(currentUserId, url)
                                Toast.makeText(context, "Profile picture set and saved!", Toast.LENGTH_SHORT).show()
                            }
                        }
                        showUrlDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Set Picture")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
