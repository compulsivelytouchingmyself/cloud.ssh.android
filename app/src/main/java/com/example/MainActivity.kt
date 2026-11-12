package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.KeyManagerScreen
import com.example.ui.screens.ProfilesScreen
import com.example.ui.screens.WebPreviewScreen
import com.example.ui.theme.CloudSshTheme
import com.example.ui.theme.CyberCyan
import com.example.ui.viewmodel.AppTab
import com.example.ui.viewmodel.SshViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: SshViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CloudSshTheme {
                val snackbarHostState = remember { SnackbarHostState() }
                val activeTab by viewModel.activeTab.collectAsState()

                // Request notification permission on Android 13+
                val notificationPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { /* handle result */ }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                // Handle snackbar events from viewModel
                LaunchedEffect(Unit) {
                    viewModel.snackBarEvent.collect { message ->
                        snackbarHostState.showSnackbar(message)
                    }
                }

                // Handle system back navigation
                if (activeTab != AppTab.TUNNEL) {
                    BackHandler {
                        viewModel.setActiveTab(AppTab.TUNNEL)
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        NavigationBar(
                            modifier = Modifier
                                .windowInsetsPadding(WindowInsets.navigationBars)
                                .testTag("main_navigation_bar"),
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 8.dp
                        ) {
                            NavigationBarItem(
                                selected = activeTab == AppTab.TUNNEL,
                                onClick = { viewModel.setActiveTab(AppTab.TUNNEL) },
                                icon = { Icon(Icons.Default.Cloud, contentDescription = "Tunnel") },
                                label = { Text("Tunnel") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = CyberCyan,
                                    selectedTextColor = CyberCyan,
                                    indicatorColor = CyberCyan.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier.testTag("nav_item_tunnel")
                            )

                            NavigationBarItem(
                                selected = activeTab == AppTab.PROFILES,
                                onClick = { viewModel.setActiveTab(AppTab.PROFILES) },
                                icon = { Icon(Icons.Default.BookmarkBorder, contentDescription = "Profiles") },
                                label = { Text("Profiles") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = CyberCyan,
                                    selectedTextColor = CyberCyan,
                                    indicatorColor = CyberCyan.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier.testTag("nav_item_profiles")
                            )

                            NavigationBarItem(
                                selected = activeTab == AppTab.KEYS,
                                onClick = { viewModel.setActiveTab(AppTab.KEYS) },
                                icon = { Icon(Icons.Default.VpnKey, contentDescription = "Keys") },
                                label = { Text("Keys") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = CyberCyan,
                                    selectedTextColor = CyberCyan,
                                    indicatorColor = CyberCyan.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier.testTag("nav_item_keys")
                            )

                            NavigationBarItem(
                                selected = activeTab == AppTab.WEB_PREVIEW,
                                onClick = { viewModel.setActiveTab(AppTab.WEB_PREVIEW) },
                                icon = { Icon(Icons.Default.Visibility, contentDescription = "Browser Preview") },
                                label = { Text("Browser") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = CyberCyan,
                                    selectedTextColor = CyberCyan,
                                    indicatorColor = CyberCyan.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier.testTag("nav_item_preview")
                            )
                        }
                    }
                ) { innerPadding ->
                    when (activeTab) {
                        AppTab.TUNNEL -> HomeScreen(
                            viewModel = viewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                        AppTab.PROFILES -> ProfilesScreen(
                            viewModel = viewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                        AppTab.KEYS -> KeyManagerScreen(
                            viewModel = viewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                        AppTab.WEB_PREVIEW -> WebPreviewScreen(
                            viewModel = viewModel,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}
