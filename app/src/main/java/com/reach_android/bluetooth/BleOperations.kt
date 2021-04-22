package com.reach_android.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattDescriptor
import com.reach_android.App
import com.reach_android.bluetooth.BleManager.canNotify
import com.reach_android.bluetooth.BleManager.canRead
import com.reach_android.bluetooth.BleManager.canWrite
import com.reach_android.bluetooth.BleManager.getCharacteristic
import com.reach_android.bluetooth.BleManager.isConnected
import com.reach_android.model.ConnectionStatus
import java.util.*
import kotlin.concurrent.timerTask

/**
 * Abstract class representing an operation to be performed synchronously with a BLE device
 */
abstract class BleOperation {

    val uuid: UUID = UUID.randomUUID()

    /**
     * Device with which to perform operations, ie. connect, read/write characteristic
     */
    abstract val device: BluetoothDevice

    /**
     * The operation must call this when its work is done
     */
    abstract val onFinish: (Result) -> Unit

    abstract val onCancel: () -> Unit

    /**
     * Describes the operation
     */
    abstract val name: String

    /**
     * Begins this operation
     */
    abstract fun start()

    /**
     * The result of the operation. Failed operations have an accompanied message
     */
    sealed class Result {
        object Success : Result()
        class Failure(val error: String) : Result()
    }
}


/****************************/
/** Generic BLE Operations **/
/****************************/


class ConnectOperation(
    override val device: BluetoothDevice,
    private val autoReconnect: Boolean,
    private val callback: BleCallback,
    override val onFinish: (Result) -> Unit,
    override val onCancel: () -> Unit
) : BleOperation() {

    override val name: String
        get() = "Connect Operation"

    override fun start() {
        if (device.isConnected()) {
            logBle("Already connected")
            onFinish(Result.Success)
            return
        }

        BleManager.getDevice(device.address)?.connectionStatusChanged(ConnectionStatus.Connecting.raw)
        device.connectGatt(App.app.applicationContext, autoReconnect, callback)
        Timer().schedule(timerTask {
            if (BleManager.pendingOperation?.uuid != uuid) return@timerTask
            logBle("Timing out connect operation")
            BleManager.gattMap[device.address]?.disconnect()
        }, 10000)
    }
}

class DisconnectOperation(
    override val device: BluetoothDevice,
    val cancelOperations: Boolean,
    override val onFinish: (Result) -> Unit,
    override val onCancel: () -> Unit
) : BleOperation() {

    override val name: String
        get() = "Disconnect Operation"

    override fun start() {
        val gatt = BleManager.gattMap[device.address] ?: run {
            onFinish(Result.Success)
            return
        }

        gatt.disconnect()
    }
}

/**
 * Sets the notification status on a given characteristic
 */
class SetNotify(
    override val device: BluetoothDevice,
    private val characteristicUuid: UUID,
    private val enable: Boolean,
    override val onFinish: (Result) -> Unit,
    override val onCancel: () -> Unit
) : BleOperation() {

    private val value: ByteArray =
        if (enable) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE

    override val name: String
        get() = "Set Notify (Enable: $enable)"

    override fun start() {
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
        val descriptor = characteristic.getDescriptor(CHARACTERISTIC_UPDATE_NOTIFICATION_DESCRIPTOR_UUID) ?: run {
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

/**
 * Attempts to read the value of a given characteristic
 */
class ReadCharacteristic(
    private val characteristicUuid: UUID,
    override val device: BluetoothDevice,
    override val onFinish: (Result) -> Unit,
    override val onCancel: () -> Unit
) : BleOperation() {

    override val name: String
        get() = "Read Characteristic ($characteristicUuid)"

    override fun start() {
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
    override val device: BluetoothDevice,
    override val onFinish: (Result) -> Unit,
    override val onCancel: () -> Unit
) : BleOperation() {

    override val name: String
        get() = "Write Characteristic ($characteristicUuid)"

    override fun start() {
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
    override val device: BluetoothDevice,
    override val onFinish: (Result) -> Unit,
    override val onCancel: () -> Unit
) : BleOperation() {

    override val name: String
        get() = "Read Descriptor: $descriptorUuid of $characteristicUuid"

    override fun start() {
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