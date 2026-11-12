package com.example.ssh

import com.example.data.model.ConnectionProfile
import com.example.data.model.SshAuthType
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Logger
import com.jcraft.jsch.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.ServerSocket

class SshTunnelManager(
    private val scope: CoroutineScope
) {
    private val _tunnelState = MutableStateFlow<TunnelState>(TunnelState.Disconnected)
    val tunnelState: StateFlow<TunnelState> = _tunnelState.asStateFlow()

    private val _logs = MutableStateFlow<List<TunnelLog>>(emptyList())
    val logs: StateFlow<List<TunnelLog>> = _logs.asStateFlow()

    private var activeSession: Session? = null
    private var pingJob: Job? = null
    private var remoteCommandJob: Job? = null
    private var activeChannel: com.jcraft.jsch.Channel? = null
    private var activeProfile: ConnectionProfile? = null

    init {
        // Forward JSch internal logging to our log flow
        JSch.setLogger(object : Logger {
            override fun isEnabled(level: Int): Boolean = true
            override fun log(level: Int, message: String) {
                val logLevel = when (level) {
                    Logger.FATAL, Logger.ERROR -> LogLevel.ERROR
                    Logger.WARN -> LogLevel.WARNING
                    else -> LogLevel.INFO
                }
                // Filter out overly noisy byte dumps
                if (!message.contains("Packet", ignoreCase = true) && !message.contains("Cipher", ignoreCase = true)) {
                    appendLog(logLevel, "[SSH] $message")
                }
            }
        })
    }

    private fun appendLog(level: LogLevel, message: String) {
        val entry = TunnelLog(level = level, message = message)
        val current = _logs.value.toMutableList()
        if (current.size > 200) {
            current.removeAt(0)
        }
        current.add(entry)
        _logs.value = current
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    suspend fun connect(profile: ConnectionProfile): Result<Int> = withContext(Dispatchers.IO) {
        if (_tunnelState.value is TunnelState.Connected) {
            appendLog(LogLevel.WARNING, "Disconnecting existing tunnel before starting new connection...")
            disconnect()
        }

        activeProfile = profile
        _tunnelState.value = TunnelState.Connecting("Initializing SSH client...")
        appendLog(LogLevel.INFO, "Target: ${profile.username}@${profile.host}:${profile.port}")
        appendLog(LogLevel.INFO, "Remote Port: ${profile.remotePort} -> Local Proxy: ${profile.localPort}")

        try {
            val jsch = JSch()

            // Handle Authentication (Key or Password)
            if (profile.authType == SshAuthType.KEY.name) {
                if (profile.privateKey.isNotBlank()) {
                    appendLog(LogLevel.INFO, "Loading private key identity...")
                    val cleanKey = SshKeyHelper.sanitizePrivateKey(profile.privateKey)
                    val passBytes = if (profile.passphrase.isNotBlank()) {
                        profile.passphrase.toByteArray(Charsets.UTF_8)
                    } else null

                    try {
                        val keyBytes = cleanKey.toByteArray(Charsets.UTF_8)
                        jsch.addIdentity("cloud_key", keyBytes, null, passBytes)
                    } catch (keyEx: Exception) {
                        // Check if key is in PKCS#8 format and needs conversion to PKCS#1
                        val converted = SshKeyHelper.tryConvertPkcs8ToPkcs1(cleanKey)
                        if (converted != null) {
                            appendLog(LogLevel.INFO, "Auto-converted PKCS#8 key to OpenSSH/PKCS#1 for compatibility.")
                            val convertedBytes = converted.toByteArray(Charsets.UTF_8)
                            jsch.addIdentity("cloud_key", convertedBytes, null, passBytes)
                        } else {
                            appendLog(LogLevel.ERROR, "Key parsing failed: ${keyEx.message}")
                            throw keyEx
                        }
                    }
                } else {
                    appendLog(LogLevel.WARNING, "No private key provided, attempting agent/default keys...")
                }
            }

            _tunnelState.value = TunnelState.Connecting("Resolving host ${profile.host}...")
            val session = jsch.getSession(profile.username, profile.host, profile.port)

            if (profile.authType == SshAuthType.PASSWORD.name && profile.password.isNotBlank()) {
                session.setPassword(profile.password)
            }

            val config = java.util.Properties()
            config["StrictHostKeyChecking"] = "no"
            config["PreferredAuthentications"] = "publickey,keyboard-interactive,password"
            config["ConnectTimeout"] = "15000"
            // Ensure compatibility with modern OpenSSH 8.8+ (requires rsa-sha2-512 / rsa-sha2-256)
            config["server_host_key"] = "ssh-ed25519,ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521,rsa-sha2-512,rsa-sha2-256"
            config["PubkeyAcceptedAlgorithms"] = "ssh-ed25519,ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521,rsa-sha2-512,rsa-sha2-256"
            session.setConfig(config)
            session.serverAliveInterval = 15 // seconds
            session.serverAliveCountMax = 3

            _tunnelState.value = TunnelState.Connecting("Connecting SSH handshake...")
            appendLog(LogLevel.INFO, "Establishing SSH session (timeout 15s)...")
            session.connect(15000)

            if (!session.isConnected) {
                throw IllegalStateException("Session connection failed.")
            }
            appendLog(LogLevel.SUCCESS, "SSH handshake established successfully!")

            // Port forwarding setup
            _tunnelState.value = TunnelState.Connecting("Binding port mirroring...")
            val forwardRules = SshCommandParser.extractAllForwardRules(profile.rawCommand).toMutableList()

            // If the requested browser proxy port is not already present, ensure it is forwarded
            val desiredProxyPort = profile.remotePort
            if (forwardRules.none { it.remotePort == desiredProxyPort }) {
                forwardRules.add(
                    PortForwardRule(
                        localPort = profile.localPort,
                        remoteHost = "127.0.0.1",
                        remotePort = desiredProxyPort
                    )
                )
            }

            var primaryBoundLocalPort = profile.localPort
            for (rule in forwardRules) {
                val targetLocalPort = resolveAvailablePort(rule.localPort)
                val boundPort = session.setPortForwardingL("127.0.0.1", targetLocalPort, rule.remoteHost, rule.remotePort)
                appendLog(LogLevel.SUCCESS, "Port mirror bound: 127.0.0.1:$boundPort -> ${rule.remoteHost}:${rule.remotePort}")
                if (rule.remotePort == desiredProxyPort) {
                    primaryBoundLocalPort = boundPort
                }
            }

            appendLog(LogLevel.SUCCESS, "Browser proxy ready at http://127.0.0.1:$primaryBoundLocalPort")

            activeSession = session

            _tunnelState.value = TunnelState.Connected(
                host = profile.host,
                sshPort = profile.port,
                localPort = primaryBoundLocalPort,
                remotePort = desiredProxyPort,
                connectedSince = System.currentTimeMillis()
            )

            startKeepalivePing(session)

            if (profile.postConnectCommand.isNotBlank()) {
                launchPostConnectInstructions(session, profile.postConnectCommand)
            }

            Result.success(primaryBoundLocalPort)
        } catch (e: Exception) {
            val errorMsg = e.localizedMessage ?: e.message ?: "Unknown SSH connection error"
            if (errorMsg.contains("Auth fail", ignoreCase = true) || errorMsg.contains("preauth", ignoreCase = true)) {
                appendLog(LogLevel.ERROR, "Authentication failed: The remote server rejected the credentials.")
                appendLog(LogLevel.WARNING, "-> If using Public Key: Ensure this key's public key is added to the instance's ~/.ssh/authorized_keys or Cloud Console.")
                appendLog(LogLevel.WARNING, "-> If your instance requires a password: Switch Auth Type to 'Password' in settings.")
                appendLog(LogLevel.WARNING, "-> You can copy your public key anytime from the SSH Keys tab.")
            } else if (errorMsg.contains("invalid privatekey", ignoreCase = true) || errorMsg.contains("ASN1", ignoreCase = true)) {
                appendLog(LogLevel.ERROR, "Private key format error: $errorMsg")
                appendLog(LogLevel.WARNING, "-> Check that the key starts with -----BEGIN ... PRIVATE KEY----- and ends with -----END ... PRIVATE KEY-----.")
            } else {
                appendLog(LogLevel.ERROR, "Connection error: $errorMsg")
            }
            _tunnelState.value = TunnelState.Error(errorMsg)
            cleanup()
            Result.failure(e)
        }
    }

    private fun launchPostConnectInstructions(session: Session, commands: String) {
        remoteCommandJob?.cancel()
        remoteCommandJob = scope.launch(Dispatchers.IO) {
            delay(500)
            appendLog(LogLevel.INFO, "[Auto-Exec] Prompt reached. Executing startup instructions...")
            try {
                val channel = session.openChannel("exec") as com.jcraft.jsch.ChannelExec
                activeChannel = channel

                val escapedCommands = commands.replace("'", "'\\''")
                channel.setCommand("bash -l -c '$escapedCommands'")
                channel.setPty(true)

                val stream = channel.inputStream
                channel.connect(10000)

                val reader = java.io.BufferedReader(java.io.InputStreamReader(stream, Charsets.UTF_8))
                var line = reader.readLine()
                while (isActive && line != null) {
                    val l = line
                    if (l.isNotBlank()) {
                        val clean = l.replace(Regex("\u001B\\[[;\\d]*m"), "").trimEnd()
                        appendLog(LogLevel.INFO, "[Remote] $clean")
                    }
                    line = reader.readLine()
                }

                if (!channel.isClosed) {
                    channel.disconnect()
                }
                appendLog(LogLevel.SUCCESS, "[Auto-Exec] Startup instructions finished.")
            } catch (e: Exception) {
                if (isActive && session.isConnected) {
                    appendLog(LogLevel.ERROR, "[Auto-Exec Error] ${e.message}")
                }
            }
        }
    }

    private fun resolveAvailablePort(preferredPort: Int): Int {
        return try {
            ServerSocket(preferredPort).use { it.localPort }
        } catch (e: Exception) {
            // Port already in use, find an available ephemeral port
            ServerSocket(0).use { it.localPort }
        }
    }

    private fun startKeepalivePing(session: Session) {
        pingJob?.cancel()
        pingJob = scope.launch(Dispatchers.IO) {
            while (isActive && session.isConnected) {
                delay(10000)
                try {
                    val start = System.currentTimeMillis()
                    session.sendKeepAliveMsg()
                    val latency = System.currentTimeMillis() - start
                    val current = _tunnelState.value
                    if (current is TunnelState.Connected) {
                        _tunnelState.value = current.copy(pingMs = latency)
                    }
                } catch (e: Exception) {
                    if (session.isConnected) {
                        appendLog(LogLevel.WARNING, "Keepalive warn: ${e.message}")
                    } else {
                        appendLog(LogLevel.ERROR, "SSH Session dropped unexpectedly.")
                        disconnect()
                        break
                    }
                }
            }
        }
    }

    fun disconnect() {
        scope.launch(Dispatchers.IO) {
            appendLog(LogLevel.INFO, "Ending SSH connection and port forwarding...")
            cleanup()
            _tunnelState.value = TunnelState.Disconnected
            appendLog(LogLevel.SUCCESS, "Connection ended.")
        }
    }

    private fun cleanup() {
        pingJob?.cancel()
        pingJob = null
        remoteCommandJob?.cancel()
        remoteCommandJob = null
        try {
            activeChannel?.let { ch ->
                if (ch.isConnected) {
                    ch.disconnect()
                }
            }
        } catch (e: Exception) {
            // ignore
        } finally {
            activeChannel = null
        }
        try {
            activeSession?.let { session ->
                if (session.isConnected) {
                    session.disconnect()
                }
            }
        } catch (e: Exception) {
            // ignore cleanup errors
        } finally {
            activeSession = null
        }
    }
}
