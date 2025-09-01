package com.aican.biometricattendance.presentation.screens.mark_attendance.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aican.biometricattendance.data.db.entity.AttendanceEventType
import com.aican.biometricattendance.presentation.screens.mark_attendance.AttendanceVerificationViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceStatusDialog(
    employeeId: String,
    matchPercent: Float,
    viewModel: AttendanceVerificationViewModel,
    onDismiss: () -> Unit,
) {
    val lastEventState by viewModel.lastEvent.collectAsState()
    LaunchedEffect(employeeId) { viewModel.fetchLastEvent(employeeId) }

    val lastEventType = lastEventState?.eventType
    val lastEventTime = lastEventState?.timestamp ?: 0L
    val isCheckedIn = lastEventType == AttendanceEventType.CHECK_IN
    val nextEventType =
        if (isCheckedIn) AttendanceEventType.CHECK_OUT else AttendanceEventType.CHECK_IN
    val lastEventReady by viewModel.lastEventReady.collectAsState(initial = false)

    val resultMsg = remember { mutableStateOf<String?>(null) }
    val isSaving = remember { mutableStateOf(false) }
    val hasAutoTriggered = remember(employeeId) { mutableStateOf(false) }

    LaunchedEffect(lastEventReady) {
        if (!lastEventReady || hasAutoTriggered.value) return@LaunchedEffect
        hasAutoTriggered.value = true

        isSaving.value = true
        viewModel.markAttendance(employeeId, nextEventType, matchPercent)
        val whenStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
        resultMsg.value =
            (if (nextEventType == AttendanceEventType.CHECK_IN) "Checked In" else "Checked Out") +
                    " successfully at $whenStr"
        isSaving.value = false
    }

    val employeeData by remember(employeeId) {
        derivedStateOf {
            viewModel.allRegisteredEmbeddings.value.find { it.employeeId == employeeId }
        }
    }

    Dialog(
        onDismissRequest = { /* Controlled by button */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            if (employeeData != null) {
                AnimatedContent(
                    targetState = resultMsg.value != null,
                    transitionSpec = {
                        slideInHorizontally(
                            animationSpec = tween(400, easing = EaseInOutCubic)
                        ) { it } togetherWith slideOutHorizontally(
                            animationSpec = tween(400, easing = EaseInOutCubic)
                        ) { -it }
                    },
                    label = "dialog_content"
                ) { showSuccess ->
                    if (showSuccess) {
                        SuccessStatusUI(
                            userName = employeeData!!.name,
                            employeeId = employeeData!!.employeeId,
                            matchPercent = matchPercent,
                            nextEventType = nextEventType,
                            resultMsg = resultMsg.value!!,
                            onClose = onDismiss
                        )
                    } else {
                        ProcessingStatusUI(
                            userName = employeeData!!.name,
                            employeeId = employeeData!!.employeeId,
                            matchPercent = matchPercent,
                            lastEventType = lastEventType,
                            lastEventTime = lastEventTime,
                            isSaving = isSaving.value
                        )
                    }
                }
            } else {
                LoadingCard()
            }
        }
    }
}

@Composable
private fun LoadingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(48.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    strokeWidth = 4.dp
                )
                Text(
                    "Loading employee data...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ProcessingStatusUI(
    userName: String,
    employeeId: String,
    matchPercent: Float,
    lastEventType: AttendanceEventType?,
    lastEventTime: Long,
    isSaving: Boolean,
) {
    val formattedLastTime = remember(lastEventTime) {
        if (lastEventTime == 0L) "No record for today"
        else SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(lastEventTime))
    }

    val pulseAnimation by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with biometric icon
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            )
                        ),
                        shape = CircleShape
                    )
                    .scale(if (isSaving) pulseAnimation else 1f),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Fingerprint,
                    contentDescription = "Biometric",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "Identity Verification",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Biometric authentication successful",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            // Employee Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // User Info Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        UserAvatar(userName = userName)
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                userName,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                employeeId,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        EnhancedMatchBadge(matchPercent)
                    }

                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                    // Last Activity Info
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AccessTime,
                            contentDescription = "Last activity",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                "Last Activity",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = lastEventType?.name?.replace('_', ' ')?.lowercase()
                                    ?.replaceFirstChar { it.uppercase() }
                                    ?.plus(" at $formattedLastTime")
                                    ?: "No previous record",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // Processing Status
            AnimatedVisibility(
                visible = isSaving,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Processing attendance...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun SuccessStatusUI(
    userName: String,
    employeeId: String,
    matchPercent: Float,
    nextEventType: AttendanceEventType,
    resultMsg: String,
    onClose: () -> Unit,
) {
    val scaleAnimation by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "success_scale"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Success Animation
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF4CAF50).copy(alpha = 0.2f),
                                Color(0xFF4CAF50).copy(alpha = 0.05f)
                            )
                        ),
                        shape = CircleShape
                    )
                    .scale(scaleAnimation),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Success",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                "Attendance Marked Successfully!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                resultMsg,
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF4CAF50),
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            // Employee Summary Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (nextEventType == AttendanceEventType.CHECK_IN) {
                        Color(0xFF4CAF50).copy(alpha = 0.1f)
                    } else {
                        Color(0xFFFF9800).copy(alpha = 0.1f)
                    }
                ),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(
                    1.dp,
                    if (nextEventType == AttendanceEventType.CHECK_IN) {
                        Color(0xFF4CAF50).copy(alpha = 0.3f)
                    } else {
                        Color(0xFFFF9800).copy(alpha = 0.3f)
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        UserAvatar(userName = userName)
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                userName,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                employeeId,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        EnhancedMatchBadge(matchPercent)
                    }

                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    // Action Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = if (nextEventType == AttendanceEventType.CHECK_IN) {
                                Icons.Default.Schedule
                            } else {
                                Icons.Outlined.AccessTime
                            },
                            contentDescription = "Action type",
                            tint = if (nextEventType == AttendanceEventType.CHECK_IN) {
                                Color(0xFF4CAF50)
                            } else {
                                Color(0xFFFF9800)
                            },
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = if (nextEventType == AttendanceEventType.CHECK_IN) "CHECKED IN" else "CHECKED OUT",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (nextEventType == AttendanceEventType.CHECK_IN) {
                                Color(0xFF4CAF50)
                            } else {
                                Color(0xFFFF9800)
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // Action Button
            Button(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
            ) {
                Text(
                    "Complete",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun UserAvatar(userName: String) {
    val initials = userName.split(" ").mapNotNull { it.firstOrNull()?.uppercase() }
        .take(2).joinToString("")

    Box(
        modifier = Modifier
            .size(56.dp)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                ),
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            initials,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
    }
}

@Composable
private fun EnhancedMatchBadge(matchPercent: Float) {
    val percent = matchPercent
    val (color, backgroundColor) = when {
        percent >= 80f -> Pair(Color(0xFF4CAF50), Color(0xFF4CAF50).copy(alpha = 0.15f))
        percent >= 60f -> Pair(Color(0xFFFF9800), Color(0xFFFF9800).copy(alpha = 0.15f))
        else -> Pair(Color(0xFFE53935), Color(0xFFE53935).copy(alpha = 0.15f))
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, CircleShape)
            )
            Text(
                text = "${percent.toInt()}%",
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}