package com.reach_android.model

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanResult
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.reach_android.bluetooth.logBle
import java.time.Instant

class BleDevice (
    private var result: ScanResult
) {
    private var lastSeen: Long
    val name: String? get() = result.device.name
    val rssi: Int get() = result.rssi
    val uuid: String get() = result.device.address
    val device: BluetoothDevice get() = result.device
    val manufacturerData = result.scanRecord?.manufacturerSpecificData
    val scanBytes = result.scanRecord?.bytes
    val advertisedName = result.scanRecord?.deviceName

    /**
     * If this device hasn't been seen in some time, it can be ignored as it is probably
     * out of range
     */
    val isValid: Boolean get() = Instant.now().toEpochMilli() - lastSeen <= 30 * 1000

    private var rssiListener = MutableLiveData<Int>()
    val rssiObservable: LiveData<Int> = rssiListener

    private var connectionStatusListener = MutableLiveData<ConnectionStatus>()
    val connectionStatusObservable: LiveData<ConnectionStatus> = connectionStatusListener

    private var descriptorListener = MutableLiveData<Unit>()
    val descriptorObservable: LiveData<Unit> = descriptorListener

    init {
        lastSeen = Instant.now().toEpochMilli()
    }

    /**
     * Called when a scan is received for a known device
     */
    fun didScan(result: ScanResult) {
        this.result = result
        lastSeen = Instant.now().toEpochMilli()
        rssiListener.postValue(rssi)
    }

    fun connectionStatusChanged(raw: Int) {
        val state = ConnectionStatus.from(raw)
        logBle("Connection state change: ${state?.description?: raw}")
        connectionStatusListener.postValue(state?: ConnectionStatus.Disconnected)
    }

    fun descriptorRead() {
        descriptorListener.postValue(Unit)
    }
}