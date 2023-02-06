package com.reach_android.ui.selecteddevice

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import androidx.core.util.forEach
import androidx.lifecycle.ViewModel
import com.reach_android.App
import com.reach_android.bluetooth.BleManager
import com.reach_android.bluetooth.BleOperation
import com.reach_android.bluetooth.hexData
import com.reach_android.repository.DeviceRepository
import com.reach_android.ui.MainViewModel
import kotlinx.coroutines.flow.flow
import java.util.*

class SelectedDeviceViewModel : ViewModel() {
    private val selectedDevice get() = DeviceRepository.selectedDevice.value
    private val gatt: BluetoothGatt? get() = selectedDevice?.uuid?.let { BleManager.gattMap[it] }

    /**
     * Stores the hex value to write to a characteristic
     */
    var hexValue = ""



    /**
     * Maps the selected device's advertised data, services and characteristics into a list
     * of displayable rows
     */
    val displayData: List<SelectedDeviceAdapter.Row>
        get() {
            val list =
                arrayListOf<SelectedDeviceAdapter.Row>(SelectedDeviceAdapter.Row.AdvertisementHeader())

            selectedDevice?.let { device ->
                // Add advertisement data
                device.advertisedName?.let { name ->
                    list.add(
                        SelectedDeviceAdapter.Row.AdvertisementData(
                            "Advertised name",
                            name
                        )
                    )
                }

                device.manufacturerData?.forEach { key, value ->
                    val hex = value.joinToString("") { String.format("%02X", it) }
                    val companyName = BleManager.knownCompanyIDs[key]
                    val manVal =
                        "${companyName ?: "Unknown Company"} (${String.format("0x%04X", key)}):\n0x$hex"
                    list.add(
                        SelectedDeviceAdapter.Row.AdvertisementData(
                            "Manufacturer specific data",
                            manVal
                        )
                    )
                }

                device.scanBytes?.let { bytes ->
                    val hex = bytes.joinToString("") { String.format("%02X", it) }
                    list.add(
                        SelectedDeviceAdapter.Row.AdvertisementData(
                            "Raw advertisement packet",
                            "0x$hex"
                        )
                    )
                }

                // Add service/characteristic data
                gatt?.services
                    // Services with known names should come first
                    ?.sortedWith(Comparator { a, b ->
                        val uuidA = a.uuid.toString().uppercase(Locale.ROOT)
                        val uuidB = b.uuid.toString().uppercase(Locale.ROOT)
                        val nameA = BleManager.knownServices[uuidA]
                        val nameB = BleManager.knownServices[uuidB]

                        if (nameA != null && nameB != null) {
                            return@Comparator if (nameA > nameB) 1 else -1
                        } else if (nameB != null) {
                            return@Comparator 1
                        } else if (nameA != null) {
                            return@Comparator -1
                        } else {
                            return@Comparator if (uuidA > uuidB) 1 else -1
                        }
                    })
                    ?.forEach { service ->
                        list.add(SelectedDeviceAdapter.Row.Service(service))
                        service.characteristics.forEach {
                            list.add(SelectedDeviceAdapter.Row.Characteristic(device.uuid, it))
                        }
                    }
            }


            return list
        }

    fun readCharacteristics() = flow {
        val gatt = gatt ?: return@flow

        val characteristics = gatt.services.flatMap { it.characteristics }
        // Read characteristic names
        characteristics
            .flatMap { it.descriptors }
            .filter { it.uuid == App.NAME_DESCRIPTOR_ID }
            .forEach {
                BleManager.readDescriptor(gatt.device, it.characteristic.uuid, it.uuid)
            }

        characteristics.forEach {
            emit(BleManager.readCharacteristic(gatt.device, it.uuid))
        }
    }

    suspend fun writeCharacteristic(characteristic: BluetoothGattCharacteristic): BleOperation.Result<Unit> {
        return gatt?.let { gatt ->
            hexValue.hexData?.let { value ->
                BleManager.writeCharacteristic(gatt.device, characteristic.uuid, value)
            } ?: BleOperation.Result.Failure("hex data is not available")
        } ?: BleOperation.Result.Failure("device is not selected")
    }
}