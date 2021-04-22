package com.reach_android.bluetooth

import android.bluetooth.*
import android.os.Handler
import android.os.Looper

class BleCallback(
    var descriptorRead: (BluetoothDevice) -> Unit,
) : BluetoothGattCallback() {

    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        val uuid = gatt.device.address
        if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
            BleManager.gattMap[uuid] = gatt
        } else if (newState != BluetoothProfile.STATE_CONNECTED) {
            BleManager.gattMap.remove(uuid)
        }

        val operation = BleManager.pendingOperation
        when {
            operation is ConnectOperation &&
            status == BluetoothGatt.GATT_SUCCESS &&
            newState == BluetoothProfile.STATE_CONNECTED -> {
                logBle("Connected to device: ${gatt.device?.name?: "Unknown"}")

                // It is widely accepted that without a delay, calling gatt.discoverServices()
                // could silently fail without the onServicesDiscovered callback being called.
                // The required delay needs to be longer for bonded devices
                // https://github.com/NordicSemiconductor/Android-BLE-Library/blob/master/ble/src/main/java/no/nordicsemi/android/ble/BleManager.java#L481
                val bonded = gatt.device.bondState == BluetoothDevice.BOND_BONDED
                val delay: Long = if (bonded) 1600 else 600

                Handler(Looper.getMainLooper()).postDelayed({
                    if (!gatt.discoverServices()) {
                        logBle("Failed to discover services")
                        operation.onFinish(BleOperation.Result.Failure("Could not discover services"))
                    }
                }, delay)
            }
            operation is DisconnectOperation &&
            status == BluetoothGatt.GATT_SUCCESS &&
            newState == BluetoothProfile.STATE_DISCONNECTED -> {
                gatt.close()
                logBle("Disconnect operation state change")
                BleManager.getDevice(uuid)?.connectionStatusChanged(newState)
                operation.onFinish(BleOperation.Result.Success)
            }
            else -> {
                gatt.close()
                logBle("Unexpected connection state change")
                BleManager.getDevice(uuid)?.connectionStatusChanged(newState)
                operation?.onFinish?.invoke(BleOperation.Result.Failure("Disconnected during operation"))
            }
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        logBle("Discovered services (${if (status == BluetoothGatt.GATT_SUCCESS) "Success" else "Failure"})")
        logBle("${gatt.services.flatMap { it.characteristics }.size} characteristics")
        val operation = BleManager.pendingOperation as? ConnectOperation ?: return

        if (status == BluetoothGatt.GATT_SUCCESS) {
            BleManager.getDevice(gatt.device.address)?.connectionStatusChanged(BluetoothProfile.STATE_CONNECTED)
            operation.onFinish(BleOperation.Result.Success)
        } else {
            gatt.disconnect()
            operation.onFinish(BleOperation.Result.Failure("Failed to discover services"))
            logBle("Service discovery failed")
        }
    }

    override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor?, status: Int) {
        val operation = BleManager.pendingOperation as? SetNotify ?: return
        if (status == BluetoothGatt.GATT_WRITE_NOT_PERMITTED) {
            operation.onFinish(BleOperation.Result.Failure("Write not permitted for descriptor ${descriptor?.uuid}"))
            return
        } else if (status != BluetoothGatt.GATT_SUCCESS) {
            operation.onFinish(BleOperation.Result.Failure("Failed to write value to descriptor ${descriptor?.uuid}"))
            return
        }

        operation.onFinish(BleOperation.Result.Success)
    }

    override fun onDescriptorRead(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor?, status: Int) {
        val operation = BleManager.pendingOperation as? ReadDescriptor ?: return
        if (status != BluetoothGatt.GATT_SUCCESS) {
            operation.onFinish(BleOperation.Result.Failure("Could not read descriptor"))
            return
        }

        descriptorRead(gatt.device)
        operation.onFinish(BleOperation.Result.Success)
    }

    override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
        val operation = BleManager.pendingOperation as? WriteCharacteristic ?: return
        logBle("Wrote characteristic")
        if (status == BluetoothGatt.GATT_WRITE_NOT_PERMITTED) {
            operation.onFinish(BleOperation.Result.Failure("Write not permitted for characteristic ${characteristic?.uuid}"))
            return
        } else if (status != BluetoothGatt.GATT_SUCCESS) {
            operation.onFinish(BleOperation.Result.Failure("Failed to write value for characteristic ${characteristic?.uuid}"))
            return
        }

        operation.onFinish(BleOperation.Result.Success)
    }

    override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
        val operation = BleManager.pendingOperation as? ReadCharacteristic ?: return
        logBle("Read characteristic")
        if (status == BluetoothGatt.GATT_READ_NOT_PERMITTED) {
            operation.onFinish(BleOperation.Result.Failure("Read not permitted for characteristic ${characteristic?.uuid}"))
            return
        } else if (status != BluetoothGatt.GATT_SUCCESS) {
            operation.onFinish(BleOperation.Result.Failure("Failed to read value for characteristic ${characteristic?.uuid}"))
            return
        }

        operation.onFinish(BleOperation.Result.Success)
    }
}