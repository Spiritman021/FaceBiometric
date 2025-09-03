package com.aican.biometricattendance.presentation.screens.reports_screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceReportScreen(
    viewModel: AttendanceReportViewModel,
    onBack: () -> Unit,
) {
    val logs = viewModel.attendanceLogs
    val isSyncing = viewModel.isSyncing
    val syncProgress = viewModel.syncProgress
    val syncMessage = viewModel.syncMessage
    val syncError = viewModel.syncError

    LaunchedEffect(Unit) {
        viewModel.fetchAllAttendanceLogs()
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Attendance Report") }, navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            })
        }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                "Total Records: ${logs.size}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (viewModel.unsyncedCount > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "${viewModel.unsyncedCount} records pending sync",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { viewModel.syncAttendance() },
                        enabled = !isSyncing && viewModel.unsyncedCount > 0
                    ) {
                        Text("Sync Now")
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            if (isSyncing) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            syncMessage ?: "Syncing…",
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { syncProgress / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            } else {
                syncMessage?.let {
                    Text(it, color = Color(0xFF00C853), fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                }
                syncError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (logs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No attendance records available.")
                }
            } else {
                // ## Table Header - UPDATED ##
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Emp ID", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text("Type", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text("Time", Modifier.weight(2f), fontWeight = FontWeight.Bold)
                    Text("%", Modifier.weight(0.6f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("Sync", Modifier.weight(0.5f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("Del", Modifier.weight(0.5f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }

                Divider()

                LazyColumn {
                    items(logs, key = { it.id }) { log ->
                        // ## Table Row - UPDATED ##
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(log.employeeId, Modifier.weight(1f), fontSize = 14.sp)
                            Text(
                                log.eventType.name
                                    .replace('_', ' ')
                                    .lowercase()
                                    .replaceFirstChar { it.titlecase(Locale.getDefault()) },
                                Modifier.weight(1f),
                                fontSize = 14.sp
                            )
                            Text(
                                SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                                    .format(Date(log.timestamp)),
                                Modifier.weight(2f),
                                fontSize = 13.sp
                            )
                            Text(
                                (log.matchPercent * 100).toInt().toString(),
                                Modifier.weight(0.6f),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                            // Sync Status Icon
                            Box(Modifier.weight(0.5f), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (log.synced) Icons.Default.CheckCircle else Icons.Default.CloudUpload,
                                    contentDescription = if (log.synced) "Synced" else "Not Synced",
                                    tint = if (log.synced) Color(0xFF4CAF50) else Color.Gray,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            // Delete Button
                            Box(Modifier.weight(0.5f), contentAlignment = Alignment.Center) {
                                IconButton(
                                    onClick = { viewModel.deleteLog(log) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Log",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                        Divider()
                    }
                }
            }
        }
    }
}