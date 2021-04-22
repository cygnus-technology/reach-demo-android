package com.reach_android.bluetooth

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.reach_android.R
import com.reach_android.App
import com.reach_android.model.BleDevice
import com.reach_android.model.KnownCompanyID
import com.reach_android.model.KnownUUID
import kotlinx.coroutines.channels.Channel
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.collections.HashMap

object BleManager : ScanCallback() {

    /**
     * A globally known list of service UUIDs mapped to their readable names
     * https://github.com/NordicSemiconductor/bluetooth-numbers-database
     */
    val knownServices = readRawJson<List<KnownUUID>>(R.raw.service_uuids)
            .associateBy({it.uuid}, {it.name})

    /**
     * A globally known list of characteristic UUIDs mapped to their readable names
     * https://github.com/NordicSemiconductor/bluetooth-numbers-database
     */
    val knownCharacteristics = readRawJson<List<KnownUUID>>(R.raw.characteristic_uuids)
            .associateBy({it.uuid}, {it.name})

    /**
     * A globally known list of company IDs mapped to their readable names
     * https://github.com/NordicSemiconductor/bluetooth-numbers-database
     */
    val knownCompanyIDs = readRawJson<List<KnownCompanyID>>(R.raw.company_ids)
            .associateBy({it.code}, {it.name})

    private val bluetoothAdapter : BluetoothAdapter by lazy {
        val manager = App.app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        manager.adapter
    }

    private val bleScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
        .build()

    private val foundDevices = HashMap<String, BleDevice>()
    private val liveDevices = MutableLiveData(foundDevices.values.toList())
    val devices: LiveData<List<BleDevice>> get() { return liveDevices }

    private val operationQueue = ConcurrentLinkedQueue<BleOperation>()
    private val bleCallback = BleCallback(::descriptorRead)
    var pendingOperation: BleOperation? = null
        private set

    val gattMap = ConcurrentHashMap<String, BluetoothGatt>()

    /**
     * Returns a scanned device with the given UUID if one has been scanned
     */
    fun getDevice(uuid: String): BleDevice? = foundDevices[uuid]

    /**
     * Begins scanning for BLE devices
     * @return boolean denoting if permissions are available to scan for BLE devices
     */
    fun startScanning(): Boolean {
        // Location access required to scan
        return if (ContextCompat.checkSelfPermission(App.app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED) {
            false
        } else {
            bleScanner.startScan(null, scanSettings, this)
            logBle("Started scanning")
            true
        }
    }

    fun stopScanning() {
        logBle("Stopped scanning")
        bleScanner.stopScan(this)
    }

    suspend fun connect(device: BluetoothDevice, autoReconnect: Boolean = false): BleOperation.Result {
        val operationChannel = Channel<BleOperation.Result>()
        enqueueOperation(ConnectOperation(device, autoReconnect, bleCallback, {
            operationDidEnd()
            operationChannel.offer(it)
        }, { operationChannel.offer(BleOperation.Result.Failure("Cancelled")) }))
        return operationChannel.receive()
    }

    /**
     * Enqueues a [DisconnectOperation]
     * @param cancelOperations If set to true, all previously queued operations will be cancelled
     */
    suspend fun disconnect(device: BluetoothDevice, cancelOperations: Boolean = false): BleOperation.Result {
        val operationChannel = Channel<BleOperation.Result>()
        enqueueOperation(DisconnectOperation(device, cancelOperations, {
            if (it is BleOperation.Result.Failure) {
                logBle("Failed to disconnect from device: ${device.name}. Error: ${it.error}")
            }
            operationDidEnd()
            operationChannel.offer(it)
        }, { operationChannel.offer(BleOperation.Result.Failure("Cancelled")) }))
        return operationChannel.receive()
    }

    suspend fun setNotify(device: BluetoothDevice, characteristicUuid: UUID, enable: Boolean): BleOperation.Result {
        val operationChannel = Channel<BleOperation.Result>()
        enqueueOperation(SetNotify(device, characteristicUuid, enable, {
            operationDidEnd()
            operationChannel.offer(it)
        }, { operationChannel.offer(BleOperation.Result.Failure("Cancelled")) }))
        return operationChannel.receive()
    }

    suspend fun readCharacteristic(device: BluetoothDevice, characteristicUuid: UUID): BleOperation.Result {
        val operationChannel = Channel<BleOperation.Result>()
        enqueueOperation(ReadCharacteristic(characteristicUuid, device, {
            operationDidEnd()
            operationChannel.offer(it)
        }, { operationChannel.offer(BleOperation.Result.Failure("Cancelled")) }))
        return operationChannel.receive()
    }

    suspend fun writeCharacteristic(device: BluetoothDevice, characteristicUuid: UUID, value: ByteArray): BleOperation.Result {
        val operationChannel = Channel<BleOperation.Result>()
        enqueueOperation(WriteCharacteristic(characteristicUuid, value, device, {
            operationDidEnd()
            operationChannel.offer(it)
        }, { operationChannel.offer(BleOperation.Result.Failure("Cancelled")) }))
        return operationChannel.receive()
    }

    fun readDescriptor(device: BluetoothDevice, characteristicUuid: UUID, descriptorUuid: UUID) {
        enqueueOperation(ReadDescriptor(characteristicUuid, descriptorUuid, device, {
            operationDidEnd()
        }, {}))
    }

    override fun onScanResult(callbackType: Int, result: ScanResult?) {
        if (result?.device != null) {
            val uuid = result.device.address
            if (foundDevices[uuid] == null) {
                foundDevices[uuid] = BleDevice(result)
            } else {
                foundDevices[uuid]?.didScan(result)
            }
            liveDevices.postValue(foundDevices.values.toList())
        }
    }

    override fun onScanFailed(errorCode: Int) {
        logBle("Device scan failed: $errorCode")
    }

    @Synchronized
    private fun enqueueOperation(operation: BleOperation) {
        logBle("Enqueuing operation: ${operation.name} (${operationQueue.size + 1} total)")

        if (operation is DisconnectOperation && operation.cancelOperations) {
            logBle("Cancelling operations")
            operationQueue.forEach { it.onCancel() }
            operationQueue.clear()
            pendingOperation = null
        }

        operationQueue.add(operation)
        if (pendingOperation == null) {
            performOperation()
        }
    }

    @Synchronized
    private fun performOperation() {
        if (pendingOperation != null) return
        val operation = operationQueue.poll() ?: return
        logBle("Performing operation: ${operation.name}")
        pendingOperation = operation
        pendingOperation?.start()
    }

    @Synchronized
    private fun operationDidEnd() {
        if (pendingOperation == null) return
        logBle("Operation ended: ${pendingOperation?.name?: "null"} (${operationQueue.size} left)")
        pendingOperation = null
        if (operationQueue.isNotEmpty()) {
            performOperation()
        }
    }

    private fun descriptorRead(device: BluetoothDevice) {
        foundDevices[device.address]?.descriptorRead()
    }

    // - Bluetooth extensions

    fun BluetoothDevice.isConnected() = gattMap.containsKey(address)

    fun BluetoothGatt.getCharacteristic(uuid: UUID): BluetoothGattCharacteristic? =
        services.flatMap { it.characteristics }.firstOrNull {
            it.uuid == uuid
        }

    fun BluetoothGattCharacteristic.canRead(): Boolean = properties and BluetoothGattCharacteristic.PROPERTY_READ != 0
    fun BluetoothGattCharacteristic.canWrite(): Boolean = properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
    fun BluetoothGattCharacteristic.canNotify(): Boolean = properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
}