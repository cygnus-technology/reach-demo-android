package com.reach_android.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import com.reach_android.App
import com.reach_android.bluetooth.BleManager.canNotify
import com.reach_android.bluetooth.BleManager.canRead
import com.reach_android.bluetooth.BleManager.canWrite
import com.reach_android.bluetooth.BleManager.getCharacteristic
import com.reach_android.bluetooth.BleManager.isConnected
import com.reach_android.model.ConnectionStatus
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import java.util.*

/**
 * Abstract class representing an operation to be performed synchronously with a BLE device
 */
abstract class BleOperation<T>(
    /**
     * Describes the operation
     */
    val name: String,
    /**
     * Device with which to perform operations, ie. connect, read/write characteristic
     */
    val device: BluetoothDevice,

    val onFinishCallback: (Result<T>) -> Unit,
    val onCancelCallback: () -> Unit
) {
    val uuid: UUID = UUID.randomUUID()

    private val hasFinished = atomic(false)
    val isFinished get() = hasFinished.value
    private val hasStarted = atomic(false)
    val isStarted get() = hasStarted.value

    protected val scope = SupervisorJob()

    /**
     * The operation must call this when its work is done
     */
    fun onFinish(result: Result<T>) {
        if (hasFinished.compareAndSet(false, true)) {
            onFinishCallback(result)
        } else {
            logBleWarning("Operation $name already finished")
        }
    }

    fun onCancel() {
        if (hasFinished.compareAndSet(false, true)) {
            scope.cancel()
            onCancelCallback()
        } else {
            logBleWarning("Operation $name already finished")
        }
    }

    /**
     * Begins this operation
     */
    fun start() {
        if (hasStarted.compareAndSet(false, true)) {
            runBlocking(scope) {
                onStarted()
            }
        } else {
            logBleWarning("Operation $name already started")
        }
    }

    abstract fun onStarted()

    /**
     * The result of the operation. Failed operations have an accompanied message
     */
    sealed class Result<in T> {
        class Success<T>(val result: T? = null) : Result<T>()
        class Failure<in T>(val error: String) : Result<T>()
    }
}


/****************************/
/** Generic BLE Operations **/
/****************************/


class ConnectOperation(
    device: BluetoothDevice,
    private val autoReconnect: Boolean,
    private val callback: BleCallback,
    onFinish: (Result<Unit>) -> Unit,
    onCancel: () -> Unit
) : BleOperation<Unit>("Connect Operation", device, onFinish, onCancel) {
    @SuppressLint("MissingPermission") // Permissions are checked in BleManager.startScanning()
    override fun onStarted() {
        if (device.isConnected()) {
            logBle("Already connected")
            onFinish(Result.Success())
            return
        }

        BleManager.getDevice(device.address)
            ?.connectionStatusChanged(ConnectionStatus.Connecting.raw)
        device.connectGatt(App.app.applicationContext, autoReconnect, callback)
    }
}

class DisconnectOperation(
    device: BluetoothDevice,
    val cancelOperations: Boolean,
    onFinish: (Result<Unit>) -> Unit,
    onCancel: () -> Unit
) : BleOperation<Unit>("Disconnect Operation", device, onFinish, onCancel) {
    @SuppressLint("MissingPermission") // Permissions are checked in BleManager.startScanning()
    override fun onStarted() {
        val gatt = BleManager.gattMap[device.address] ?: run {
            onFinish(Result.Success())
            return
        }

        gatt.disconnect()
    }
}

/**
 * Sets the notification status on a given characteristic
 */
