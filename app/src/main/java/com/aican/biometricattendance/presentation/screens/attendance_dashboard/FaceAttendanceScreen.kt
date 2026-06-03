package com.aican.biometricattendance.presentation.screens.attendance_dashboard

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.aican.biometricattendance.data.models.ui.MenuItem
import com.aican.biometricattendance.data.models.ui.QuickAction
import com.aican.biometricattendance.navigation.routes.AppRoutes
import com.aican.biometricattendance.presentation.screens.attendance_dashboard.components.QuickActionCard
import com.aican.biometricattendance.presentation.screens.mark_attendance.AskForEmailDialog
import com.aican.biometricattendance.presentation.theme.AttendanceTheme


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceAttendanceScreen(navController: NavController, logOut: () -> Unit) {
    var showDialog by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }

    val quickActions = listOf(
        QuickAction(
            icon = Icons.Filled.PersonAdd,
            title = "Register User",
            subtitle = "Add new employee",
            color = AttendanceTheme.Primary
        ),
//        QuickAction(
//            icon = Icons.Filled.Group,
//            title = "Register Face",
//            subtitle = "Manage profiles",
//            color = Color(0xFF9C27B0)
//        ),
        QuickAction(
            icon = Icons.Filled.CameraAlt,
            title = "Mark Attendance",
            subtitle = "Check in/out",
            color = AttendanceTheme.Secondary
        ),
        QuickAction(
            icon = Icons.Filled.Analytics,
            title = "Reports",
            subtitle = "View analytics",
            color = AttendanceTheme.Accent
        ),
        QuickAction(
            icon = Icons.Filled.Group,
            title = "Employees",
            subtitle = "Manage profiles",
            color = Color(0xFF9C27B0)
        ),

        )

    val menuItems = listOf(
        MenuItem(
            icon = Icons.Outlined.Schedule,
            title = "Today's Attendance",
            subtitle = "View today's check-ins",
            trailing = {
                Text(
                    text = "24",
                    color = AttendanceTheme.Secondary,
                    fontWeight = FontWeight.Bold
                )
            }
        ),
        MenuItem(
            icon = Icons.Outlined.CalendarMonth,
            title = "Monthly Report",
            subtitle = "Attendance summary"
        ),
        MenuItem(
            icon = Icons.Outlined.Settings,
            title = "Settings",
            subtitle = "App preferences"
        ),
        MenuItem(
            icon = Icons.Outlined.CloudSync,
            title = "Data Sync",
            subtitle = "Backup & restore",
            trailing = {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = AttendanceTheme.Secondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        ),
        MenuItem(
            icon = Icons.Outlined.Security,
            title = "Privacy & Security",
            subtitle = "Manage data privacy"
        ),
        MenuItem(
            icon = Icons.Outlined.Help,
            title = "Help & Support",
            subtitle = "Get assistance"
        )
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "FaceSecure",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Attendance Management",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = logOut) {
                        Icon(
                            Icons.Default.Logout, // Make sure you import: import androidx.compose.material.icons.filled.Logout
                            contentDescription = "Logout",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = { navController.navigate(AppRoutes.ROUTE_ACCOUNT.route) }) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Person,
                                contentDescription = "Profile",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                modifier = Modifier.background(AttendanceTheme.PrimaryGradient)
            )
        },
        containerColor = AttendanceTheme.Background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
//            item {
//                WelcomeSection()
//            }

//            item {
//                QuickStatsSection()
//            }

            item {
                Text(
                    text = "Quick Actions",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AttendanceTheme.OnSurface,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    quickActions.take(2).forEach { action ->
                        QuickActionCard(
                            action = action.copy(
                                onClick = {
                                    when (action.title) {
                                        "Register User" -> {
                                            navController.navigate(AppRoutes.ROUTE_CHECK_EMPLOYEE.route)
                                        }

                                        "Register Face" -> {
                                            navController.navigate(AppRoutes.ROUTE_FACE_IMAGE_PICKER.route)
                                        }

                                        "Mark Attendance" -> {
//                                            showDialog = true
                                            navController.navigate(
                                                AppRoutes.navigateToMarkAttendance("")
                                            )

                                        }

                                        "Employees" -> {
                                            navController.navigate(AppRoutes.ROUTE_REGISTERED_USERS.route)
                                        }

                                    }
                                }
                            ),
                            modifier = Modifier.weight(1f)
                        )

                    }
                }
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    quickActions.drop(2).take(2).forEach { action ->
                        QuickActionCard(
                            action = action.copy(
                                onClick = {
                                    when (action.title) {
                                        "Reports" -> {
                                            navController.navigate(AppRoutes.ROUTE_ATTENDANCE_REPORT.route)

                                        }

                                        "Mark Attendance" -> {

//                                            showDialog = true
                                            navController.navigate(
                                                AppRoutes.navigateToMarkAttendance("")
                                            )


                                        }

                                        "Employees" -> {
                                            navController.navigate(AppRoutes.ROUTE_REGISTERED_USERS.route)
                                        }
                                    }
                                }
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }


//            item {
//                Text(
//                    text = "Manage",
//                    fontSize = 18.sp,
//                    fontWeight = FontWeight.SemiBold,
//                    color = AttendanceTheme.OnSurface,
//                    modifier = Modifier.padding(bottom = 12.dp)
//                )
//                Card(
//                    colors = CardDefaults.cardColors(
//                        containerColor = AttendanceTheme.Surface
//                    ),
//                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
//                    shape = RoundedCornerShape(16.dp)
//                ) {
//                    Column {
//                        menuItems.forEachIndexed { index, item ->
//                            MenuItemRow(
//                                item = item,
//                                showDivider = index < menuItems.size - 1
//                            )
//                        }
//                    }
//                }
//            }

//            item {
//                UpgradeSection()
//            }

//            item {
//                FooterSection()
//            }
        }
    }



    if (showDialog) {
        AskForEmailDialog(
            inputValue = inputText,
            onInputChange = { inputText = it },
            onProceed = { value ->
                // Handle proceed logic here
                println("User input: $value")
                showDialog = false
//                navController.navigate(AppRoutes.ROUTE_MARK_ATTENDANCE_SCREEN.route)

                navController.navigate(
                    AppRoutes.navigateToMarkAttendance(value.toString())
                )

            },
            onDismissRequest = {
                showDialog = false
            }
        )
    }
}


@Composable
fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                color = color,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = color.copy(alpha = 0.8f)
            )
        }
    }
}

