package com.reach_android.ui.selecteddevice

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.util.Log
import androidx.core.util.forEach
import androidx.lifecycle.*
import com.reach_android.App
import com.reach_android.bluetooth.BleManager
import com.reach_android.bluetooth.BleOperation
import com.reach_android.bluetooth.hexData
import com.reach_android.bluetooth.name
import com.reach_android.model.BleDevice
import com.reach_android.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*
import kotlin.Comparator

class SelectedDeviceViewModel : ViewModel() {

    private val gatt: BluetoothGatt? get() {
        val uuid = selectedDevice?.uuid?: return null
        return BleManager.gattMap[uuid]
    }

    /**
     * Stores the hex value to write to a characteristic
     */
    var hexValue = ""

    /**
     * Stored in [MainViewModel], passed in by [SelectedDeviceFragment]
     */
    var selectedDevice: BleDevice? = null

    /**
     * Maps the selected device's advertised data, services and characteristics into a list
     * of displayable rows
     */
    val displayData: List<SelectedDeviceAdapter.Row> get() {
        val list = arrayListOf<SelectedDeviceAdapter.Row>(SelectedDeviceAdapter.Row.AdvertisementHeader())
        // Add advertisement data
        selectedDevice?.advertisedName?.let { name ->
            list.add(
                SelectedDeviceAdapter.Row.AdvertisementData(
                    "Advertised name",
                    name))
        }

        selectedDevice?.manufacturerData?.forEach { key, value ->
            val hex = value.joinToString("") { String.format("%02X", it) }
            val companyName = BleManager.knownCompanyIDs[key]
            val manVal = "${companyName?: "Unknown Company"} (${String.format("0x%04X", key)}):\n0x$hex"
            list.add(
                SelectedDeviceAdapter.Row.AdvertisementData(
                    "Manufacturer specific data",
                    manVal))
        }

        selectedDevice?.scanBytes?.let { bytes ->
            val hex = bytes.joinToString("") { String.format("%02X", it) }
            list.add(
                SelectedDeviceAdapter.Row.AdvertisementData(
                    "Raw advertisement packet",
                    "0x$hex"))
        }

        // Add service/characteristic data
        gatt?.services
                // Services with known names should come first
                ?.sortedWith(Comparator { a, b ->
                    val uuidA = a.uuid.toString().toUpperCase(Locale.ROOT)
                    val uuidB = b.uuid.toString().toUpperCase(Locale.ROOT)
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
                        list.add(SelectedDeviceAdapter.Row.Characteristic(it))
                    }
                }

        return list
    }

    fun readCharacteristics(): LiveData<BleOperation.Result> {
        val data = MutableLiveData<BleOperation.Result>()
        val gatt = gatt?: return data
        val characteristics = gatt.services.flatMap { it.characteristics }
        // Read characteristic names
        characteristics
            .flatMap { it.descriptors }
            .filter { it.uuid == UUID.fromString(App.NAME_DESCRIPTOR_UUID) }
            .forEach {
                BleManager.readDescriptor(gatt.device, it.characteristic.uuid, it.uuid)
            }

        characteristics.forEach {
            viewModelScope.launch(Dispatchers.Default) {
                val result = BleManager.readCharacteristic(gatt.device, it.uuid)
                data.postValue(result)
            }
        }
        return data
    }

    fun writeCharacteristic(characteristic: BluetoothGattCharacteristic): LiveData<BleOperation.Result> {
        val data = MutableLiveData<BleOperation.Result>()
        val gatt = gatt?: return data
        val value = hexValue.hexData?: return data
        viewModelScope.launch {
            val result = BleManager.writeCharacteristic(gatt.device, characteristic.uuid, value)
            data.postValue(result)
        }
        return data
    }
}