package com.reach_android.bluetooth

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.util.Log
import android.util.SparseArray
import androidx.annotation.RawRes
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.reach_android.App
import java.lang.Exception
import java.util.*

/**
 * Logs bluetooth related messages
 */
fun logBle(message: String) = Log.d("BleManager", message)
fun logBleWarning(message: String) = Log.w("BleManager", message)

/**
 * Checks for a well known service name given its UUID. Returns its UUID string if not
 */
val BluetoothGattService.name: String get() {
    val uuidString = uuid.toString().uppercase(Locale.ROOT)
    return if (uuidString.startsWith("0000")) {
        val sixteenBit = uuidString.substring(4 until 8)
        BleManager.knownServices[sixteenBit] ?: sixteenBit
    } else {
        uuidString
    }
}

/**
 * Checks for a well known characteristic name given its UUID. Returns its UUID string if not
 */
val BluetoothGattCharacteristic.name: String get() {
    val uuidString = uuid.toString().uppercase(Locale.ROOT)
    val descriptor = getDescriptor(App.NAME_DESCRIPTOR_ID)?.value

    return if (uuidString.startsWith("0000")) {
        val sixteenBit = uuidString.substring(4 until 8)
        BleManager.knownCharacteristics[sixteenBit] ?: sixteenBit
    } else descriptor?.toString(Charsets.UTF_8) ?: uuidString
}


/**
 * Attempts to convert a JSON source to the passed in type
 */
inline fun <reified T> readRawJson(@RawRes rawResId: Int): T {
    App.app.resources.openRawResource(rawResId).bufferedReader().use {
        return Gson().fromJson<T>(it, object: TypeToken<T>() {}.type)
    }
}

val String.hexData: ByteArray? inline get() {
    var hexStr = removePrefix("0x")

    // Pad uneven hex string with 0s
    if (hexStr.length % 2 == 1) {
        hexStr = "0$hexStr"
    }

    return try { hexStr
            .chunked(2)
            .map { it.uppercase(Locale.ROOT).toInt(16).toByte() }
            .toByteArray() }
            catch (e: Exception) { null }
}

val ByteArray.hexString: String inline get() = this.joinToString("") { it.toUByte().toString(16) }


