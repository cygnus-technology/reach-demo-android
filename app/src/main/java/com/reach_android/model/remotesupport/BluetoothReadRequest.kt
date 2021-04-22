package com.reach_android.model.remotesupport

data class BluetoothReadRequest(
    val uuid: String
)

data class BluetoothReadResponse(
    val value: String,
    val encoding: String = "utf-8"
)