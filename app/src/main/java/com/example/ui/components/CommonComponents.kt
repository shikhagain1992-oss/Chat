package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.theme.DeliveredGrey
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SeenDoubleBlue

@Composable
fun UserAvatar(
    photoUrl: String?,
    displayName: String,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier,
    isOnline: Boolean = false,
    showOnlineIndicator: Boolean = false
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = "Avatar of $displayName",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .testTag("avatar_image")
            )
        } else {
            val initial = displayName.trim().firstOrNull()?.uppercase() ?: "N"
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(PrimaryBlue)
                    .testTag("avatar_placeholder"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.42f).sp
                )
            }
        }

        if (showOnlineIndicator && isOnline) {
            val indicatorSize = (size.value * 0.28f).coerceAtLeast(10f).dp
            Box(
                modifier = Modifier
                    .size(indicatorSize)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(OnlineGreen)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .testTag("online_indicator")
            )
        }
    }
}

@Composable
fun MessageStatusIcon(
    status: String,
    modifier: Modifier = Modifier
) {
    when (status) {
        "seen" -> {
            Icon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = "Seen",
                tint = SeenDoubleBlue,
                modifier = modifier.size(16.dp).testTag("status_seen")
            )
        }
        "delivered" -> {
            Icon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = "Delivered",
                tint = DeliveredGrey,
                modifier = modifier.size(16.dp).testTag("status_delivered")
            )
        }
        else -> {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Sent",
                tint = DeliveredGrey,
                modifier = modifier.size(14.dp).testTag("status_sent")
            )
        }
    }
}
