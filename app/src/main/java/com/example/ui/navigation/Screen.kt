package com.example.ui.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Register : Screen("register")
    object Dashboard : Screen("dashboard")
    object UserSearch : Screen("user_search")
    object GroupCreate : Screen("group_create")
    object StatusUpload : Screen("status_upload")
    object ProfileEdit : Screen("profile_edit")

    object ChatDetail : Screen("chat_detail/{conversationId}/{otherUserId}/{isGroup}") {
        fun createRoute(conversationId: String, otherUserId: String = "none", isGroup: Boolean = false): String {
            return "chat_detail/$conversationId/$otherUserId/$isGroup"
        }
    }

    object CallScreen : Screen("call_screen/{callId}/{isCaller}/{isVideo}") {
        fun createRoute(callId: String, isCaller: Boolean, isVideo: Boolean): String {
            return "call_screen/$callId/$isCaller/$isVideo"
        }
    }

    object StatusViewer : Screen("status_viewer/{statusId}") {
        fun createRoute(statusId: String): String {
            return "status_viewer/$statusId"
        }
    }
}