class SetNotify(
    device: BluetoothDevice,
    private val characteristicUuid: UUID,
    private val enable: Boolean,
    onFinish: (Result<Unit>) -> Unit,
    onCancel: () -> Unit
) : BleOperation<Unit>("Set Notify (Enable: $enable)", device, onFinish, onCancel) {

    private val value: ByteArray =
        if (enable) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE

    @SuppressLint("MissingPermission") // Permissions are checked in BleManager.startScanning()
    override fun onStarted() {
        val gatt = BleManager.gattMap[device.address] ?: run {
            onFinish(Result.Failure("Device is not connected"))
            return
        }
        val characteristic = gatt.getCharacteristic(characteristicUuid) ?: run {
            onFinish(Result.Failure("Could not find characteristic with specified UUID"))
            return
        }

        if (!characteristic.canNotify()) {
            onFinish(Result.Failure("Characteristic does not have the ability to notify"))
            return
        }

        gatt.setCharacteristicNotification(characteristic, enable)
        val descriptor =
            characteristic.getDescriptor(CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID)
                ?: run {
                    onFinish(Result.Failure("Cannot set up notifications on specified characteristic"))
                    return
                }

        descriptor.value = value
        gatt.writeDescriptor(descriptor)
    }

    companion object {
        /**
         * Notify descriptor UUID
         */
        private val CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}

class ReadCharacteristicResultsRaw(val characteristic: BluetoothGattCharacteristic, val value: ByteArray)
class ReadDescriptorResults(val descriptor: BluetoothGattDescriptor, val value: ByteArray)

/**
 * Attempts to read the value of a given characteristic
 */
class ReadCharacteristic(
    private val characteristicUuid: UUID,
    device: BluetoothDevice,
    onFinish: (Result<ReadCharacteristicResultsRaw>) -> Unit,
    onCancel: () -> Unit
) : BleOperation<ReadCharacteristicResultsRaw>("Read Characteristic ($characteristicUuid)", device, onFinish, onCancel) {
    @SuppressLint("MissingPermission") // Permissions are checked in BleManager.startScanning()
    override fun onStarted() {
        val gatt = BleManager.gattMap[device.address] ?: run {
            onFinish(Result.Failure("Device is not connected"))
            return
        }
        val characteristic = gatt.getCharacteristic(characteristicUuid) ?: run {
            onFinish(Result.Failure("Could not find characteristic with specified UUID"))
            return
        }

        if (!characteristic.canRead()) {
            onFinish(Result.Failure("Characteristic does not have the ability to be read"))
            return
        }



        if (!gatt.readCharacteristic(characteristic)) {
            onFinish(Result.Failure("Failed to read characteristic"))
            return
        }
    }
}

/**
 * Attempts to write a given value to a characteristic
 */
class WriteCharacteristic(
    private val characteristicUuid: UUID,
    private val value: ByteArray,
    device: BluetoothDevice,
    onFinish: (Result<Unit>) -> Unit,
    onCancel: () -> Unit
) : BleOperation<Unit>("Write Characteristic ($characteristicUuid)", device, onFinish, onCancel) {
    @SuppressLint("MissingPermission") // Permissions are checked in BleManager.startScanning()
    override fun onStarted() {
        val gatt = BleManager.gattMap[device.address] ?: run {
            onFinish(Result.Failure("Device is not connected"))
            return
        }
        val characteristic = gatt.getCharacteristic(characteristicUuid) ?: run {
            onFinish(Result.Failure("Could not find characteristic with specified UUID"))
            return
        }

        if (!characteristic.canWrite()) {
            onFinish(Result.Failure("Characteristic does not have the ability to be written"))
            return
        }

        characteristic.value = value
        if (!gatt.writeCharacteristic(characteristic)) {
            onFinish(Result.Failure("Failed to write characteristic"))
            return
        }
    }
}

class ReadDescriptor(
    private val characteristicUuid: UUID,
    private val descriptorUuid: UUID,
    device: BluetoothDevice,
    onFinish: (Result<ReadDescriptorResults>) -> Unit,
    onCancel: () -> Unit
) : BleOperation<ReadDescriptorResults>(
    "Read Descriptor: $descriptorUuid of $characteristicUuid",
    device,
    onFinish,
    onCancel
) {
    @SuppressLint("MissingPermission") // Permissions are checked in BleManager.startScanning()
    override fun onStarted() {
        val gatt = BleManager.gattMap[device.address] ?: run {
            onFinish(Result.Failure("Device is not connected"))
            return
        }
        val characteristic = gatt.getCharacteristic(characteristicUuid) ?: run {
            onFinish(Result.Failure("Could not find characteristic with specified UUID"))
            return
        }
        val descriptor = characteristic.getDescriptor(descriptorUuid) ?: run {
            onFinish(Result.Failure("Could not find descriptor with specified UUID"))
            return
        }

        gatt.readDescriptor(descriptor)
    }
}