package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.remote.FirebaseManager
import com.example.ui.theme.IceBlue
import com.example.ui.theme.LightBlueTint
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.PrimaryBlueDark
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftBlueBorder
import com.example.ui.theme.TextDark
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onNavigateToDashboard: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val scale = remember { Animatable(0.75f) }

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing)
        )
        delay(800)

        val currentUser = FirebaseManager.currentUser
        if (currentUser != null && currentUser.uid.isNotEmpty()) {
            onNavigateToDashboard()
        } else {
            onNavigateToLogin()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(PureWhite, IceBlue, LightBlueTint)
                )
            )
            .testTag("splash_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .scale(scale.value)
                    .clip(CircleShape)
                    .background(PureWhite)
                    .border(3.dp, SoftBlueBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = "NexChat Pro Logo",
                    modifier = Modifier.size(90.dp)
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = "NexChat Pro",
                color = TextDark,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )

            Text(
                text = "Fast • Secure • Real-time",
                color = PrimaryBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(36.dp))

            CircularProgressIndicator(
                modifier = Modifier
                    .size(30.dp)
                    .testTag("splash_loading"),
                color = PrimaryBlue,
                strokeWidth = 3.dp
            )
        }
    }
}
