package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.navigation.Screen
import com.example.ui.screens.CallScreen
import com.example.ui.screens.ChatDetailScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.GroupCreateScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.ProfileEditScreen
import com.example.ui.screens.RegisterScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.screens.StatusUploadScreen
import com.example.ui.screens.StatusViewerScreen
import com.example.ui.screens.UserSearchScreen
import com.example.ui.theme.NexChatTheme

import android.content.Intent
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {

    private val incomingCallData = mutableStateOf<Triple<String, Boolean, Boolean>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Turn screen on and show over lockscreen for calls
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        val prefs = getSharedPreferences("nexchat_prefs", MODE_PRIVATE)
        com.example.ui.theme.ThemeState.isDarkMode.value = prefs.getBoolean("dark_mode", false)

        checkCallIntent(intent)

        setContent {
            NexChatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NexChatProApp(
                        incomingCall = incomingCallData.value,
                        onCallHandled = { incomingCallData.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkCallIntent(intent)
    }

    private fun checkCallIntent(intent: Intent?) {
        val callId = intent?.getStringExtra("callId")
        if (!callId.isNullOrBlank()) {
            val isVideo = intent.getBooleanExtra("isVideo", false)
            val acceptFromNotif = intent.getBooleanExtra("acceptFromNotification", false)
            if (acceptFromNotif) {
                val stopIntent = Intent(this, com.example.service.NexChatCallService::class.java).apply {
                    action = com.example.service.NexChatCallService.ACTION_ACCEPT_CALL
                    putExtra(com.example.service.NexChatCallService.EXTRA_CALL_ID, callId)
                }
                startService(stopIntent)
            }
            incomingCallData.value = Triple(callId, false, isVideo)
        }
    }
}

@Composable
fun NexChatProApp(
    incomingCall: Triple<String, Boolean, Boolean>? = null,
    onCallHandled: () -> Unit = {}
) {
    val navController = rememberNavController()

    LaunchedEffect(incomingCall) {
        incomingCall?.let { (callId, isCaller, isVideo) ->
            navController.navigate(Screen.CallScreen.createRoute(callId, isCaller, isVideo))
            onCallHandled()
        }
    }

    // Request permissions gracefully on app startup
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        // Splash Screen
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToDashboard = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        // Login Screen
        composable(Screen.Login.route) {
            LoginScreen(
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route)
                },
                onLoginSuccess = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        // Register Screen
        composable(Screen.Register.route) {
            RegisterScreen(
                onNavigateToLogin = {
                    navController.popBackStack()
                },
                onRegisterSuccess = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        // Dashboard Screen (Chats, Status, Calls, Settings)
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToChatDetail = { convId, otherId, isGroup ->
                    navController.navigate(Screen.ChatDetail.createRoute(convId, otherId, isGroup))
                },
                onNavigateToUserSearch = {
                    navController.navigate(Screen.UserSearch.route)
                },
                onNavigateToGroupCreate = {
                    navController.navigate(Screen.GroupCreate.route)
                },
                onNavigateToStatusUpload = {
                    navController.navigate(Screen.StatusUpload.route)
                },
                onNavigateToStatusViewer = { statusId ->
                    navController.navigate(Screen.StatusViewer.createRoute(statusId))
                },
                onNavigateToCallScreen = { callId, isCaller, isVideo ->
                    navController.navigate(Screen.CallScreen.createRoute(callId, isCaller, isVideo))
                },
                onNavigateToProfileEdit = {
                    navController.navigate(Screen.ProfileEdit.route)
                },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                    }
                }
            )
        }

        // Chat Detail Screen
        composable(
            route = Screen.ChatDetail.route,
            arguments = listOf(
                navArgument("conversationId") { type = NavType.StringType },
                navArgument("otherUserId") { type = NavType.StringType; defaultValue = "none" },
                navArgument("isGroup") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            val convId = backStackEntry.arguments?.getString("conversationId") ?: ""
            val otherId = backStackEntry.arguments?.getString("otherUserId") ?: "none"
            val isGroup = backStackEntry.arguments?.getBoolean("isGroup") ?: false

            ChatDetailScreen(
                conversationId = convId,
                otherUserId = otherId,
                isGroup = isGroup,
                onNavigateBack = { navController.popBackStack() },
                onStartCall = { callId, isVideo ->
                    navController.navigate(Screen.CallScreen.createRoute(callId, isCaller = true, isVideo = isVideo))
                }
            )
        }

        // User Search Screen
        composable(Screen.UserSearch.route) {
            UserSearchScreen(
                onNavigateBack = { navController.popBackStack() },
                onStartConversation = { convId, otherId ->
                    navController.navigate(Screen.ChatDetail.createRoute(convId, otherId, false)) {
                        popUpTo(Screen.UserSearch.route) { inclusive = true }
                    }
                }
            )
        }

        // Group Create Screen
        composable(Screen.GroupCreate.route) {
            GroupCreateScreen(
                onNavigateBack = { navController.popBackStack() },
                onGroupCreated = { groupId ->
                    navController.navigate(Screen.ChatDetail.createRoute(groupId, "group", true)) {
                        popUpTo(Screen.GroupCreate.route) { inclusive = true }
                    }
                }
            )
        }

        // Call Screen
        composable(
            route = Screen.CallScreen.route,
            arguments = listOf(
                navArgument("callId") { type = NavType.StringType },
                navArgument("isCaller") { type = NavType.BoolType; defaultValue = false },
                navArgument("isVideo") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            val callId = backStackEntry.arguments?.getString("callId") ?: ""
            val isCaller = backStackEntry.arguments?.getBoolean("isCaller") ?: false
            val isVideo = backStackEntry.arguments?.getBoolean("isVideo") ?: false

            CallScreen(
                callId = callId,
                isCaller = isCaller,
                initialIsVideo = isVideo,
                onCallEnded = { navController.popBackStack() }
            )
        }

        // Status Viewer Screen
        composable(
            route = Screen.StatusViewer.route,
            arguments = listOf(navArgument("statusId") { type = NavType.StringType })
        ) { backStackEntry ->
            val statusId = backStackEntry.arguments?.getString("statusId") ?: ""
            StatusViewerScreen(
                statusId = statusId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Status Upload Screen
        composable(Screen.StatusUpload.route) {
            StatusUploadScreen(
                onNavigateBack = { navController.popBackStack() },
                onStatusUploaded = { navController.popBackStack() }
            )
        }

        // Profile Edit Screen
        composable(Screen.ProfileEdit.route) {
            ProfileEditScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
