package com.reach_android.model.remotesupport

import com.cygnusreach.buffers.IMessageBufferReader
import com.cygnusreach.messages.IMessageError
import com.cygnusreach.messages.IUserMessageCategory
import com.cygnusreach.messages.WellKnownTagEncoder

/**
 * Known message categories
 */
enum class MessageCategory(override val value: Int) : IUserMessageCategory {
    DeviceData(110),
    BluetoothReadRequest(111),
    BluetoothWriteRequest(112),
    DiagnosticHeartbeat(113),
    Image(114),
    Video(115),
    BluetoothNotifyRequest(116),
    RequestDeviceList(117),
    ConnectToDevice(118),
    DisconnectFromDevice(119),
    StartSharing(120),
    StopSharing(121)
}

enum class MediaSharingType(val value: Int) {
    VIDEO(0),
    SCREEN(1),
    UNKNOWN(Int.MIN_VALUE);

    companion object {
        fun decode(bufferReader: IMessageBufferReader) =
            when (WellKnownTagEncoder.Int.decode(bufferReader)) {
                VIDEO.value -> VIDEO
                SCREEN.value -> SCREEN
                else -> UNKNOWN
            }
    }
}

enum class MessageErrors(override val value: Long, override val message: String) : IMessageError {
    InvalidState(1, "Invalid application state"),
    DeviceConnectionError(2, "Device connection error"),
    MediaShareError(3, "Error initiating media sharing"),
    JsonParseError(4, "Unable to parse json message"),
    UserTimeout(5, "User has not responded to request")
}