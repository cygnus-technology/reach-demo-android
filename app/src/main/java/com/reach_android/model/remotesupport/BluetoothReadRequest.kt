package com.reach_android.model.remotesupport

data class BluetoothReadRequest(
    val uuid: String
)

data class BluetoothReadResponse(
    val value: String,
    val data: ByteArray
) {
    val version: Int = 3
}