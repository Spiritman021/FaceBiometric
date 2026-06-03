package com.aican.biometricattendance.presentation.screens.mark_attendance

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aican.biometricattendance.data.db.entity.AttendanceEventType
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkStatusScreen(
    email: String,
    matchPercent: String,
    employeeId: String,
    viewModel: AttendanceVerificationViewModel,
    onBack: () -> Unit,
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
    val saving = remember { mutableStateOf(false) }
    val hasAutoTriggered = remember(employeeId) { mutableStateOf(false) }
    val cooldownMs = 20_000L // avoid accidental double events


    // 🔸 AUTO-RUN: as soon as we know lastEvent, mark & leave
    LaunchedEffect(lastEventReady, lastEventType, lastEventTime, employeeId) {
        if (!lastEventReady || hasAutoTriggered.value) return@LaunchedEffect
        hasAutoTriggered.value = true

        val now = System.currentTimeMillis()
        val tooSoonSameType = (lastEventType == nextEventType) &&
                (lastEventTime != 0L) &&
                (now - lastEventTime in 0..cooldownMs)

        saving.value = true
        if (!tooSoonSameType) {
            viewModel.markAttendance(
                employeeId,
                nextEventType,
                matchPercent.toFloatOrNull() ?: 0f
            )
            val whenStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            resultMsg.value =
                (if (nextEventType == AttendanceEventType.CHECK_IN) "Check In" else "Check Out") +
                        " successful at $whenStr"
        } else {
            val whenStr =
                SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(lastEventTime))
            val pretty =
                nextEventType.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
            resultMsg.value = "Already $pretty at $whenStr"
        }
        saving.value = false

        delay(1200)
        onBack()
    }
    // ---------- UI (unchanged look; buttons hidden since it’s auto) ----------
    val formattedLastTime = remember(lastEventTime) {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(lastEventTime))
    }
    val currentTime = remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime.value = System.currentTimeMillis(); delay(1000)
        }
    }

    BackHandler {
        onBack()
    }
    val userName =
        remember(email) { email.substringBefore("@").replaceFirstChar { it.uppercaseChar() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Attendance Confirmation", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))

            // User Info card (unchanged)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C2E)),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xFFCBFF68), RoundedCornerShape(50)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            userName.firstOrNull()?.uppercase() ?: "",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(userName, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(email, color = Color(0xFF91ccff), fontSize = 13.sp)
                    }
                    MatchPercentBadge(matchPercent)
                }
            }

            Spacer(Modifier.height(20.dp))

            // Current Time card (unchanged)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF151524)),
                shape = RoundedCornerShape(14.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Current Time", color = Color(0xFF8CEDA3), fontSize = 13.sp)
                    Text(
                        DateFormat.getTimeInstance(DateFormat.MEDIUM)
                            .format(Date(currentTime.value)),
                        fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.Medium
                    )
                    Text(
                        DateFormat.getDateInstance(DateFormat.LONG).format(Date(currentTime.value)),
                        fontSize = 13.sp, color = Color.Gray
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Last Attendance Info (unchanged text)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Today's Last Action:",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    lastEventType?.name?.replace('_', ' ')?.lowercase()
                        ?.replaceFirstChar { it.uppercase() }
                        ?.plus(" at $formattedLastTime")
                        ?: "No attendance recorded yet.",
                    color = Color(0xFFCBFF68),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(32.dp))

            // Status area (replaces buttons)
            if (saving.value && resultMsg.value == null) {
                Text(
                    "Marking ${if (nextEventType == AttendanceEventType.CHECK_IN) "Check In" else "Check Out"}…",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(12.dp))
                CircularProgressIndicator()
            } else {
                resultMsg.value?.let {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF044927)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            it,
                            Modifier.padding(14.dp),
                            color = Color(0xFFCBFF68),
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Keep a manual Back only if needed during failures (normally we auto-pop)
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving.value
            ) { Text("Back", fontWeight = FontWeight.Medium) }

            Spacer(Modifier.height(12.dp))
        }
    }
}


@Composable
private fun MatchPercentBadge(matchPercent: String) {
    val percent = matchPercent.toFloatOrNull() ?: 0f
    val color = when (percent) {
        in 80f..100f -> Color(0xFF4CAF50)
        in 60f..79.9f -> Color(0xFFFF9800)
        else -> Color(0xFFE53935)
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Text(
            "$matchPercent%",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}
