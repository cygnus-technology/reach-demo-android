package com.reach_android.model.remotesupport

import android.net.Uri

/**
 * Represents a single chat message in a remote support session
 */
class ChatMessage(
    /**
     * Denotes whether the message was sent by the user or received by the peer
     */
    val sent: Boolean,

    /**
     * Whether or not this represents a placeholder for an incomplete message
     */
    val loading: Boolean = false,

    /**
     * Used to match loading message placeholders with their eventual complete message.
     * Can be ignored for sent messages
     */
    val id: Int = -1,

    val text: String? = null,
    val image: Uri? = null,
    val video: Uri? = null,
)

/**
 * Known message categories
 */
enum class MessageCategory(val raw: Int) {
    DeviceData(110),
    BluetoothReadRequest(111),
    BluetoothWriteRequest(112),
    DiagnosticHeartbeat(113),
    Image(114),
    Video(115),
    BluetoothNotifyRequest(116)
}