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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import coil.compose.AsyncImage
import com.example.data.remote.CloudinaryUploader
import com.example.ui.theme.AliceBlue
import com.example.ui.theme.SoftBlueBorder
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserProfile
import com.example.data.remote.FirebaseManager
import com.example.data.repository.UserRepository
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueLight
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun LoginScreen(
    onNavigateToRegister: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showForgotPasswordDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .testTag("login_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Welcome to NexChat Pro",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Log in to connect with your contacts",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            errorMessage?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("login_error_text")
                )
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; errorMessage = null },
                label = { Text("Email address") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = "Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("email_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; errorMessage = null },
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Password") },
                trailingIcon = {
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        modifier = Modifier.testTag("password_visibility_toggle")
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle password visibility"
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("password_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { showForgotPasswordDialog = true },
                    modifier = Modifier.testTag("forgot_password_button")
                ) {
                    Text("Forgot Password?", color = PrimaryBlue)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        errorMessage = "Please enter both email and password"
                        return@Button
                    }
                    isLoading = true
                    errorMessage = null
                    scope.launch {
                        try {
                            val result = FirebaseManager.auth.signInWithEmailAndPassword(email.trim(), password).await()
                            if (result.user != null) {
                                UserRepository().setUserOnline(true)
                                onLoginSuccess()
                            } else {
                                errorMessage = "Authentication failed. Please check credentials."
                            }
                        } catch (e: Exception) {
                            errorMessage = e.localizedMessage ?: "Login failed. Please check your credentials."
                        } finally {
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("login_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Text("Log In", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Don't have an account?", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Sign Up",
                    color = PrimaryBlue,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { onNavigateToRegister() }
                        .testTag("go_to_register")
                )
            }
        }
    }

    if (showForgotPasswordDialog) {
        var resetEmail by remember { mutableStateOf(email) }
        var resetStatus by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showForgotPasswordDialog = false },
            title = { Text("Reset Password") },
            text = {
                Column {
                    Text("Enter your email address to receive password reset link:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = { resetEmail = it },
                        label = { Text("Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    resetStatus?.let {
                        Text(it, color = PrimaryBlue, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (resetEmail.isNotBlank()) {
                        FirebaseManager.auth.sendPasswordResetEmail(resetEmail.trim())
                            .addOnSuccessListener {
                                Toast.makeText(context, "Reset email sent successfully!", Toast.LENGTH_LONG).show()
                                showForgotPasswordDialog = false
                            }
                            .addOnFailureListener {
                                resetStatus = it.localizedMessage ?: "Failed to send reset email"
                            }
                    }
                }) {
                    Text("Send Reset Link")
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotPasswordDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun RegisterScreen(
    onNavigateToLogin: () -> Unit,
    onRegisterSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        selectedPhotoUri = uri
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .testTag("register_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Create Account",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Join NexChat Pro community",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            // Profile Photo Picker Section
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clickable {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                    .testTag("register_photo_picker"),
                contentAlignment = Alignment.Center
            ) {
                if (selectedPhotoUri != null) {
                    AsyncImage(
                        model = selectedPhotoUri,
                        contentDescription = "Profile Photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(90.dp)
                            .clip(CircleShape)
                            .border(2.5.dp, PrimaryBlue, CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .clip(CircleShape)
                            .background(AliceBlue)
                            .border(2.dp, SoftBlueBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Select Photo",
                            tint = PrimaryBlue,
                            modifier = Modifier.size(46.dp)
                        )
                    }
                }

                // Camera badge icon
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(PrimaryBlue)
                        .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Choose Photo",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Text(
                text = if (selectedPhotoUri != null) "Photo selected (tap to change)" else "Add Profile Photo",
                fontSize = 12.sp,
                color = if (selectedPhotoUri != null) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
            )

            errorMessage?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .testTag("register_error_text")
                )
            }

            OutlinedTextField(
                value = displayName,
                onValueChange = { displayName = it },
                label = { Text("Display Name *") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = "Name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("display_name_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it.filter { char -> char.isLetterOrDigit() || char == '_' } },
                label = { Text("Username (@handle) *") },
                leadingIcon = { Text("@", modifier = Modifier.padding(start = 14.dp), fontWeight = FontWeight.Bold) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("username_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email Address *") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = "Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("register_email_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                label = { Text("Mobile Phone Number * (Mandatory)") },
                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = "Phone") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("phone_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password (min 6 characters) *") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Password") },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "Toggle password visibility"
                        )
                    }
                },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("register_password_input"),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank() || displayName.isBlank() || username.isBlank()) {
                        errorMessage = "Please fill in all required fields"
                        return@Button
                    }
                    if (phoneNumber.trim().length < 8) {
                        errorMessage = "Mobile phone number is mandatory (min 8 digits)"
                        return@Button
                    }
                    if (password.length < 6) {
                        errorMessage = "Password must be at least 6 characters"
                        return@Button
                    }

                    isLoading = true
                    errorMessage = null
                    scope.launch {
                        try {
                            var uploadedPhotoUrl = ""
                            if (selectedPhotoUri != null) {
                                val uploadRes = CloudinaryUploader.uploadUri(context, selectedPhotoUri!!, "image/jpeg")
                                if (uploadRes.isSuccess) {
                                    uploadedPhotoUrl = uploadRes.getOrNull() ?: ""
                                }
                            }

                            val authResult = FirebaseManager.auth.createUserWithEmailAndPassword(email.trim(), password).await()
                            val user = authResult.user
                            if (user != null) {
                                val profile = UserProfile(
                                    uid = user.uid,
                                    username = username.trim().lowercase(),
                                    displayName = displayName.trim(),
                                    email = email.trim(),
                                    phoneNumber = phoneNumber.trim(),
                                    photoUrl = uploadedPhotoUrl,
                                    about = "Hey there! I am using NexChat Pro.",
                                    isOnline = true,
                                    lastSeen = System.currentTimeMillis(),
                                    createdAt = System.currentTimeMillis()
                                )
                                UserRepository().saveUserProfile(profile)
                                onRegisterSuccess()
                            }
                        } catch (e: Exception) {
                            errorMessage = e.localizedMessage ?: "Registration failed. Please try again."
                        } finally {
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("register_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Text("Sign Up", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Already have an account?", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Log In",
                    color = PrimaryBlue,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { onNavigateToLogin() }
                        .testTag("go_to_login")
                )
            }
        }
    }
}
