package com.reach_android.model

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.le.ScanResult
import com.reach_android.bluetooth.BleManager
import com.reach_android.bluetooth.logBle
import com.reach_android.ui.objectmodel.MovingAverage
import com.reach_android.util.mapCompact
import com.reach_android.util.resettableLazy
import com.reach_android.util.resettableManager
import kotlinx.coroutines.flow.*

@SuppressLint("MissingPermission")
class BleDevice(
    private var result: ScanResult
) {
    private var lastSeen: Long

    private val resetManager = resettableManager()

    val scanBytes by resettableLazy(resetManager) { result.scanRecord?.bytes }
    val device: BluetoothDevice by resettableLazy(resetManager) { result.device }
    val advertisedName by resettableLazy(resetManager) { result.scanRecord?.deviceName }
    val name by resettableLazy(resetManager) { result.device.name ?: advertisedName ?: "Unknown" }
    val uuid  by resettableLazy(resetManager) { result.device.address ?: "" }
    val manufacturerData by resettableLazy(resetManager) { result.scanRecord?.manufacturerSpecificData }
    val company by resettableLazy(resetManager) {
        result.scanRecord?.manufacturerSpecificData
            ?.mapCompact { key, _ -> BleManager.knownCompanyIDs[key] }
            ?.joinToString()
            ?: ""
    }

    val displayName get() = (advertisedName ?: name) + if (company.isNotEmpty()) " - $company" else ""

    /**
     * If this device hasn't been seen in some time, it can be ignored as it is probably
     * out of range
     */
    val isValid: Boolean get() = (System.currentTimeMillis() - lastSeen) <= 30 * 1000 && uuid.isNotEmpty()

    private val rssiListener = MovingAverage(3)
    val rssi = rssiListener.asStateFlow()

    private val rssiBucketListener = MutableStateFlow(0)
    val rssiBucket = rssiBucketListener.asStateFlow()

    private fun getRssiBucket(rssi: Int): Int = when(rssi) {
        in 0 downTo -39 -> 3
        in -40 downTo -59 -> 2
        in -60 downTo -84 -> 1
        else -> 0
    }


    private var connectionStatusListener = MutableStateFlow(ConnectionStatus.Disconnected)
    val connectionStatusObservable = connectionStatusListener.asStateFlow()

    private var descriptorListener = MutableSharedFlow<BluetoothGattDescriptor>(1)
    val descriptorObservable = descriptorListener.asSharedFlow()

    init {
        lastSeen = System.currentTimeMillis()
        updateRssi(result)
    }

    /**
     * Called when a scan is received for a known device
     */
    fun didScan(result: ScanResult) {
        this.result = result
        resetManager.reset()
        lastSeen = System.currentTimeMillis()
        updateRssi(result)
    }

    private fun updateRssi(result: ScanResult) {
        rssiListener.tryEmit(result.rssi)
        rssiBucketListener.tryEmit(getRssiBucket(result.rssi))
    }

    fun connectionStatusChanged(raw: Int) {
        val state = ConnectionStatus.from(raw)
        logBle("Connection state change: ${state?.description ?: raw}")
        connectionStatusListener.tryEmit(state ?: ConnectionStatus.Disconnected)
    }

    fun descriptorRead(descriptor: BluetoothGattDescriptor) {
        descriptorListener.tryEmit(descriptor)
    }
}