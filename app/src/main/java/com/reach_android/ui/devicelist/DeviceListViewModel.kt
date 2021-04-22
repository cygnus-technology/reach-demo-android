package com.reach_android.ui.devicelist

import androidx.lifecycle.*
import com.reach_android.bluetooth.BleManager
import com.reach_android.model.BleDevice
import com.reach_android.repository.DeviceRepository
import java.util.*
import kotlin.concurrent.timerTask

/**
 * View model for [DeviceListFragment]
 */
class DeviceListViewModel : ViewModel() {
    private var refresh: LiveData<Unit>? = null
    private val displayedDevices = MediatorLiveData<List<BleDevice>>()
    private val bleDevices = BleManager.devices
    private var searchQuery: LiveData<String>? = null
    val devices: LiveData<List<BleDevice>> = displayedDevices

    /**
     * Adds a search query observer to the list of BLE devices
     */
    fun listenToSearchQuery(query: LiveData<String>) {
        if (searchQuery != null) return
        searchQuery = query
        displayedDevices.addSource(query) { text ->
            var list = bleDevices.value?: return@addSource
            if (text.isNotEmpty()) {
                list = list.filter { it.name != null && it.name!!.contains(text, true) }
            }
            displayedDevices.value = list.sortedWith(compareBy { -it.rssi })
        }
    }

    fun refreshDevices(): LiveData<Unit> {
        val existing = refresh
        if (existing != null) return existing
        val data = MutableLiveData<Unit>()
        refresh = data

        fun setDevices() {
            var finalList = bleDevices.value?.filter { it.isValid }?: run {
                data.postValue(Unit)
                return
            }

            val query = searchQuery?.value
            if (query != null && query.isNotEmpty()) {
                finalList = finalList.filter { it.name?.contains(query, true)?: false }
            }
            displayedDevices.postValue(finalList.sortedWith(compareBy { -it.rssi }))
            data.postValue(Unit)
            refresh = null
        }

        // Wait an arbitrary amount of time for BLE devices to be scanned
        val timer = Timer()
        timer.schedule(timerTask {
            setDevices()
        }, 3000)
        return data
    }

    fun selectDevice(device: BleDevice) {
        DeviceRepository.selectedDevice = device
    }
}