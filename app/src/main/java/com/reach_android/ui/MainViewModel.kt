package com.reach_android.ui

import android.util.Log
import android.widget.SearchView
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reach_android.bluetooth.BleManager
import com.reach_android.bluetooth.BleOperation
import com.reach_android.model.BleDevice
import com.reach_android.repository.DeviceRepository
import kotlinx.coroutines.launch

class MainViewModel : ViewModel(), SearchView.OnQueryTextListener {

    private val mutableSearchQuery = MutableLiveData<String>()

    val searchQuery: LiveData<String> get() = mutableSearchQuery
    val selectedDevice: BleDevice? get() = DeviceRepository.selectedDevice

    override fun onQueryTextChange(query: String): Boolean {
        mutableSearchQuery.value = query
        return true
    }

    override fun onQueryTextSubmit(query: String) = true

    /**
     * Attempts to connect to the selected device. Broadcasts the device's connected gatt
     * if successful, otherwise null
     * @param attemptRetry Set to true if you want to attempt connection after failure
     * automatically
     */
    fun connectDevice(attemptRetry: Boolean = false): LiveData<BleOperation.Result> {
        val data = MutableLiveData<BleOperation.Result>()
        var attempts = if (attemptRetry) 3 else 1

        fun connect() {
            viewModelScope.launch {
                val device = selectedDevice?.device ?: return@launch

                attempts--
                when (val result = BleManager.connect(device)) {
                    is BleOperation.Result.Success -> {
                        data.postValue(result)
                    }
                    is BleOperation.Result.Failure -> {
                        if (attempts > 0) {
                            Log.d("Reach", "Re-attempting bluetooth connection")
                            viewModelScope.launch {
                                connect()
                            }
                        } else {
                            data.postValue(result)
                        }
                    }
                }
            }
        }

        connect()
        return data
    }

    fun disconnectDevice() {
        val device = selectedDevice
        if (device != null) {
            viewModelScope.launch { BleManager.disconnect(device.device, true) }
        }
    }
}