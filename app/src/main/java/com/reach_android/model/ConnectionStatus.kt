package com.reach_android.model

import com.reach_android.R

/**
 * Enum representing anything that can be in a connected, disconnected or connecting state
 */
enum class ConnectionStatus(val raw: Int) {
    Disconnected(0),
    Connecting(1),
    Connected(2);

    val background: Int get() =
        when (this) {
            Connected -> R.drawable.connected_icon
            Disconnected -> R.drawable.disconnected_icon
            Connecting -> R.drawable.reconnecting_icon
        }

    /**
     * Used to send to web SDK
     */
    val description: String get() =
        when (this) {
            Connected -> "connected"
            Disconnected -> "disconnected"
            Connecting -> "connecting"
        }

    companion object {
        fun from(raw: Int): ConnectionStatus? = values().firstOrNull { it.raw == raw }
    }
}