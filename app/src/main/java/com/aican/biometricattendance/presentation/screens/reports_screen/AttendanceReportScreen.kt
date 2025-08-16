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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aican.biometricattendance.presentation.theme.AttendanceTheme.Divider
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
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            if (viewModel.unsyncedCount > 0) {
                Text(
                    "${viewModel.unsyncedCount} pending sync",
                    fontSize = 12.sp,
                    color = Color(0xFFFF8A65),
                    modifier = Modifier.padding(bottom = 1.dp)

                )
                Spacer(Modifier.height(1.dp))
                Button(
                    onClick = { viewModel.syncAttendance() },
                    enabled = !isSyncing && viewModel.unsyncedCount > 0,
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Text("Sync")
                }
                Spacer(Modifier.height(8.dp))

            }

            // Inline sync status UI
            if (isSyncing) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF102A43))
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            syncMessage ?: "Syncing…",
                            color = Color(0xFFB3E5FC),
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = syncProgress / 100f, modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            } else {
                // Show last message or error if any
                syncMessage?.let {
                    Text(it, color = Color(0xFF00C853), fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                }
                syncError?.let {
                    Text(it, color = Color(0xFFFF5252), fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                }
            }

            Text(
                "Total Records: ${logs.size}",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )


            if (logs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No attendance records available.")
                }
            } else {
                // Table Header
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFE0E0E0))
                        .padding(vertical = 8.dp)
                ) {
                    Text("Emp ID", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text("Type", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text("Time", Modifier.weight(1.5f), fontWeight = FontWeight.Bold)
                    Text("%", Modifier.weight(0.7f), fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(4.dp))

                LazyColumn {
                    items(logs) { log ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .background(Color(0xFFF9F9F9))
                        ) {
                            Text(log.employeeId, Modifier.weight(1f))
                            Text(
                                log.eventType.name.replace("_", " ").capitalize(Locale.ROOT),
                                Modifier.weight(1f)
                            )
                            Text(
                                SimpleDateFormat(
                                    "dd MMM yyyy, hh:mm a",
                                    Locale.getDefault()
                                ).format(Date(log.timestamp)), Modifier.weight(1.5f)
                            )
                            Text("%.2f".format(log.matchPercent), Modifier.weight(0.7f))
                        }

                        Divider()
                    }
                }
            }
        }
    }
}
