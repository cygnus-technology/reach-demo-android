package com.reach_android.model.remotesupport

import com.google.gson.annotations.SerializedName

data class BluetoothWriteRequest(
    val uuid: String,
    val encoding: Encoding,
    val value: String
) {

    enum class Encoding {
        @SerializedName("utf-8")
        Utf8,
        @SerializedName("hex")
        Hex
    }
}