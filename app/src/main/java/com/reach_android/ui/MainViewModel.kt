package com.reach_android.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reach_android.bluetooth.BleManager
import com.reach_android.bluetooth.BleOperation
import com.reach_android.model.ConnectionStatus
import com.reach_android.repository.DeviceRepository
import com.reach_android.util.*
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

enum class DeviceConnectionState {
    connected, not_connected, unknown
}

class MainViewModel : ViewModel() {
    private val refreshRequestedFlow = MutableSharedFlow<Unit>()

    private val deviceConnectionStatusFlow = MutableStateFlow(DeviceConnectionState.not_connected)
    val deviceConnectionStatus = deviceConnectionStatusFlow.asStateFlow()

    val selectedDevice = DeviceRepository.selectedDevice.asStateFlow()
    val refreshRequested = refreshRequestedFlow.asSharedFlow()

    private val connectionStatusSupervisor = SupervisorJob()

    init {
        DeviceRepository.selectedDevice.subscribe(viewModelScope) {
            connectionStatusSupervisor.cancelChildren()
            it?.let { device ->
                device.connectionStatusObservable.subscribe(viewModelScope + connectionStatusSupervisor) { status ->
                    if (status == ConnectionStatus.Disconnected) {
                        connectDevice(true)
                    }
                }
            }
        }
    }


    fun requestRefresh() {
        refreshRequestedFlow.tryEmit(Unit)
    }

    /**
     * Attempts to connect to the selected device. Broadcasts the device's connected gatt
     * if successful, otherwise null
     * @param attemptRetry Set to true if you want to attempt connection after failure
     * automatically
     */
    suspend fun connectDevice(attemptRetry: Boolean = false) =
        suspendCoroutine<BleOperation.Result<Unit>> {
            var attempts = if (attemptRetry) 3 else 1

            val device = selectedDevice.value?.device
            if (device == null) {
                it.resume(BleOperation.Result.Failure("Device is not selected"))
            } else {
                suspend fun connect() {
                    attempts--
                    when (val result = BleManager.connect(device)) {
                        is BleOperation.Result.Success -> {
                            deviceConnectionStatusFlow.value = DeviceConnectionState.connected
                            it.resume(result)
                            return
                        }
                        is BleOperation.Result.Failure -> {
                            if (attempts > 0) {
                                Log.d("Reach", "Re-attempting bluetooth connection")
                                connect()
                            } else {
                                it.resume(result)
                                return
                            }
                        }
                    }
                }
                viewModelScope.launch { connect() }
            }
        }

    fun disconnectDevice() {
        selectedDevice.value?.let {
            viewModelScope.launch {
                when (BleManager.disconnect(it.device, true)) {
                    is BleOperation.Result.Success -> deviceConnectionStatusFlow.value = DeviceConnectionState.not_connected
                    else -> DeviceConnectionState.unknown
                }
            }
        }
    }

    fun selectDevice(uuid: String): Boolean {
        BleManager.getDevice(uuid)?.let {
            DeviceRepository.selectedDevice.value = it
            return true
        }
        return false
    }

    fun clearDevice() {
        DeviceRepository.selectedDevice.value = null
    }
}