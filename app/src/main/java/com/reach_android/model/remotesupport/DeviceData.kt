package com.reach_android.model.remotesupport

/**
 * Ble device info to be sent over to peer during remote support session
 */
data class DeviceData(
    val localName: String,
    val advertisementData: HashMap<String, String>,
    val services: List<ServiceInfo>
)

data class ServiceInfo(
    val uuid: String,
    val characteristics: List<CharacteristicInfo>
)

data class CharacteristicInfo(
    val uuid: String,
    val read: Boolean,
    val write: Boolean,
    val notify: Boolean,
    val name: String?,
    val value: String?,
    val encoding: String? = "hex"
)