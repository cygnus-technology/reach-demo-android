package com.reach_android.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.util.ArrayMap
import com.reach_android.App
import com.reach_android.R
import com.reach_android.model.BleDevice
import com.reach_android.model.KnownCompanyID
import com.reach_android.model.KnownUUID
import com.reach_android.util.arePermissionsGranted
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object BleManager : ScanCallback() {
    val scanSupervisor = SupervisorJob()
    val scanScope = CoroutineScope(scanSupervisor + Dispatchers.Default)
    val isScanning = atomic(false)

    /**
     * A globally known list of service UUIDs mapped to their readable names
     * https://github.com/NordicSemiconductor/bluetooth-numbers-database
     */
    val knownServices = readRawJson<List<KnownUUID>>(R.raw.service_uuids)
        .associateBy({ it.uuid }, { it.name })

    /**
     * A globally known list of characteristic UUIDs mapped to their readable names
     * https://github.com/NordicSemiconductor/bluetooth-numbers-database
     */
    val knownCharacteristics = readRawJson<List<KnownUUID>>(R.raw.characteristic_uuids)
        .associateBy({ it.uuid }, { it.name })

    /**
     * A globally known list of company IDs mapped to their readable names
     * https://github.com/NordicSemiconductor/bluetooth-numbers-database
     */
    val knownCompanyIDs = readRawJson<List<KnownCompanyID>>(R.raw.company_ids)
        .associateBy({ it.code }, { it.name })

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val manager = App.app.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        manager.adapter
    }

    private val bleScanner by lazy {
        bluetoothAdapter.bluetoothLeScanner
    }

    private val characteristicCache = ConcurrentHashMap<CharacteristicID, ReadCharacteristicResults>()
    fun getCachedCharacteristic(deviceAddress: String, characteristicID: UUID) =
        characteristicCache[CharacteristicID(deviceAddress, characteristicID)]

    private val scanSettings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    private val foundDevices = ArrayMap<String, BleDevice>()
    val devices: Collection<BleDevice> get() = foundDevices.values
    private val deviceAddedFlow = MutableSharedFlow<BleDevice>()
    val deviceAdded = deviceAddedFlow.asSharedFlow()
    private val deviceUpdatedFlow = MutableSharedFlow<BleDevice>()
    val deviceUpdated = deviceUpdatedFlow.asSharedFlow()
    private val deviceRemovedFlow = MutableSharedFlow<BleDevice>()
    val deviceRemoved = deviceRemovedFlow.asSharedFlow()

    private val operationQueue = ConcurrentLinkedQueue<BleOperation<*>>()
    private val bleCallback = BleCallback(::descriptorRead)

    private val pendingOperationState = MutableStateFlow<BleOperation<*>?>(null)
    val pendingOperation get() = pendingOperationState.value

    val gattMap = ConcurrentHashMap<String, BluetoothGatt>()

    /**
     * Returns a scanned device with the given UUID if one has been scanned
     */
    fun getDevice(uuid: String): BleDevice? = foundDevices[uuid]

    /**
     * Permissions required for the BleManager to operate
     */
    val requiredPermissions = if (Build.VERSION.SDK_INT >= 31) {
        arrayOf(
            // Location access required to scan
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(
            // Location access required to scan
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN
        )
    }

    /**
     * Begins scanning for BLE devices
     * @return boolean denoting if permissions are available to scan for BLE devices
     */
    @SuppressLint("MissingPermission")
    fun startScanning(): Boolean {
        return if (arePermissionsGranted(requiredPermissions)) {
            if (isScanning.compareAndSet(false, true)) {
                bleScanner.startScan(null, scanSettings, this)
                logBle("Started scanning")
                true
            } else {
                false
            }
        } else {
            false
        }
    }

    @SuppressLint("MissingPermission") // Permissions are checked in startScanning()
    fun stopScanning(): Boolean {
        if (isScanning.compareAndSet(true, false)) {
            logBle("Stopped scanning")
            bleScanner.stopScan(this)
            scanSupervisor.cancelChildren()
            return true
        } else {
            return false
        }
    }

    suspend fun connect(
        device: BluetoothDevice,
        autoReconnect: Boolean = false
    ) = suspendCoroutine<BleOperation.Result<Unit>> { promise ->
        enqueueOperation(ConnectOperation(device, autoReconnect, bleCallback, {
            operationDidEnd()
            promise.resume(it)
        }, {
            promise.resume(BleOperation.Result.Failure("Cancelled"))
        }))
    }

    /**
     * Enqueues a [DisconnectOperation]
     * @param cancelOperations If set to true, all previously queued operations will be cancelled
     */
    @SuppressLint("MissingPermission")
    suspend fun disconnect(
        device: BluetoothDevice,
        cancelOperations: Boolean = false
    ) = suspendCoroutine<BleOperation.Result<Unit>> { promise ->
        enqueueOperation(DisconnectOperation(device, cancelOperations, {
            if (it is BleOperation.Result.Failure) {
                logBle("Failed to disconnect from device: ${device.name}. Error: ${it.error}")
            }
            operationDidEnd()
            promise.resume(it)
        }, {
            promise.resume(BleOperation.Result.Failure("Cancelled"))
        }))
    }

    suspend fun setNotify(
        device: BluetoothDevice,
        characteristicUuid: UUID,
        enable: Boolean
    ) = suspendCoroutine<BleOperation.Result<Unit>> { promise ->
        enqueueOperation(SetNotify(device, characteristicUuid, enable, {
            operationDidEnd()
            promise.resume(it)
        }, {
            promise.resume(BleOperation.Result.Failure("Cancelled"))
        }))
    }

    suspend fun readCachedCharacteristic(
        device: BluetoothDevice,
        characteristicUuid: UUID
    ): BleOperation.Result<ReadCharacteristicResults> {
        return characteristicCache[CharacteristicID(device.address, characteristicUuid)]?.let {
            BleOperation.Result.Success(it)
        }  ?: readCharacteristic(device, characteristicUuid)
    }

    suspend fun readCharacteristic(
        device: BluetoothDevice,
        characteristicUuid: UUID
    ): BleOperation.Result<ReadCharacteristicResults> {
        val pf = readDescriptor(device, characteristicUuid, CharacteristicPresentationFormat.descriptorId)
        val format = when (pf) {
            is BleOperation.Result.Success -> pf.result?.value
            else -> null
        }

        return when (val cv = readCharacteristicInternal(device, characteristicUuid)) {
            is BleOperation.Result.Success -> cv.result?.let { results ->
                format?.let { decodeWithFormatter(it, results.value) } ?: decodeAsString(results.value)
            } ?: BleOperation.Result.Failure("Failed to read characteristic")
            is BleOperation.Result.Failure -> BleOperation.Result.Failure("Failed to read characteristic: ${cv.error}")
        }.also {
            if(it is BleOperation.Result.Success && it.result != null) {
                characteristicCache[CharacteristicID(device.address, characteristicUuid)] = it.result
            }
        }
    }

    private fun decodeWithFormatter(
        format: ByteArray,
        value: ByteArray
    ): BleOperation.Result<ReadCharacteristicResults>? = CharacteristicPresentationFormat
        .parse(format)
        .formatValue(value)
        ?.let { BleOperation.Result.Success(ReadCharacteristicResults(it, value)) }

    private fun decodeAsString(value: ByteArray) = try {
        BleOperation.Result.Success(
            ReadCharacteristicResults(
                StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(value)).toString(), value
            )
        )
    } catch (e: Throwable) {
        BleOperation.Result.Success(ReadCharacteristicResults(value.hexString, value))
    }

    private suspend fun readCharacteristicInternal(
        device: BluetoothDevice,
        characteristicUuid: UUID
    ) = suspendCoroutine<BleOperation.Result<ReadCharacteristicResultsRaw>> { promise ->
        enqueueOperation(ReadCharacteristic(characteristicUuid, device, {
            operationDidEnd()
            promise.resume(it)
        }, {
            promise.resume(BleOperation.Result.Failure("Cancelled"))
        }))
    }

    suspend fun writeCharacteristic(
        device: BluetoothDevice,
        characteristicUuid: UUID,
        value: ByteArray
    ) = suspendCoroutine<BleOperation.Result<Unit>> { promise ->
        enqueueOperation(WriteCharacteristic(characteristicUuid, value, device, { rslt ->
            operationDidEnd()
            promise.resume(rslt)
        }, {
            promise.resume(BleOperation.Result.Failure("Cancelled"))
        }))
    }


    suspend fun readDescriptor(
        device: BluetoothDevice,
        characteristicUuid: UUID,
        descriptorUuid: UUID
    ) = suspendCoroutine<BleOperation.Result<ReadDescriptorResults>> { promise ->
        enqueueOperation(ReadDescriptor(characteristicUuid, descriptorUuid, device, { rslt ->
            operationDidEnd()
            promise.resume(rslt)
        }, {
            promise.resume(BleOperation.Result.Failure("Cancelled"))
        }))
    }

    override fun onScanResult(callbackType: Int, result: ScanResult?) {
        result?.let { rslt ->
            if (rslt.device != null) {
                when (callbackType) {
                    ScanSettings.CALLBACK_TYPE_MATCH_LOST -> {
                        val removed = foundDevices.remove(rslt.device.address)
                        removed?.let {
                            scanScope.launch {
                                deviceRemovedFlow.emit(it)
                            }
                        }
                    }
                    else -> {
                        val uuid = rslt.device.address
                        if (foundDevices[uuid] == null) {
                            val device = BleDevice(result)
                            foundDevices[uuid] = device
                            scanScope.launch {
                                deviceAddedFlow.emit(device)
                            }
                        } else {
                            foundDevices[uuid]?.let {
                                it.didScan(rslt)
                                scanScope.launch {
                                    deviceUpdatedFlow.emit(it)
                                }

                            }
                        }
                    }
                }
            }
        }

    }

    override fun onScanFailed(errorCode: Int) {
        logBle("Device scan failed: $errorCode")
    }

    @Synchronized
    private fun enqueueOperation(operation: BleOperation<*>) {
        logBle("Enqueuing operation: ${operation.name} (${operationQueue.size + 1} total)")

        if (operation is DisconnectOperation && operation.cancelOperations) {
            logBle("Cancelling operations")
            operationQueue.forEach { it.onCancel() }
            operationQueue.clear()
            pendingOperationState.value = null
        }

        operationQueue.add(operation)
        if (pendingOperation == null) {
            performOperation()
        }
    }

    // Only called from synchronized methods
    private fun performOperation() {
        if (pendingOperation != null) return
        val operation = operationQueue.poll() ?: return
        logBle("Performing operation: ${operation.name}")
        pendingOperationState.value = operation
        operation.start()
    }

    @Synchronized
    private fun operationDidEnd() {
        if (pendingOperation == null) return
        logBle("Operation ended: ${pendingOperation?.name ?: "null"} (${operationQueue.size} left)")
        pendingOperationState.value = null
        if (operationQueue.isNotEmpty()) {
            performOperation()
        }
    }

    private fun descriptorRead(device: BluetoothDevice, descriptor: BluetoothGattDescriptor) {
        foundDevices[device.address]?.descriptorRead(descriptor)
    }

    // - Bluetooth extensions

    fun BluetoothDevice.isConnected() = gattMap.containsKey(address)

    fun BluetoothGatt.getCharacteristic(uuid: UUID): BluetoothGattCharacteristic? =
        services.flatMap { it.characteristics }.firstOrNull {
            it.uuid == uuid
        }

    fun BluetoothGattCharacteristic.canRead(): Boolean =
        properties and BluetoothGattCharacteristic.PROPERTY_READ != 0

    fun BluetoothGattCharacteristic.canWrite(): Boolean =
        properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0

    fun BluetoothGattCharacteristic.canNotify(): Boolean =
        properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
}

data class CharacteristicID(val deviceAddress: String, val characteristicID: UUID)
data class ReadCharacteristicResults(val formatted: String, val value: ByteArray)


