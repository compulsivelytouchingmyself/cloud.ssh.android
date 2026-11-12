package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SshAuthType
import com.example.ssh.TunnelState
import com.example.ui.components.KeySelectorDialog
import com.example.ui.components.LogConsole
import com.example.ui.components.PortPresetChips
import com.example.ui.components.StatusIndicator
import com.example.ui.theme.AmberPending
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.EmeraldConnected
import com.example.ui.viewmodel.AppTab
import com.example.ui.viewmodel.SshViewModel

@Composable
fun HomeScreen(
    viewModel: SshViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val rawCommand by viewModel.rawCommand.collectAsState()
    val host by viewModel.host.collectAsState()
    val sshPort by viewModel.sshPort.collectAsState()
    val username by viewModel.username.collectAsState()
    val remotePort by viewModel.remotePort.collectAsState()
    val localPort by viewModel.localPort.collectAsState()
    val proxyPort by viewModel.proxyPort.collectAsState()
    val commandAugmentation by viewModel.commandAugmentation.collectAsState()
    val authType by viewModel.authType.collectAsState()
    val privateKey by viewModel.privateKey.collectAsState()
    val password by viewModel.password.collectAsState()
    val autoOpenBrowser by viewModel.autoOpenBrowser.collectAsState()
    val tunnelState by viewModel.tunnelState.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val savedKeys by viewModel.savedKeyPairs.collectAsState()

    val isDynamicSshPort by viewModel.isDynamicSshPort.collectAsState()
    val hasPortPlaceholder by viewModel.hasPortPlaceholder.collectAsState()
    val detectedVastForwardPort by viewModel.detectedVastForwardPort.collectAsState()

    var showKeyDialog by remember { mutableStateOf(false) }
    var showManualSettings by remember { mutableStateOf(false) }
    var showQuickSampleDialog by remember { mutableStateOf(false) }

    val isConnected = tunnelState is TunnelState.Connected
    val isConnecting = tunnelState is TunnelState.Connecting

    if (showKeyDialog) {
        KeySelectorDialog(
            savedKeys = savedKeys,
            currentKey = privateKey,
            onKeySelected = { viewModel.applyKeyPair(it) },
            onPasteKey = { viewModel.privateKey.value = it },
            onGenerateKey = { type, title -> viewModel.generateNewKey(type, title) },
            onDismiss = { showKeyDialog = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top App Bar / Status Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(CyberCyan.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cloud,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CloudSSH",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Text(
                    text = "Cloud SSH & Remote Browser Proxy",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            StatusIndicator(state = tunnelState)
        }

        // -------------------------------------------------------------
        // ACTIVE CONNECTION CARD (When Connected)
        // -------------------------------------------------------------
        AnimatedVisibility(
            visible = isConnected,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            (tunnelState as? TunnelState.Connected)?.let { state ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.5.dp,
                            Brush.linearGradient(listOf(EmeraldConnected, CyberCyan)),
                            RoundedCornerShape(16.dp)
                        )
                        .testTag("active_tunnel_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF0F2438)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(EmeraldConnected, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "PROXY ACTIVE",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldConnected
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            if (state.pingMs > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = null,
                                        tint = CyberCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${state.pingMs} ms",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = CyberCyan
                                    )
                                }
                            }
                        }

                        // Local URL Box (Click to open)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF07121F), RoundedCornerShape(10.dp))
                                .border(1.dp, EmeraldConnected.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                .clickable {
                                    viewModel.openBrowserExternal(context, state.localUrl)
                                }
                                .padding(12.dp)
                                .testTag("proxy_url_banner")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Local Browser Address",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = state.localUrl,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = EmeraldConnected
                                    )
                                    Text(
                                        text = "Forwarding remote cloud port :${state.remotePort}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.OpenInBrowser,
                                    contentDescription = "Open",
                                    tint = EmeraldConnected,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        // Primary Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.openBrowserExternal(context, state.localUrl) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EmeraldConnected,
                                    contentColor = Color(0xFF003319)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("open_browser_button")
                            ) {
                                Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open in Browser", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            OutlinedButton(
                                onClick = { viewModel.setActiveTab(AppTab.WEB_PREVIEW) },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = CyberCyan
                                ),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("in_app_preview_button")
                            ) {
                                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("In-App View", fontSize = 12.sp)
                            }
                        }

                        // Prominent "End Connection" button
                        Button(
                            onClick = { viewModel.disconnect() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CrimsonError.copy(alpha = 0.2f),
                                contentColor = CrimsonError
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CrimsonError.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("end_connection_button")
                        ) {
                            Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("End Connection", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // ERROR BANNER (Actionable Help for Auth Failures & Key Errors)
        // -------------------------------------------------------------
        AnimatedVisibility(
            visible = tunnelState is TunnelState.Error,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            (tunnelState as? TunnelState.Error)?.let { err ->
                val isAuthFail = err.message.contains("Auth fail", ignoreCase = true) ||
                        err.message.contains("preauth", ignoreCase = true) ||
                        err.message.contains("rejected", ignoreCase = true)
                val isKeyFormatError = err.message.contains("invalid privatekey", ignoreCase = true) ||
                        err.message.contains("ASN1", ignoreCase = true)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CrimsonError.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .testTag("connection_error_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF260D13)
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = CrimsonError,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isAuthFail) "Authentication Failed" else if (isKeyFormatError) "Key Format Error" else "Connection Failed",
                                fontWeight = FontWeight.Bold,
                                color = CrimsonError,
                                fontSize = 14.sp
                            )
                        }

                        Text(
                            text = if (isAuthFail) {
                                "The cloud server rejected the public key. Verify that your public key is added to the cloud instance's ~/.ssh/authorized_keys (or cloud console). If your instance requires a password, switch to Password auth."
                            } else if (isKeyFormatError) {
                                "The private key could not be parsed. Ensure the key begins with '-----BEGIN ... PRIVATE KEY-----'."
                            } else {
                                err.message
                            },
                            fontSize = 12.sp,
                            color = Color(0xFFFFD4D8),
                            lineHeight = 16.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isAuthFail) {
                                Button(
                                    onClick = { showKeyDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CyberCyan,
                                        contentColor = Color(0xFF002026)
                                    ),
                                    modifier = Modifier.weight(1f).height(38.dp).testTag("error_copy_key_button")
                                ) {
                                    Text("View / Copy Public Key", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = { viewModel.authType.value = SshAuthType.PASSWORD },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                                    modifier = Modifier.height(38.dp).testTag("error_switch_password_button")
                                ) {
                                    Text("Use Password", fontSize = 11.sp)
                                }
                            } else if (isKeyFormatError) {
                                Button(
                                    onClick = { showKeyDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CyberCyan,
                                        contentColor = Color(0xFF002026)
                                    ),
                                    modifier = Modifier.fillMaxWidth().height(38.dp)
                                ) {
                                    Text("Manage SSH Keys", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // 1. CLOUD SSH CONNECTION
        // -------------------------------------------------------------
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                .testTag("paste_ssh_card"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "1. Cloud SSH Connection",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.weight(1f))

                    // Paste button
                    OutlinedButton(
                        onClick = {
                            val clip = clipboardManager.getText()?.text
                            if (!clip.isNullOrBlank()) {
                                viewModel.onCommandPasted(clip)
                                Toast.makeText(context, "Pasted & analyzed SSH command", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("paste_clipboard_button")
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Paste", fontSize = 12.sp)
                    }
                }

                Text(
                    text = "Paste the SSH command provided by your cloud provider (Vast.ai, RunPod, AWS, etc.):",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = rawCommand,
                    onValueChange = { viewModel.onCommandPasted(it) },
                    placeholder = {
                        Text(
                            "ssh -p 34567 root@ssh4.vast.ai -L 8080:localhost:8080",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF64748B)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ssh_command_input"),
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = CyberCyan
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        focusedContainerColor = Color(0xFF090F21),
                        unfocusedContainerColor = Color(0xFF090F21)
                    ),
                    maxLines = 4
                )

                if (host.isNotBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF081524), RoundedCornerShape(8.dp))
                            .border(1.dp, CyberCyan.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = EmeraldConnected,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Target: $username@$host:$sshPort",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = EmeraldConnected,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (isDynamicSshPort) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Dynamic Port",
                                    fontSize = 10.sp,
                                    color = CyberCyan,
                                    modifier = Modifier
                                        .background(CyberCyan.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (commandAugmentation.hasExistingL) {
                            val existingList = commandAugmentation.existingForwardings.joinToString { "${it.localPort}:${it.remotePort}" }
                            Text(
                                text = "Detected Existing -L: $existingList",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = CyberCyan
                            )
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // 2. BROWSER PROXY & -L PORT MIRRORING
        // -------------------------------------------------------------
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                .testTag("proxy_port_card"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Router,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "2. Browser Proxy Port",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Text(
                    text = "What port do you want to access in your browser? (e.g. Gradio is 7860, Jupyter is 8888)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = proxyPort,
                        onValueChange = { viewModel.onProxyPortChanged(it) },
                        label = { Text("Proxy Port") },
                        placeholder = { Text("7860") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .width(130.dp)
                            .testTag("remote_port_input"),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = CyberCyan
                        )
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Auto-Open in Browser",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = autoOpenBrowser,
                                onCheckedChange = { viewModel.autoOpenBrowser.value = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = CyberCyan,
                                    checkedTrackColor = CyberCyan.copy(alpha = 0.35f)
                                ),
                                modifier = Modifier.testTag("auto_open_browser_switch")
                            )
                        }
                        Text(
                            text = "Opens http://127.0.0.1:${proxyPort.ifBlank { "7860" }} on connect",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Quick presets
                PortPresetChips(
                    presets = viewModel.portPresets,
                    selectedPort = proxyPort.toIntOrNull(),
                    onPresetSelected = { viewModel.applyPreset(it) }
                )

                // -L Port Mirroring inspection banner
                val aug = commandAugmentation
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF081524), RoundedCornerShape(10.dp))
                        .border(1.dp, CyberCyan.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (aug.hasExistingL) "Port Mirroring: -L detected" else "Port Mirroring: Creating -L",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyberCyan
                            )
                        }

                        Text(
                            text = if (aug.hasExistingL) {
                                val existingList = aug.existingForwardings.joinToString { "${it.localPort}:${it.remotePort}" }
                                "Existing -L port mirror found ($existingList). Adding browser proxy -L $proxyPort:localhost:$proxyPort alongside it. Other than that, your SSH command is not modified."
                            } else {
                                "No -L found in command. Creating -L $proxyPort:localhost:$proxyPort for browser access. Other than that, your SSH command is not modified."
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp
                        )

                        if (aug.effectiveCommand.isNotBlank()) {
                            Text(
                                text = "Effective SSH command:",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = aug.effectiveCommand,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = EmeraldConnected
                            )
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // STEP 3: SSH AUTHENTICATION / KEY SELECTION
        // -------------------------------------------------------------
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                .testTag("auth_card"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.VpnKey,
                        contentDescription = null,
                        tint = CyberCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "3. SSH Key / Credentials",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.weight(1f))

                    TextButton(
                        onClick = { showKeyDialog = true },
                        modifier = Modifier.testTag("manage_keys_button")
                    ) {
                        Text("Manage Keys", fontSize = 12.sp, color = CyberCyan)
                    }
                }

                // Key or Password toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.authType.value = SshAuthType.KEY },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (authType == SshAuthType.KEY) CyberCyan.copy(alpha = 0.15f) else Color.Transparent,
                            contentColor = if (authType == SshAuthType.KEY) CyberCyan else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f).testTag("auth_key_tab")
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Private Key", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { viewModel.authType.value = SshAuthType.PASSWORD },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (authType == SshAuthType.PASSWORD) CyberCyan.copy(alpha = 0.15f) else Color.Transparent,
                            contentColor = if (authType == SshAuthType.PASSWORD) CyberCyan else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f).testTag("auth_password_tab")
                    ) {
                        Text("Password", fontSize = 12.sp)
                    }
                }

                if (authType == SshAuthType.KEY) {
                    if (privateKey.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CyberCyan.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .border(1.dp, CyberCyan.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Private Key Configured", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = CyberCyan)
                                Text("${privateKey.take(30)}... (${privateKey.length} chars)", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            val matchingKey = savedKeys.firstOrNull { it.privateKey.trim() == privateKey.trim() }
                            if (matchingKey != null && matchingKey.publicKey.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(matchingKey.publicKey))
                                        Toast.makeText(context, "Public key copied to clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Public Key", tint = CyberCyan, modifier = Modifier.size(16.dp))
                                }
                            }
                            TextButton(onClick = { showKeyDialog = true }) {
                                Text("Change", fontSize = 11.sp, color = CyberCyan)
                            }
                        }
                    } else {
                        Button(
                            onClick = { showKeyDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("select_ssh_key_button")
                        ) {
                            Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select, Paste, or Generate SSH Key")
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { viewModel.password.value = it },
                        label = { Text("SSH Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("password_input")
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // PRIMARY CONNECT BUTTON / ACTIONS
        // -------------------------------------------------------------
        if (!isConnected) {
            Button(
                onClick = { viewModel.connect() },
                enabled = !isConnecting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberCyan,
                    contentColor = Color(0xFF002026),
                    disabledContainerColor = CyberCyan.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("connect_button")
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        color = Color(0xFF002026),
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Connecting & Binding Proxy...", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                } else {
                    Icon(Icons.Default.Cloud, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("CONNECT & OPEN BROWSER", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                }
            }

            // Save profile row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(
                    onClick = { viewModel.saveProfile() },
                    modifier = Modifier.testTag("save_as_profile_button")
                ) {
                    Icon(Icons.Default.BookmarkBorder, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save this Connection to Profiles", fontSize = 12.sp, color = CyberCyan)
                }
            }
        } else {
            // Already connected: End Connection is in top card, but offer a duplicate quick disconnect button
            OutlinedButton(
                onClick = { viewModel.disconnect() },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = CrimsonError
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, CrimsonError),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("end_connection_secondary_button")
            ) {
                Icon(Icons.Default.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("End Connection", fontWeight = FontWeight.Bold)
            }
        }

        // -------------------------------------------------------------
        // LIVE TERMINAL LOGS
        // -------------------------------------------------------------
        LogConsole(
            logs = logs,
            onClearLogs = { viewModel.clearLogs() }
        )

        Spacer(modifier = Modifier.height(20.dp))
    }
}
