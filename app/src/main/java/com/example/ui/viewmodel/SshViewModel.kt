package com.example.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.ConnectionProfile
import com.example.data.model.SshAuthType
import com.example.data.model.SshKeyPair
import com.example.data.repository.SshRepository
import com.example.ssh.GeneratedKeyPairResult
import com.example.ssh.SshCommandParser
import com.example.ssh.SshKeyHelper
import com.example.ssh.SshTunnelService
import com.example.ssh.TunnelLog
import com.example.ssh.TunnelState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AppTab {
    TUNNEL,
    PROFILES,
    KEYS,
    WEB_PREVIEW
}

data class PortPreset(
    val port: Int,
    val label: String,
    val serviceName: String,
    val iconEmoji: String
)

class SshViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SshRepository(AppDatabase.getInstance(application).connectionDao())

    val savedProfiles: StateFlow<List<ConnectionProfile>> = repository.allProfiles.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val savedKeyPairs: StateFlow<List<SshKeyPair>> = repository.allKeyPairs.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // Navigation Tab
    private val _activeTab = MutableStateFlow(AppTab.TUNNEL)
    val activeTab: StateFlow<AppTab> = _activeTab.asStateFlow()

    fun setActiveTab(tab: AppTab) {
        _activeTab.value = tab
    }

    // Input States
    val rawCommand = MutableStateFlow("")
    val host = MutableStateFlow("")
    val sshPort = MutableStateFlow("22")
    val username = MutableStateFlow("root")
    val remotePort = MutableStateFlow("7860")
    val localPort = MutableStateFlow("7860")
    val authType = MutableStateFlow(SshAuthType.KEY)
    val privateKey = MutableStateFlow("")
    val passphrase = MutableStateFlow("")
    val password = MutableStateFlow("")
    val autoOpenBrowser = MutableStateFlow(true)
    val profileName = MutableStateFlow("")

    val proxyPort = MutableStateFlow("7860")
    val commandAugmentation = MutableStateFlow(
        com.example.ssh.SshCommandParser.augmentCommandWithProxyPort("", 7860)
    )

    val isDynamicSshPort = MutableStateFlow(false)
    val hasPortPlaceholder = MutableStateFlow(false)
    val detectedVastForwardPort = MutableStateFlow<Int?>(null)

    val detectedKeyHint = MutableStateFlow<String?>(null)

    // Service binding & connection state
    private var tunnelService: SshTunnelService? = null
    private val _isServiceBound = MutableStateFlow(false)

    private val _tunnelState = MutableStateFlow<TunnelState>(TunnelState.Disconnected)
    val tunnelState: StateFlow<TunnelState> = _tunnelState.asStateFlow()

    private val _logs = MutableStateFlow<List<TunnelLog>>(emptyList())
    val logs: StateFlow<List<TunnelLog>> = _logs.asStateFlow()

    private val _snackBarEvent = MutableSharedFlow<String>()
    val snackBarEvent: SharedFlow<String> = _snackBarEvent.asSharedFlow()

    val portPresets = listOf(
        PortPreset(7860, "7860", "Gradio / WebUI", "📻"),
        PortPreset(8888, "8888", "JupyterLab", "🪐"),
        PortPreset(8188, "8188", "ComfyUI", "🎨"),
        PortPreset(6006, "6006", "TensorBoard", "📊"),
        PortPreset(11434, "11434", "Ollama LLM", "🦙"),
        PortPreset(8080, "8080", "HTTP WebApp", "🌐"),
        PortPreset(3000, "3000", "Node / React", "⚡"),
        PortPreset(5000, "5000", "Flask / API", "🚀")
    )

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? SshTunnelService.LocalBinder
            tunnelService = binder?.getService()
            _isServiceBound.value = true

            tunnelService?.let { s ->
                viewModelScope.launch {
                    s.tunnelManager.tunnelState.collect { state ->
                        _tunnelState.value = state
                    }
                }
                viewModelScope.launch {
                    s.tunnelManager.logs.collect { newLogs ->
                        _logs.value = newLogs
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            tunnelService = null
            _isServiceBound.value = false
        }
    }

    init {
        val app = getApplication<Application>()
        val intent = Intent(app, SshTunnelService::class.java)
        app.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun updateAugmentation() {
        val port = proxyPort.value.toIntOrNull() ?: 7860
        commandAugmentation.value = SshCommandParser.augmentCommandWithProxyPort(rawCommand.value, port)
        remotePort.value = port.toString()
        localPort.value = port.toString()
    }

    fun onProxyPortChanged(port: String) {
        proxyPort.value = port
        updateAugmentation()
    }

    fun onCommandPasted(command: String) {
        rawCommand.value = command
        updateAugmentation()
        val parsed = SshCommandParser.parse(command)

        if (parsed.host.isNotBlank()) {
            host.value = parsed.host
            username.value = parsed.username

            // Handle dynamic cloud SSH port
            if (parsed.hasExplicitPort && parsed.port != null) {
                sshPort.value = parsed.port.toString()
                isDynamicSshPort.value = true
                hasPortPlaceholder.value = false
            } else if (parsed.hasPortPlaceholder) {
                // Command had placeholder like -p port. Do not insert generic 22!
                sshPort.value = ""
                hasPortPlaceholder.value = true
                isDynamicSshPort.value = false
            } else {
                // Command had no port at all
                isDynamicSshPort.value = false
                hasPortPlaceholder.value = false
                if (sshPort.value.isBlank()) {
                    sshPort.value = "22"
                }
            }

            if (parsed.keyIdentityHint.isNotBlank()) {
                detectedKeyHint.value = parsed.keyIdentityHint
                // Try matching key with saved key pairs
                val matched = savedKeyPairs.value.firstOrNull {
                    parsed.keyIdentityHint.contains(it.title, ignoreCase = true) ||
                            it.title.contains(parsed.keyIdentityHint, ignoreCase = true)
                }
                if (matched != null) {
                    privateKey.value = matched.privateKey
                }
            }

            // If command already has -L forward(s), register it
            if (parsed.detectedRemotePort != null) {
                detectedVastForwardPort.value = parsed.detectedRemotePort
            } else {
                detectedVastForwardPort.value = null
            }

            viewModelScope.launch {
                val portDesc = when {
                    parsed.hasExplicitPort -> "dynamic SSH port :${parsed.port}"
                    parsed.hasPortPlaceholder -> "SSH port needed"
                    else -> "standard SSH port :22"
                }
                _snackBarEvent.emit("Parsed ${parsed.username}@${parsed.host} ($portDesc)")
            }
        }
    }

    fun applyPreset(preset: PortPreset) {
        proxyPort.value = preset.port.toString()
        updateAugmentation()
    }

    fun applyKeyPair(keyPair: SshKeyPair) {
        privateKey.value = keyPair.privateKey
        authType.value = SshAuthType.KEY
        viewModelScope.launch {
            _snackBarEvent.emit("Selected SSH Key: ${keyPair.title}")
        }
    }

    fun generateNewKey(type: String, title: String) {
        viewModelScope.launch {
            try {
                val result = SshKeyHelper.generateKeyPair(type, comment = "cloudssh@android")
                val keyPair = SshKeyPair(
                    title = title.ifBlank { result.title },
                    publicKey = result.publicKeyOpenSsh,
                    privateKey = result.privateKeyPem,
                    keyType = result.keyType,
                    fingerprint = result.fingerprint
                )
                repository.saveKeyPair(keyPair)
                applyKeyPair(keyPair)
                _snackBarEvent.emit("Generated new ${result.keyType} keypair!")
            } catch (e: Exception) {
                _snackBarEvent.emit("Key generation error: ${e.message}")
            }
        }
    }

    fun connect() {
        val currentHost = host.value.trim()
        if (currentHost.isBlank()) {
            viewModelScope.launch {
                _snackBarEvent.emit("Please enter or paste a valid SSH Host")
            }
            return
        }

        val augmentation = commandAugmentation.value
        val effectiveCmd = if (augmentation.effectiveCommand.isNotBlank()) augmentation.effectiveCommand else rawCommand.value
        val currentProxyPort = proxyPort.value.toIntOrNull() ?: 7860

        val profile = ConnectionProfile(
            name = profileName.value.ifBlank { "Cloud-$currentHost" },
            rawCommand = effectiveCmd,
            host = currentHost,
            port = sshPort.value.toIntOrNull() ?: 22,
            username = username.value.ifBlank { "root" },
            authType = authType.value.name,
            privateKey = privateKey.value,
            passphrase = passphrase.value,
            password = password.value,
            remotePort = currentProxyPort,
            localPort = currentProxyPort,
            autoOpenBrowser = autoOpenBrowser.value
        )

        val app = getApplication<Application>()
        val startServiceIntent = Intent(app, SshTunnelService::class.java)
        app.startService(startServiceIntent)

        tunnelService?.connectTunnel(profile) { result ->
            viewModelScope.launch {
                if (result.isSuccess) {
                    _snackBarEvent.emit("Connected! Proxy active on port ${result.getOrNull()}")
                } else {
                    _snackBarEvent.emit("Connection failed: ${result.exceptionOrNull()?.message}")
                }
            }
        }
    }

    fun disconnect() {
        tunnelService?.disconnectTunnel()
        viewModelScope.launch {
            _snackBarEvent.emit("Connection ended.")
        }
    }

    fun openBrowserExternal(context: Context, url: String) {
        SshTunnelService.openBrowser(context, url)
    }

    fun saveProfile() {
        viewModelScope.launch {
            val pName = profileName.value.ifBlank { "Cloud-${host.value.ifBlank { "instance" }}" }
            val profile = ConnectionProfile(
                name = pName,
                rawCommand = rawCommand.value,
                host = host.value.ifBlank { "localhost" },
                port = sshPort.value.toIntOrNull() ?: 22,
                username = username.value.ifBlank { "root" },
                authType = authType.value.name,
                privateKey = privateKey.value,
                passphrase = passphrase.value,
                password = password.value,
                remotePort = remotePort.value.toIntOrNull() ?: 7860,
                localPort = localPort.value.toIntOrNull() ?: 7860,
                autoOpenBrowser = autoOpenBrowser.value
            )
            repository.saveProfile(profile)
            _snackBarEvent.emit("Profile '$pName' saved!")
        }
    }

    fun loadProfile(profile: ConnectionProfile) {
        profileName.value = profile.name
        rawCommand.value = profile.rawCommand
        host.value = profile.host
        sshPort.value = profile.port.toString()
        username.value = profile.username
        authType.value = try {
            SshAuthType.valueOf(profile.authType)
        } catch (e: Exception) {
            SshAuthType.KEY
        }
        privateKey.value = profile.privateKey
        passphrase.value = profile.passphrase
        password.value = profile.password
        remotePort.value = profile.remotePort.toString()
        localPort.value = profile.localPort.toString()
        autoOpenBrowser.value = profile.autoOpenBrowser
        _activeTab.value = AppTab.TUNNEL

        viewModelScope.launch {
            _snackBarEvent.emit("Loaded profile: ${profile.name}")
        }
    }

    fun deleteProfile(profile: ConnectionProfile) {
        viewModelScope.launch {
            repository.deleteProfile(profile)
            _snackBarEvent.emit("Deleted profile: ${profile.name}")
        }
    }

    fun deleteKeyPair(keyPair: SshKeyPair) {
        viewModelScope.launch {
            repository.deleteKeyPair(keyPair)
            _snackBarEvent.emit("Deleted key: ${keyPair.title}")
        }
    }

    fun clearLogs() {
        tunnelService?.tunnelManager?.clearLogs()
    }

    override fun onCleared() {
        super.onCleared()
        try {
            if (_isServiceBound.value) {
                getApplication<Application>().unbindService(serviceConnection)
            }
        } catch (e: Exception) {
            // ignore
        }
    }
}
