package com.example.ssh

sealed class TunnelState {
    data object Disconnected : TunnelState()

    data class Connecting(val statusMessage: String = "Connecting...") : TunnelState()

    data class Connected(
        val host: String,
        val sshPort: Int,
        val localPort: Int,
        val remotePort: Int,
        val connectedSince: Long = System.currentTimeMillis(),
        val pingMs: Long = 0L,
        val localUrl: String = "http://127.0.0.1:$localPort"
    ) : TunnelState()

    data class Error(val message: String) : TunnelState()
}

data class TunnelLog(
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel = LogLevel.INFO,
    val message: String
)

enum class LogLevel {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
}
