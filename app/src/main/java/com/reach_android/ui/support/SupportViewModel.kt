package com.reach_android.ui.support

import androidx.lifecycle.ViewModel
import com.reach_android.model.BleDevice
import com.reach_android.repository.DeviceRepository
import kotlinx.coroutines.flow.MutableStateFlow

enum class DeviceViewState { NO_DEVICE, DEVICE_SELECTED }
enum class CameraViewStates { PLACE_HOLDER, CAMERA_VIEW }
enum class ScreenViewStates { PLACE_HOLDER, SCREEN_VIEW }

class SupportViewModel : ViewModel() {
    val deviceViewState = MutableStateFlow(DeviceViewState.NO_DEVICE)
    val cameraViewState = MutableStateFlow(CameraViewStates.PLACE_HOLDER)
    val screenViewState = MutableStateFlow(ScreenViewStates.PLACE_HOLDER)
    val selectedDevice get() = DeviceRepository.selectedDevice
}

