package com.reach_android.repository

import com.reach_android.model.BleDevice
import kotlinx.coroutines.flow.MutableStateFlow

object DeviceRepository {
    val selectedDevice = MutableStateFlow<BleDevice?>(null)
}