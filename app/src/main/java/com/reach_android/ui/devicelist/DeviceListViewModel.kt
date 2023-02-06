package com.reach_android.ui.devicelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reach_android.bluetooth.BleManager
import com.reach_android.model.BleDevice
import com.reach_android.repository.DeviceRepository
import com.reach_android.util.subscribe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * View model for [DeviceListFragment]
 */
class DeviceListViewModel : ViewModel() {
    private val searchQuery = MutableStateFlow("")
    private val displayedDevicesFlow = MutableStateFlow<List<BleDevice>>(emptyList())
    private val refreshEvent = MutableSharedFlow<Unit>()
    val displayedDevices = displayedDevicesFlow.asStateFlow()


    init {
        BleManager.deviceAdded.subscribe(viewModelScope) {
            refresh()
        }
        BleManager.deviceRemoved.subscribe(viewModelScope) {
            refresh()
        }
        BleManager.deviceUpdated.subscribe(viewModelScope) {
            refresh()
        }
        searchQuery.subscribe(viewModelScope) {
            refresh()
        }

        viewModelScope.launch(Dispatchers.Default) {
            refreshEvent.collectLatest {
                val text = searchQuery.value.trim()
                if (text.isNotBlank()) {
                    displayedDevicesFlow.emit(BleManager.devices
                        .filter { it.isValid && (it.displayName.contains(text, true)) }
                        .sortedWith(
                            compareByDescending<BleDevice> {
                                it.rssiBucket.value
                            }
                                .thenBy(BleDevice::name)
                                .thenBy(BleDevice::uuid)
                        ))
                } else {
                    val devices = BleManager.devices
                        .filter { it.isValid }
                        .sortedWith(
                            compareByDescending<BleDevice> {
                                it.rssiBucket.value
                            }
                                .thenBy(BleDevice::name)
                                .thenBy(BleDevice::uuid)
                        )
                    displayedDevicesFlow.emit(devices)
                }
            }
        }
    }

    suspend fun updateSearchQuery(query: String) {
        searchQuery.emit(query)
    }

    fun selectDevice(device: BleDevice) {
        DeviceRepository.selectedDevice.value = device
    }

    suspend fun refresh() {
        refreshEvent.emit(Unit)
    }
}